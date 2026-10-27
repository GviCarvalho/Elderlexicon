package com.elderlexicon.mod.university;

import com.elderlexicon.mod.dialogue.Dialogue;
import com.elderlexicon.mod.dialogue.Dialogue.Choice;
import com.elderlexicon.mod.dialogue.Dialogue.Line;
import com.elderlexicon.mod.mind.Option;
import com.elderlexicon.mod.spell.function.MarkHelper;
import com.elderlexicon.mod.spell.mark.MarkCost;
import com.elderlexicon.mod.wizard.Wizard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Locale;
import java.util.UUID;

/**
 * An Emissary's errand (docs/mago-design.md, 5j): a Wizard of a university sent to one player with a message. It seeks
 * them out, waits for them to hear it, and gives them the Rector's letter and a map to the house. If they want, it takes
 * them there the way a mage moves a thing to a place, for the language is the only magic there is: it marks them and
 * reads, in a trance, {@code x y z ubis <their mark> vocant}, the courtyard of the house written as the place, and goes
 * with them ({@code x y z ubis ego vocant}). So long a way is more than a caster's own energy: the house sets in its wand
 * a gem of its own, charged with what the journey costs, and the reading draws on it as on any setting.
 * <p>
 * While it seeks them it keeps them: whatever comes for them, it strikes first.
 */
public final class Emissary {

    /** What it brings: the first invitation, or the house calling back one who is away and owes a period. */
    public enum Message {
        INVITATION, RETURN;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Message byId(String id) {
            return RETURN.id().equals(id) ? RETURN : INVITATION;
        }
    }

    private enum Stage {
        /** Walking to the player. */
        SEEKING,
        /** Beside them, until they hear it. */
        WAITING,
        /** Reading the call that takes them both to the house. */
        SENDING,
        /** At the house with them: it tells them where to go. */
        ARRIVED,
        /** Its errand done: away, out of sight. */
        LEAVING
    }

    /** How near it comes to speak. */
    private static final double SPEAKING = 3.5D;
    /** So far from it, the one it seeks is not near it. */
    private static final double LOST = 64.0D;
    /** How long it waits beside them to be heard (five minutes), before it leaves the letter with them. */
    private static final int PATIENCE = 6000;
    /** How long without its player near (away, gone, asleep in another world) before it gives up. */
    private static final int GIVES_UP = 1200;
    /** How many times it reads the call again when a blow broke its trance. */
    private static final int READINGS = 3;
    /** Where it brings them: the courtyard of the house, south of the fountain, facing the council's hall. */
    private static final int COURTYARD_SOUTH = 14;
    /** How near the courtyard counts as there. */
    private static final double THERE = 24.0D;
    /** What the house adds to the reckoning of the journey, for the reading's own share. */
    private static final double MARGIN = 1.2D;
    /** How long it stays with them at the house before it goes. */
    private static final int WELCOME_TICKS = 100;
    /** How far it walks off before it is out of sight, and how long it takes at most. */
    private static final double GONE = 28.0D;
    private static final int LEAVING_TICKS = 600;
    /** Its pace: brisk while it seeks the player, a walk when it goes. */
    private static final double BRISK = 0.85D;
    private static final double WALK = 0.6D;

    private final Wizard body;
    private final UUID player;
    private final Message message;
    private final BlockPos university;
    private Stage stage = Stage.SEEKING;
    private int ticks;
    private int away;
    /** Readings of the call begun, and whether one is under way now. */
    private int readings;
    private boolean reading;
    /** The mark the player had before it marked them, given back after the journey ("" for none). */
    @Nullable
    private String formerMark;

    public Emissary(Wizard body, UUID player, Message message, BlockPos university) {
        this.body = body;
        this.player = player;
        this.message = message;
        this.university = university;
    }

    public UUID player() {
        return player;
    }

    /** The one it seeks, when they are near it: whom it keeps from harm. */
    @Nullable
    public ServerPlayer ward(ServerLevel level) {
        ServerPlayer target = level.getServer().getPlayerList().getPlayer(player);
        return target != null && target.level() == level && target.isAlive() && target.distanceTo(body) < LOST
                ? target : null;
    }

