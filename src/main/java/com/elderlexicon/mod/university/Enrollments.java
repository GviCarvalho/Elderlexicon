package com.elderlexicon.mod.university;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Who studies at the universities (docs/mago-design.md, 4.1 and 4.2), kept with the world: each student's rank, the
 * fee the last examination set, until when the period is paid, and the disciplines passed. The universities of a world
 * are one house: a student of one is a student of all.
 */
public final class Enrollments extends SavedData {

    private static final String NAME = "elderlexicon_university";
    /** A period: seven days of the game. */
    public static final long PERIOD = 7L * 24000L;
    /** How long one who failed waits before being examined again. */
    public static final long RETRY = 24000L;
    /** How many questions the council asks, and how many must be right to be admitted. */
    public static final int COUNCIL_QUESTIONS = 6;
    public static final int ADMITTED = 3;
    /** How many questions a master asks, and how many must be right to pass. */
    public static final int MASTER_QUESTIONS = 5;
    public static final int PASSED = 4;

    /** A student, as the house keeps them. */
    public static final class Student {
        @Nullable
        Rank rank;
        /** Emeralds a period, as the last examination set it: below zero, the house pays them. */
        int fee;
        /** When the fee was set (-1: never); and until when the period is paid. */
        long feeSetAt = -1L;
        long paidUntil;
        /** When the council last failed them. */
        long failedAt = Long.MIN_VALUE / 2;
        /** The disciplines passed, with the score of each; when each master last examined them. */
        final Map<Discipline, Integer> passed = new EnumMap<>(Discipline.class);
        final Map<Discipline, Long> triedAt = new EnumMap<>(Discipline.class);

        public Optional<Rank> rank() {
            return Optional.ofNullable(rank);
        }

        public int fee() {
            return fee;
        }

        public long paidUntil() {
            return paidUntil;
        }

        public Map<Discipline, Integer> passed() {
            return passed;
        }

        /** Admitted, and the period paid. */
        public boolean inGoodStanding(long now) {
            return rank != null && now < paidUntil;
        }

        /** The period is over (or was never paid), and the fee for the next was set by an examination since. */
        public boolean owes(long now) {
            return rank != null && now >= paidUntil && feeSetAt >= paidUntil && feeSetAt >= 0;
        }

        /** The period is over, and the council must examine them again to set the next fee. */
        public boolean dueForExamination(long now) {
            return rank != null && now >= paidUntil && !owes(now);
        }

        /** Whether the council failed them too lately to be examined again. */
        public boolean waiting(long now) {
            return now < failedAt + RETRY;
        }

        /** Whether the master of {@code discipline} examined them too lately to do it again. */
        public boolean waiting(Discipline discipline, long now) {
            return now < triedAt.getOrDefault(discipline, Long.MIN_VALUE / 2) + RETRY;
        }

        /**
         * Whether they may be a Lector (docs/mago-design.md, 4.2): a Scriba who passed three disciplines or more, one of
         * them without a mistake (that master accepts them).
         */
        public boolean mayRead() {
            return rank == Rank.SCRIBA && passed.size() >= 3
                    && passed.values().stream().anyMatch(score -> score >= MASTER_QUESTIONS);
        }

        /** The discipline whose master accepts them, the first passed without a mistake. */
        public Optional<Discipline> master() {
            return passed.entrySet().stream().filter(entry -> entry.getValue() >= MASTER_QUESTIONS)
                    .map(Map.Entry::getKey).findFirst();
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            if (rank != null) {
                tag.putString("Rank", rank.id());
            }
            tag.putInt("Fee", fee);
            tag.putLong("FeeSetAt", feeSetAt);
            tag.putLong("PaidUntil", paidUntil);
            tag.putLong("FailedAt", failedAt);
            CompoundTag passedTag = new CompoundTag();
            passed.forEach((discipline, score) -> passedTag.putInt(discipline.id(), score));
            tag.put("Passed", passedTag);
            CompoundTag triedTag = new CompoundTag();
            triedAt.forEach((discipline, time) -> triedTag.putLong(discipline.id(), time));
            tag.put("Tried", triedTag);
            return tag;
        }

