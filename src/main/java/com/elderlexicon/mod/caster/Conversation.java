package com.elderlexicon.mod.caster;

import net.minecraft.world.entity.Mob;

import java.util.List;

/**
 * Two casters talking, in the Old Tongue (docs/mago-design.md, 5b). They take turns: one says something (a sentence of
 * its own, the other's said back, or the other's corrected) and the other takes it well or badly. The words are seen
 * above the head of who says them, as a reading is, and a player who does not know the runes sees only that they talk,
 * and how it goes: who is pleased, who is not. Who hears learns a little of the runes it does not know.
 * <p>
 * Taken badly too often, it comes to blows ({@link CasterMind#quarrel}): a duel, until one yields; but not among
 * people living in peace, where the one offended walks off ({@link CasterMind#fallOut}). Wizards and Sorcerers
 * are neither friends nor foes: they talk as any two casters do, and fight only over what is said.
 */
final class Conversation {

    /** How near another caster must be to be talked to. */
    static final double REACH = 10.0D;
    /** How near they stand to talk. */
    static final double CLOSE = 3.0D;
    /** Ticks between one saying something and the next. */
    private static final int TURN_TICKS = 50;
    /** How long after something is said the other shows how it took it. */
    private static final int REACT_TICKS = 20;
    /** Ticks they take to come close before the first word. */
    private static final int OPENING_TICKS = 20;

    private final CasterMind first;
    private final CasterMind second;
    private CasterMind speaker;
    private int turnsLeft;
    private int ticks;
    private int reactAt = Integer.MIN_VALUE;
    private CasterMind.Utterance said;
    private boolean over;

    private Conversation(CasterMind first, CasterMind second, int turns) {
        this.first = first;
        this.second = second;
        this.speaker = first;
        this.turnsLeft = turns;
        this.ticks = -OPENING_TICKS;
    }

    /** A conversation {@code first} begins with {@code second}: both are in it from now. */
    static Conversation begin(CasterMind first, CasterMind second) {
        Conversation conversation = new Conversation(first, second, 4 + first.owner().getRandom().nextInt(5));
        first.joined(conversation);
        second.joined(conversation);
        return conversation;
    }

    /** The one {@code mind} talks with. */
    CasterMind other(CasterMind mind) {
        return mind == first ? second : first;
    }

    /** Whether {@code mind} leads it: the one whose tick runs it. */
    boolean ledBy(CasterMind mind) {
        return mind == first;
    }

    boolean over() {
        return over;
    }

    /** One tick of the conversation, run by the one who began it. */
    void tick() {
        if (over) {
            return;
        }
        if (!still(first) || !still(second) || first.owner().distanceTo(second.owner()) > REACH * 1.5D) {
            end();
            return;
        }
        ticks++;
        if (said != null && ticks == reactAt) {
            CasterMind listener = other(speaker);
            boolean quarrel = listener.hear(said, speaker);
            if (quarrel || --turnsLeft <= 0) {
                end();
                if (quarrel) {
                    listener.fallOut(speaker);
                }
                return;
            }
            speaker = listener;
        }
        if (ticks >= 0 && ticks % TURN_TICKS == 0 && ticks > reactAt) {
            said = speaker.utter(said == null ? null : said.words());
            speaker.say(said);
            reactAt = ticks + REACT_TICKS;
        }
    }

    /** Whether {@code mind} can go on talking: alive, at peace, still at it. */
    private static boolean still(CasterMind mind) {
        Mob owner = mind.owner();
        return owner.isAlive() && !owner.isRemoved() && mind.calm() && mind.atLeisure();
    }

    /** It ends: each goes its way, and lets go a little of what annoyed it. */
    void end() {
        if (over) {
            return;
        }
        over = true;
        first.left(this);
        second.left(this);
    }

    /** What was said last, for a test. */
    List<String> lastSaid() {
        return said == null ? List.of() : said.words();
    }
}