    /** Whether it is on its way still (not leaving): it does not stop to write or talk with other casters. */
    public boolean busy() {
        return stage != Stage.LEAVING;
    }

    /** Back on its errand after something else moved it (a fight). */
    public void resume() {
    }

    // ------------------------------------------------------------------ living

    public void tick(ServerLevel level) {
        ticks++;
        ServerPlayer target = level.getServer().getPlayerList().getPlayer(player);
        boolean near = target != null && target.level() == level && target.isAlive()
                && target.distanceTo(body) < 3.0D * LOST;
        away = near ? 0 : away + 1;
        if (away > GIVES_UP) {
            if (target != null) {
                unmark(target);
            }
            vanish(level);
            return;
        }
        if (!near) {
            body.getNavigation().stop();
            return;
        }
        if (body.getTarget() != null) {
            return; // a fight first: the errand waits
        }
        switch (stage) {
            case SEEKING -> seek(target);
            case WAITING -> await(target);
            case SENDING -> send(level, target);
            case ARRIVED -> welcome(target);
            case LEAVING -> leave(level, target);
        }
    }

    private void seek(ServerPlayer target) {
        if (body.distanceTo(target) <= SPEAKING) {
            body.getNavigation().stop();
            stage = Stage.WAITING;
            ticks = 0;
            say(target, greeting(target));
            return;
        }
        if (ticks % 20 == 1) {
            body.getNavigation().moveTo(target, BRISK);
        }
    }

    private void await(ServerPlayer target) {
        body.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (body.distanceTo(target) > SPEAKING + 3.0D && ticks % 20 == 0) {
            body.getNavigation().moveTo(target, BRISK);
        }
        if (ticks > PATIENCE) {
            // Not heard: it leaves the letter with them, and goes.
            Emissaries.deliver(target, message, university);
            say(target, Component.literal("Deixo a carta com você. O conselho o espera."));
            depart();
        }
    }

    // ------------------------------------------------------------------ the journey

    /**
     * The call read, and what came of it: there, it welcomes them; its trance broken by a blow, it reads again; read and
     * not there (the place would not take them), it gives them the map's way and goes.
     */
    private void send(ServerLevel level, ServerPlayer target) {
        body.getNavigation().stop();
        body.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (body.casterMind().inTrance() || body.casterMind().releasing()) {
            return;
        }
        if (reading) {
            reading = false;
            if (Vec3.atCenterOf(arrival(level)).distanceTo(target.position()) < THERE) {
                unmark(target);
                stage = Stage.ARRIVED;
                ticks = 0;
                say(target, Component.literal("Chegamos: este é o pátio da Universidade. O Rector recebe quem chega no "
                        + "salão do conselho, ao norte, depois da fonte. Que a casa lhe ensine bem."));
                return;
            }
        }
        if (readings >= READINGS) {
            unmark(target);
            say(target, Component.literal("A leitura não o levou. Siga o mapa: o conselho o espera."));
            depart();
            return;
        }
        if (ticks % 10 == 0) {
            read(level, target);
        }
    }

    /** It marks them, sets the house's journey in its gem, and begins to read the call. */
    private void read(ServerLevel level, ServerPlayer target) {
        BlockPos to = arrival(level);
        String mark = mark(target);
        double cost = MarkCost.movement(target.getMaxHealth(), target.position().distanceTo(Vec3.atCenterOf(to)))
                + MarkCost.movement(body.getMaxHealth(), body.position().distanceTo(Vec3.atCenterOf(to)));
        Emissaries.seal(body, cost * MARGIN);
        String place = to.getX() + " " + to.getY() + " " + to.getZ();
        List<Option.Line> lines = List.of(
                new Option.Line(List.of((place + " ubis " + mark + " vocant").split(" ")), 0),
                new Option.Line(List.of((place + " ubis ego vocant").split(" ")), 1));
        Option call = new Option("chamado da casa", lines, List.of(), cost, false);
        if (body.casterMind().readToward(call, target.getEyePosition())) {
            readings++;
            reading = true;
        }
    }