        private static Student load(CompoundTag tag) {
            Student student = new Student();
            student.rank = Rank.byId(tag.getString("Rank")).orElse(null);
            student.fee = tag.getInt("Fee");
            student.feeSetAt = tag.getLong("FeeSetAt");
            student.paidUntil = tag.getLong("PaidUntil");
            student.failedAt = tag.getLong("FailedAt");
            CompoundTag passedTag = tag.getCompound("Passed");
            passedTag.getAllKeys().forEach(key -> Discipline.byId(key)
                    .ifPresent(discipline -> student.passed.put(discipline, passedTag.getInt(key))));
            CompoundTag triedTag = tag.getCompound("Tried");
            triedTag.getAllKeys().forEach(key -> Discipline.byId(key)
                    .ifPresent(discipline -> student.triedAt.put(discipline, triedTag.getLong(key))));
            return student;
        }
    }

    private final Map<UUID, Student> students = new HashMap<>();

    /** The house's book, kept in the overworld. */
    public static Enrollments of(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(Enrollments::load, Enrollments::new,
                NAME);
    }

    /** What the house knows of {@code who}: a new page, if nothing. */
    public Student student(UUID who) {
        return students.computeIfAbsent(who, uuid -> new Student());
    }

    /** What the house knows of {@code who}, if anything. */
    public Optional<Student> find(UUID who) {
        return Optional.ofNullable(students.get(who));
    }

    /**
     * The fee the council sets for {@code right} questions right of {@code asked} (docs/mago-design.md, 4.1): who barely
     * passes pays dear, who goes well pays little, who misses nothing pays nothing, and is paid besides.
     */
    public static int feeFor(int right, int asked) {
        int missed = asked - right;
        return switch (missed) {
            case 0 -> -3;
            case 1 -> 5;
            case 2 -> 12;
            default -> 20;
        };
    }

    /** The council examined {@code who}: admitted (an Auditor, if they were nothing) and the fee set, or failed. */
    public void examined(UUID who, int right, int asked, long now) {
        Student student = student(who);
        if (right < ADMITTED) {
            student.failedAt = now;
        } else {
            student.fee = feeFor(right, asked);
            student.feeSetAt = now;
            if (student.rank == null) {
                student.rank = Rank.AUDITOR;
            }
        }
        setDirty();
    }

    /** {@code who} paid the period (or was paid it): it runs seven days from now. */
    public void paid(UUID who, long now) {
        Student student = student(who);
        student.paidUntil = now + PERIOD;
        setDirty();
    }

    /**
     * The master of {@code discipline} examined {@code who}: passed or not; a passed Auditor becomes a Scriba. Returns
     * whether they passed.
     */
    public boolean examinedBy(UUID who, Discipline discipline, int right, long now) {
        Student student = student(who);
        student.triedAt.put(discipline, now);
        boolean passed = right >= PASSED;
        if (passed) {
            student.passed.merge(discipline, right, Math::max);
            if (student.rank == Rank.AUDITOR) {
                student.rank = Rank.SCRIBA;
            }
        }
        setDirty();
        return passed;
    }

    /** {@code who} becomes a Lector, if they may. */
    public boolean makeLector(UUID who) {
        Student student = student(who);
        if (!student.mayRead()) {
            return false;
        }
        student.rank = Rank.LECTOR;
        setDirty();
        return true;
    }

    /**
     * The period of {@code who} ends now, and every wait for a new examination with it: for a command, to try the
     * next period without waiting seven days.
     */
    public void endPeriod(UUID who, long now) {
        Student student = student(who);
        student.paidUntil = Math.min(student.paidUntil, now);
        student.feeSetAt = Math.min(student.feeSetAt, now - 1);
        student.failedAt = Long.MIN_VALUE / 2;
        student.triedAt.clear();
        setDirty();
    }

    /** {@code who} is struck off the book: for a test or a command. */
    public void forget(UUID who) {
        students.remove(who);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        students.forEach((uuid, student) -> {
            CompoundTag entry = student.save();
            entry.putUUID("Who", uuid);
            list.add(entry);
        });
        tag.put("Students", list);
        return tag;
    }

    private static Enrollments load(CompoundTag tag) {
        Enrollments enrollments = new Enrollments();
        for (Tag entry : tag.getList("Students", Tag.TAG_COMPOUND)) {
            CompoundTag student = (CompoundTag) entry;
            enrollments.students.put(student.getUUID("Who"), Student.load(student));
        }
        return enrollments;
    }
}