    /** Where it brings them: the courtyard of the house, drawn up now if no one has been there yet. */
    private BlockPos arrival(ServerLevel level) {
        ServerLevel world = level.getServer().overworld();
        // A campus: the courtyard before the core's gate.
        Optional<BlockPos> courtyard = UniversityStructure.courtyard(world, university);
        if (courtyard.isPresent()) {
            BlockPos at = courtyard.get();
            world.getChunk(at.getX() >> 4, at.getZ() >> 4);
            return new BlockPos(at.getX(), world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(),
                    at.getZ()), at.getZ());
        }
        ChunkPos heart = new ChunkPos(university);
        int x = heart.getMiddleBlockX();
        int z = heart.getMiddleBlockZ() + COURTYARD_SOUTH;
        world.getChunk(x >> 4, z >> 4); // made, if it was not
        return new BlockPos(x, world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /** A mark of its own on them, for the call: the one they had is kept, and given back after. */
    private String mark(ServerPlayer target) {
        String mark = "rumo_" + player.toString().replace("-", "").substring(0, 8);
        if (formerMark == null) {
            formerMark = MarkHelper.markForEntity(target).orElse("");
        }
        MarkHelper.applyMark(target, mark);
        return mark;
    }

    private void unmark(ServerPlayer target) {
        if (formerMark != null) {
            MarkHelper.applyMark(target, formerMark);
            formerMark = null;
        }
    }

    private void welcome(ServerPlayer target) {
        body.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (ticks > WELCOME_TICKS) {
            depart();
        }
    }

    // ------------------------------------------------------------------ going

    private void leave(ServerLevel level, ServerPlayer target) {
        if (body.distanceTo(target) > GONE || ticks > LEAVING_TICKS) {
            vanish(level);
            return;
        }
        if (body.getNavigation().isDone() && ticks % 20 == 0) {
            Vec3 off = body.position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D);
            off = off.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : off.normalize();
            BlockPos at = ground(level, body.position().add(off.scale(GONE + 8.0D)));
            if (at != null) {
                body.getNavigation().moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, WALK);
            }
        }
    }

    private void depart() {
        stage = Stage.LEAVING;
        ticks = 0;
        body.getNavigation().stop();
    }

    /** Gone, as a mage goes: in a breath of light. */
    private void vanish(ServerLevel level) {
        level.sendParticles(ParticleTypes.ENCHANT, body.getX(), body.getY() + 1.0D, body.getZ(), 40, 0.4D, 0.8D, 0.4D,
                0.8D);
        body.discard();
    }

    /**
     * Dry ground to stand on at {@code at}'s column (where its feet go), or null over water, lava or powder snow. Grass,
     * flowers and a thin snow are stood in, as one walks through them; a deeper snow is stood on.
     */
    @Nullable
    static BlockPos ground(ServerLevel level, Vec3 at) {
        if (!level.hasChunkAt(BlockPos.containing(at))) {
            return null; // not loaded: no ground it can see
        }
        BlockPos feet = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
        for (int lift = 0; lift < 2; lift++, feet = feet.above()) {
            if (standsOn(level, feet.below()) && passable(level, feet) && passable(level, feet.above())) {
                return feet;
            }
        }
        return null;
    }

    /** Whether one can stand on the block at {@code pos}: something solid, not a fluid, not powder snow it sinks in. */
    private static boolean standsOn(ServerLevel level, BlockPos pos) {
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && !state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                && !state.getCollisionShape(level, pos).isEmpty();
    }

    /** Whether one can be in the block at {@code pos}: nothing in the way, no fluid, no fire, no powder snow. */
    private static boolean passable(ServerLevel level, BlockPos pos) {
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && !state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                && !state.is(net.minecraft.tags.BlockTags.FIRE) && state.getCollisionShape(level, pos).isEmpty();
    }

    // ------------------------------------------------------------------ what it says

    private static void say(ServerPlayer player, Component text) {
        player.sendSystemMessage(Component.literal("<Emissário> ").withStyle(net.minecraft.ChatFormatting.GOLD)
                .append(text.copy().withStyle(net.minecraft.ChatFormatting.WHITE)));
    }

    private Component greeting(ServerPlayer target) {
        return Component.literal(target.getGameProfile().getName() + "! Venho da Universidade, com uma mensagem para "
                + "você. Fale comigo.");
    }

    /** {@code speaker} spoke to it: the one it was sent to hears its message; anyone else, that it is not theirs. */
    public void talk(ServerPlayer speaker) {
        if (!speaker.getUUID().equals(player)) {
            Dialogue.open(speaker, body, new Line(Component.literal("Trago uma mensagem da Universidade, mas não é para "
                    + "você. Se quiser estudar, a casa recebe quem pede admissão ao Rector."), bye()));
            return;
        }
        Dialogue.open(speaker, body, switch (stage) {
            case SEEKING, WAITING -> message(speaker);
            case SENDING -> new Line(Component.literal("Fique perto de mim. A leitura já começa."), bye());
            case ARRIVED -> new Line(Component.literal("O Rector o recebe no salão do conselho, ao norte, depois da "
                    + "fonte."), bye());
            case LEAVING -> new Line(Component.literal("Minha mensagem já foi dada. O conselho o espera."), bye());
        });
    }

    private Line message(ServerPlayer target) {
        String where = Emissaries.distance(target, university) + " blocos daqui, ao " + Emissaries.heading(target,
                university);
        Component said = message == Message.INVITATION
                ? Component.literal("O conselho soube que o transe despertou em você, e o convida a estudar a Língua "
                + "Antiga na Universidade, a uns " + where + ". Lá se aprende o que as runas são e como vivê-las; o "
                + "conselho examina quem chega, e cada um paga pelo que sabe. Trago uma carta do Rector e um mapa.")
                : Component.literal("O período terminou, e a casa não o vê. O conselho o chama de volta à "
                + "Universidade, a uns " + where + ": " + Emissaries.owing(target) + " Trago uma carta do Rector e "
                + "um mapa.");
        return new Line(said,
                new Choice(Component.literal("Aceito. Pode me levar até lá?"), p -> {
                    Emissaries.deliver(p, message, university);
                    stage = Stage.SENDING;
                    ticks = 0;
                    return new Line(Component.literal("Fique onde está. Vou marcá-lo e ler o chamado que leva o que é "
                            + "marcado a um lugar: o pátio da casa. A casa pôs neste engaste a força da viagem. Iremos "
                            + "juntos."), bye());
                }),
                new Choice(Component.literal("Aceito a carta. Vou sozinho."), p -> {
                    Emissaries.deliver(p, message, university);
                    depart();
                    return new Line(Component.literal("O mapa mostra o caminho. O conselho o espera."), bye());
                }),
                new Choice(Component.literal(message == Message.INVITATION ? "Não tenho interesse." : "Agora não."),
                        p -> {
                            depart();
                            return new Line(Component.literal(message == Message.INVITATION
                                    ? "Como quiser. A casa não esquece quem tem o dom: talvez nos vejamos de novo."
                                    : "A casa espera. Mas não para sempre."), bye());
                        }));
    }

    private static Choice bye() {
        return new Choice(Component.literal("Até logo."), p -> null);
    }

    // ------------------------------------------------------------------ kept with it

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Player", player);
        tag.putString("Message", message.id());
        tag.putLong("University", university.asLong());
        tag.putString("Stage", stage.name());
        if (formerMark != null) {
            tag.putString("FormerMark", formerMark);
        }
        return tag;
    }

    public static Emissary load(Wizard body, CompoundTag tag) {
        Emissary errand = new Emissary(body, tag.getUUID("Player"), Message.byId(tag.getString("Message")),
                BlockPos.of(tag.getLong("University")));
        try {
            errand.stage = Stage.valueOf(tag.getString("Stage"));
        } catch (IllegalArgumentException unknown) {
            errand.stage = Stage.SEEKING; // an errand of before (a guide on foot): it begins again
        }
        if (tag.contains("FormerMark")) {
            errand.formerMark = tag.getString("FormerMark");
        }
        return errand;
    }
}
