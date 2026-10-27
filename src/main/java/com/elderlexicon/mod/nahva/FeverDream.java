package com.elderlexicon.mod.nahva;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.SpellTicks;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * The fever dream of a rune (docs/aprendizado-runas-design.md, 4b): the mage dreams on their own kern, and the kern turns
 * into a castle of rooms that make no sense ({@link FeverHalls}), built anew in each scene, which change without
 * warning: the rune's one extreme, the memories of the body, and its other extreme. In each extreme the air does
 * something to the dreamer, and holding the right thing keeps it off; such things lie about the rooms. Among the
 * memories, the places where the body lived the rune are in some rooms, turned as the rune turns things, and the rift is
 * in one of them.
 * <ul>
 *   <li><b>igni:</b> the heat (the air burns; something cold) and the cold (the air freezes; something warm).</li>
 *   <li><b>aqua:</b> the flood (rooms sunk into deep pools; a sponge keeps the breath) and the drought (the air dries
 *       the body out; a bottle of water). In the memories, water is sand and sand is water held still.</li>
 *   <li><b>aura:</b> the gale (winds through the rooms, the pressure in the ears, sounds that echo; a feather) and the
 *       void (no air, no sound; an empty bottle, the air kept in it). In the memories, what was full is glass.</li>
 *   <li><b>firmo:</b> the weight (low rooms, a body too heavy to fly or to hurry; something light) and the burial (walls
 *       that close in, row by row; a pickaxe opens a way). In the memories, the stone is ore.</li>
 *   <li><b>vis:</b> each extreme one of the eight of the four sources, drawn anew each time. In the memories, all is
 *       crystal.</li>
 * </ul>
 * The scenes go round until the dreamer goes through the rift, or dies. Everything the dream puts on the kern, and
 * everything the dreamer builds or breaks there, is taken off at waking ({@link KernDressing}). The client is told each
 * scene, for its fever (FeverPacket).
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class FeverDream {

    /** The realm a fever dream is dreamed in: the mage's own kern, dressed. */
    public static final String REALM = "fever";

    /** The scenes of the dream; their order is the one they come in. */
    public enum Scene {
        FIRST, MEMORY, SECOND
    }

    /** What the air of an extreme does each second to one who does not hold the right thing. */
    private interface Air {
        void breathe(ServerPlayer dreamer, CompoundTag state);
    }

    /**
     * An extreme of a rune: its halls, what lies about them, what keeps its air off, the air, and what more the scene
     * does (the rule of a verb).
     */
    private record Extreme(FeverHalls.Stuff stuff, Supplier<List<ItemStack>> things, Predicate<ItemStack> keeps,
                           ParticleOptions mist, Air air, FeverVerbs.Rule rule) {

        Extreme(FeverHalls.Stuff stuff, Supplier<List<ItemStack>> things, Predicate<ItemStack> keeps,
                ParticleOptions mist, Air air) {
            this(stuff, things, keeps, mist, air, FeverVerbs.Rule.NONE);
        }
    }

    /**
     * A rune's dream: its two extremes, how its memories are turned, and whether it is a verb's (no flight in it, a time
     * limit, longer scenes, each told by a hint).
     */
    private record Dream(Extreme first, Extreme second, UnaryOperator<BlockState> swap, boolean verb) {

        Dream(Extreme first, Extreme second, UnaryOperator<BlockState> swap) {
            this(first, second, swap, false);
        }
    }

    /** A verb's scene: its halls and its rule; what lies about them; no air to keep off. */
    private static Extreme verb(FeverHalls.Stuff stuff, FeverVerbs.Rule rule, Supplier<List<ItemStack>> things) {
        return new Extreme(stuff, things, stack -> false, ParticleTypes.ENCHANT, (dreamer, state) -> {
        }, rule);
    }

    /** A verb's dream: the verb, its memories held still, the verb turned round. */
    private static Dream verbs(Extreme verb, Extreme turned) {
        return new Dream(verb, turned, FeverDream::still, true);
    }

    /** The four sources, whose extremes vis draws from. */
    private static final List<String> SOURCES = List.of("igni", "aqua", "aura", "firmo");

    private static final Map<String, Dream> DREAMS = withFacts(Map.ofEntries(
            Map.entry("iactare", verbs(verb(FeverVerbs.THROWN, FeverVerbs.THROWN_RULE, List::of),
                    verb(FeverVerbs.THROWN, FeverVerbs.PULLED_RULE, List::of))),
            Map.entry("vocant", verbs(verb(FeverVerbs.GAZE, FeverVerbs.GAZE_RULE, List::of),
                    verb(FeverVerbs.SUMMONED, FeverVerbs.SUMMONED_RULE, List::of))),
            Map.entry("impediunt", verbs(verb(FeverVerbs.WARD, FeverVerbs.FLEEING_RULE, List::of),
                    verb(FeverVerbs.WARD, FeverVerbs.FOLLOWING_RULE, List::of))),
            Map.entry("vertere", verbs(verb(FeverVerbs.CHANGING, FeverVerbs.CHANGING_RULE, List::of),
                    verb(FeverVerbs.STILL, FeverVerbs.STILL_RULE, List::of))),
            Map.entry("ligabis", verbs(verb(FeverVerbs.BOUND, FeverVerbs.PAIRS_RULE,
                            () -> items(Items.IRON_PICKAXE, Items.STONE_PICKAXE)),
                    verb(FeverVerbs.LOOSE, FeverVerbs.LOOSE_RULE, () -> items(Items.LEAD)))),
            Map.entry("reframe", verbs(verb(FeverVerbs.NAMED, FeverVerbs.NAMED_RULE,
                            () -> List.of(FeverVerbs.tag("Porta"), FeverVerbs.tag("Porta"), FeverVerbs.tag("Parede"))),
                    verb(FeverVerbs.NAMELESS, FeverVerbs.Rule.NONE, List::of))),
            Map.entry("surgit", verbs(verb(FeverVerbs.DARK, FeverVerbs.DARK_RULE,
                            () -> items(Items.BOOK, Items.WRITABLE_BOOK)),
                    verb(FeverVerbs.BARE, FeverVerbs.Rule.NONE, List::of))),
            // The filters: measures of the dreamer, not halls (docs/aprendizado-runas-design.md, 4d).
            Map.entry("quantum", verbs(verb(FeverHalls.OLD, FeverMeasures.SMALL, List::of),
                    verb(FeverHalls.OLD, FeverMeasures.MODEL, List::of))),
            Map.entry("chronos", verbs(verb(FeverHalls.OLD, FeverMeasures.LOOP, List::of),
                    verb(FeverHalls.OLD, FeverMeasures.REWIND, List::of))),
            Map.entry("ubis", verbs(verb(FeverHalls.OLD, FeverMeasures.NUMBERS, List::of),
                    verb(FeverHalls.OLD, FeverMeasures.SAME, List::of))),
            Map.entry("tenet", verbs(verb(FeverHalls.OLD, FeverMeasures.DRAIN, List::of),
                    verb(FeverHalls.OLD, FeverMeasures.BODY, List::of))),
            Map.entry("igni", new Dream(
                    new Extreme(FeverHalls.HEAT, () -> items(Items.SNOWBALL, Items.BLUE_ICE, Items.PACKED_ICE),
                            FeverDream::cold, ParticleTypes.FLAME, FeverDream::burn),
                    new Extreme(FeverHalls.COLD, () -> items(Items.TORCH, Items.BLAZE_ROD, Items.MAGMA_BLOCK),
                            FeverDream::warm, ParticleTypes.SNOWFLAKE, FeverDream::freeze),
                    FeverDream::hotForCold)),
            Map.entry("aqua", new Dream(
                    new Extreme(FeverHalls.FLOOD, () -> items(Items.SPONGE),
                            stack -> stack.is(Items.SPONGE) || stack.is(Items.WET_SPONGE), ParticleTypes.BUBBLE,
                            (dreamer, state) -> {
                            }),
                    new Extreme(FeverHalls.DROUGHT, FeverDream::waterBottles,
                            stack -> stack.is(Items.POTION) && PotionUtils.getPotion(stack) == Potions.WATER,
                            ParticleTypes.WHITE_ASH, FeverDream::dryOut),
                    FeverDream::wetForDry)),
            Map.entry("aura", new Dream(
                    new Extreme(FeverHalls.GALE, () -> items(Items.FEATHER), stack -> stack.is(Items.FEATHER),
                            ParticleTypes.CLOUD, FeverDream::pressure),
                    new Extreme(FeverHalls.VOID, () -> items(Items.GLASS_BOTTLE), stack -> stack.is(Items.GLASS_BOTTLE),
                            ParticleTypes.ASH, (dreamer, state) -> {
                            }),
                    FeverDream::fullForEmpty)),
            Map.entry("firmo", new Dream(
                    new Extreme(FeverHalls.WEIGHT, () -> items(Items.FEATHER, Items.PAPER),
                            stack -> stack.is(Items.FEATHER) || stack.is(Items.PAPER), new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.FALLING_DUST,
                                    Blocks.GRAVEL.defaultBlockState()),
                            FeverDream::heavy),
                    new Extreme(FeverHalls.BURIAL, () -> items(Items.IRON_PICKAXE, Items.STONE_PICKAXE),
                            stack -> false, ParticleTypes.MYCELIUM, FeverDream::closeIn),
                    FeverDream::stoneForOre))));

    /** The facts, each a mirror: the fact, its memories, the fact turned round (docs/aprendizado-runas-design.md, 4e). */
    private static Map<String, Dream> withFacts(Map<String, Dream> dreams) {
        Map<String, Dream> all = new java.util.HashMap<>(dreams);
        for (String fact : FeverMirror.FACTS.keySet()) {
            all.put(fact, verbs(verb(FeverHalls.OLD, FeverMirror.rule(fact, false), List::of),
                    verb(FeverHalls.OLD, FeverMirror.rule(fact, true), List::of)));
        }
        return java.util.Collections.unmodifiableMap(all);
    }

    /** How long a scene lasts, at least, and how much longer it may; a verb's, which is a maze, longer. */
    private static final int SCENE_TICKS = 500;
    private static final int SCENE_SPREAD = 400;
    private static final int VERB_SCENE_TICKS = 1200;
    private static final int VERB_SCENE_SPREAD = 600;
    /** How long a verb's dream lasts at most: then it comes apart, and the dreamer wakes without the rune. */
    private static final int VERB_LIMIT = 6000;
    /** How often the walls of the burial come in, in seconds, and how far they come at most. */
    private static final int CLOSING_SECONDS = 6;
    private static final int CLOSING_ROWS = 3;
    /** How hard the wind of the gale pushes. */
    private static final double WIND = 0.07D;
    /** What marks the things of the dream. */
    private static final String DREAMT = ElderLexicon.MODID + "_fever";

    private FeverDream() {
    }

    /**
     * vis, the four sources in one: each extreme is one of the eight of the four sources, drawn anew each time; in the
     * memories, everything is crystal.
     */
    private static final String VIS = "vis";

    /** Whether a rune is dreamed this way. */
    public static boolean dreams(String rune) {
        return DREAMS.containsKey(rune) || VIS.equals(rune);
    }

    /** The dreamer is told what they are dreaming, for the fever on their screen: of vis, the extreme it drew. */
    static void tell(ServerPlayer player, CompoundTag state) {
        Scene scene = Scene.values()[Mth.clamp(state.getInt("scene"), 0, Scene.values().length - 1)];
        Dream dream = DREAMS.get(state.getString("rune"));
        if (dream != null && dream.verb() && scene != Scene.MEMORY) {
            player.displayClientMessage(Component.translatable("dream.elderlexicon.scene." + state.getString("rune")
                    + (scene == Scene.FIRST ? ".verb" : ".turned")).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE,
                    net.minecraft.ChatFormatting.ITALIC), true);
        }
        if (VIS.equals(state.getString("rune")) && scene != Scene.MEMORY) {
            SpellingNetwork.sendFever(player, state.getString("drawnRune"), state.getInt("drawnSide") == 0 ? 0 : 2);
        } else {
            SpellingNetwork.sendFever(player, state.getString("rune"), scene.ordinal());
        }
    }

    // ------------------------------------------------------------------ falling into it

    /**
     * The kern laid out (if it never was) and dressed in the first scene: where the dreamer appears, and where the
     * rift is now. {@code state} holds the mage and the rune, and keeps the scene.
     */
    static Realms.Built begin(ServerLevel level, BlockPos origin, CompoundTag state) {
        KernRealm.build(level, origin);
        Dream dream = DREAMS.get(state.getString("rune"));
        if (dream != null && dream.verb()) {
            state.putLong("deadline", level.getGameTime() + VERB_LIMIT);
        }
        Vec3 rift = enter(level, origin, state, Scene.FIRST);
        BlockPos island = origin.offset(0, KernRealm.Y, 0);
        Extreme first = extreme(state);
        Vec3 start = first == null ? null : first.rule().start(island, state);
        return new Realms.Built(start != null ? start : FeverHalls.spawn(island), rift);
    }

    /** Each tick of the dream: the doorways, the wind, the void; when a scene is over, the next, and the rift moves. */
    static void everyTick(ServerPlayer player, CompoundTag dream) {
        CompoundTag state = dream.getCompound("state");
        ServerLevel level = player.serverLevel();
        BlockPos origin = new BlockPos(dream.getInt("ox"), 0, dream.getInt("oz"));
        BlockPos island = origin.offset(0, KernRealm.Y, 0);
        FeverHalls.step(player, state);
        Dream mine = DREAMS.get(state.getString("rune"));
        if (mine != null && mine.verb() && !player.isCreative()
                && (player.getAbilities().mayfly || player.getAbilities().flying)) {
            // In a verb's dream the body does not fly: the mazes are walked (or thrown, or gazed) through.
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        Extreme extreme = extreme(state);
        if (extreme != null) {
            extreme.rule().tick(player, island, dream, state);
        }
        long deadline = state.getLong("deadline");
        if (deadline > 0L) {
            long left = deadline - level.getGameTime();
            if (left == 1200L || left == 200L) {
                player.displayClientMessage(Component.translatable("dream.elderlexicon.fading")
                        .withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC), true);
            }
            if (left <= 0L) {
                // The dream comes apart before the rift is found: waking, without the rune.
                state.putLong("deadline", 0L);
                player.sendSystemMessage(Component.translatable("dream.elderlexicon.faded")
                        .withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC));
                SpellTicks.schedule(player.server, 1, () -> Dreams.end(player, false));
                return;
            }
        }
        if (extreme != null && extreme.stuff().windy()) {
            Direction wind = FeverHalls.wind(island, player.position(), state);
            if (wind != null) {
                player.push(wind.getStepX() * WIND, 0.0D, wind.getStepZ() * WIND);
                player.hurtMarked = true;
            }
        }
        if (extreme != null && extreme.stuff() == FeverHalls.VOID && !holds(player, extreme.keeps())) {
            // No air to breathe: the breath runs out, as under water, faster than it comes back.
            player.setAirSupply(Math.max(-20, player.getAirSupply() - 6));
            if (player.getAirSupply() <= -20 && player.tickCount % 20 == 0) {
                player.hurt(player.damageSources().drown(), 2.0F);
            }
        }
        if (level.getGameTime() < state.getLong("until")) {
            return;
        }
        Scene next = Scene.values()[(state.getInt("scene") + 1) % Scene.values().length];
        Vec3 rift = enter(level, origin, state, next);
        dream.putDouble("riftX", rift.x);
        dream.putDouble("riftY", rift.y);
        dream.putDouble("riftZ", rift.z);
        // The halls are built anew round the dreamer: they stand in the middle of the room they were in. A scene that
        // is not the halls says where.
        Extreme now = extreme(state);
        Vec3 start = now == null ? null : now.rule().start(island, state);
        int[] room = FeverHalls.roomAt(island, player.position());
        Vec3 stand = start != null ? start : room == null ? FeverHalls.spawn(island)
                : Vec3.atBottomCenterOf(FeverHalls.middle(island, room[0], room[1]));
        player.teleportTo(stand.x, stand.y, stand.z);
        tell(player, state);
    }

    /** Whether the dreamer's body may not fly now: the weight. */
    static boolean grounded(CompoundTag dream) {
        CompoundTag state = dream.getCompound("state");
        Dream mine = DREAMS.get(state.getString("rune"));
        if (mine != null && mine.verb()) {
            return true;
        }
        Extreme extreme = extreme(state);
        return extreme != null && extreme.stuff() == FeverHalls.WEIGHT;
    }

    /** Each second: the air of the scene, and what holding the right thing does against it. */
    static void pulse(ServerPlayer dreamer, CompoundTag dream) {
        CompoundTag state = dream.getCompound("state");
        Extreme extreme = extreme(state);
        if (extreme == null) {
            return;
        }
        dreamer.serverLevel().sendParticles(dreamer, extreme.mist(), false, dreamer.getX(), dreamer.getY() + 1.0D,
                dreamer.getZ(), 30, 6.0D, 3.0D, 6.0D, 0.02D);
        state.putInt("seconds", state.getInt("seconds") + 1);
        if (extreme.stuff() == FeverHalls.BURIAL || !holds(dreamer, extreme.keeps())) {
            extreme.air().breathe(dreamer, state);
        } else {
            kept(dreamer, extreme);
        }
    }

    /** What holding the right thing does at once: the fire out, the breath back, the body light. */
    private static void kept(ServerPlayer dreamer, Extreme extreme) {
        dreamer.clearFire();
        dreamer.setTicksFrozen(0);
        if (extreme.stuff() == FeverHalls.FLOOD) {
            dreamer.setAirSupply(dreamer.getMaxAirSupply());
        }
        if (extreme.stuff() == FeverHalls.WEIGHT) {
            dreamer.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            if (!dreamer.getAbilities().mayfly) {
                dreamer.getAbilities().mayfly = true;
                dreamer.onUpdateAbilities();
            }
        }
    }

    /** Awake: the kern as the mage left it, the dream's things gone, the fever off. */
    static void over(ServerPlayer player, CompoundTag dream) {
        ServerLevel nahva = Nahva.level(player.server);
        if (nahva != null) {
            BlockPos origin = new BlockPos(dream.getInt("ox"), 0, dream.getInt("oz"));
            clearThings(nahva, origin);
            KernDressing.takeOff(nahva, player.getUUID());
        }
        SpellingNetwork.sendFever(player, "", -1);
    }

    /** A mage back after the server went down mid-dream: their kern undressed, or their fever told again. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (Dreams.isDreaming(player) && REALM.equals(Dreams.dream(player).getString("realm"))) {
            tell(player, Dreams.dream(player).getCompound("state"));
        } else if (KernDressing.wearing(player.server, player.getUUID())) {
            ServerLevel nahva = Nahva.level(player.server);
            if (nahva != null) {
                KernDressing.takeOff(nahva, player.getUUID());
            }
        }
    }

    /**
     * What the dreamer breaks in the dream (the walls of the burial, or anything) is gone without a trace, and comes back
     * at waking; it drops nothing.
     */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && feverish(player)
                && player.level() instanceof ServerLevel level) {
            event.setCanceled(true);
            CompoundTag dream = Dreams.dream(player);
            Extreme extreme = extreme(dream.getCompound("state"));
            if (extreme != null && extreme.rule().keeps(event.getState())) {
                return;
            }
            KernDressing.put(level, player.getUUID(), event.getPos(), Blocks.AIR.defaultBlockState());
            if (extreme != null) {
                extreme.rule().broke(player, new BlockPos(dream.getInt("ox"), KernRealm.Y, dream.getInt("oz")),
                        dream.getCompound("state"), event.getPos());
            }
        }
    }

    /** Using what is in hand on a block of the dream: the rule may take it (a name tag that makes a wall a door). */
    @SubscribeEvent
    public static void onUse(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !feverish(player)
                || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        CompoundTag dream = Dreams.dream(player);
        Extreme extreme = extreme(dream.getCompound("state"));
        if (extreme != null && extreme.rule().used(player, new BlockPos(dream.getInt("ox"), KernRealm.Y,
                dream.getInt("oz")), dream.getCompound("state"), event.getPos(), event.getFace(),
                event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    /** Striking a block of the dream: the rule may take it (tenet: what is touched is drunk). */
    @SubscribeEvent
    public static void onHit(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !feverish(player)
                || event.getAction() != net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock
                .Action.START) {
            return;
        }
        CompoundTag dream = Dreams.dream(player);
        Extreme extreme = extreme(dream.getCompound("state"));
        if (extreme != null && extreme.rule().hit(player, new BlockPos(dream.getInt("ox"), KernRealm.Y,
                dream.getInt("oz")), dream.getCompound("state"), event.getPos())) {
            event.setCanceled(true);
        }
    }

    /** A thing of the dream laid at {@code at}: what the dreamer needs, gone with the dream. */
    static void thing(ServerLevel level, Vec3 at, ItemStack stack) {
        ItemEntity thing = new ItemEntity(level, at.x, at.y, at.z, dreamt(stack));
        thing.getPersistentData().putBoolean(DREAMT, true);
        thing.setUnlimitedLifetime();
        thing.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(thing);
    }

    // ------------------------------------------------------------------ the rift, as the rules move it

    /** Where the rift is now. */
    static Vec3 rift(CompoundTag dream) {
        return new Vec3(dream.getDouble("riftX"), dream.getDouble("riftY"), dream.getDouble("riftZ"));
    }

    /** The rift moved to {@code at}. */
    static void riftAt(CompoundTag dream, Vec3 at) {
        dream.putDouble("riftX", at.x);
        dream.putDouble("riftY", at.y);
        dream.putDouble("riftZ", at.z);
    }

    /** A thing of the dream, gone with it (a statue). */
    static void mark(net.minecraft.world.entity.Entity thing) {
        thing.getPersistentData().putBoolean(DREAMT, true);
    }

    /** What the dreamer sets down in the dream is gone at waking, and what it stood in comes back. */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && feverish(player)
                && player.level() instanceof ServerLevel level) {
            KernDressing.note(level, player.getUUID(), event.getPos(), event.getBlockSnapshot().getReplacedBlock());
        }
    }

    private static boolean feverish(ServerPlayer player) {
        return Dreams.isDreaming(player) && REALM.equals(Dreams.dream(player).getString("realm"))
                && Nahva.isNahva(player.level());
    }

    // ------------------------------------------------------------------ the scenes

    /** The extreme the dream is in now, or null among the memories. */
    private static Extreme extreme(CompoundTag state) {
        Scene scene = Scene.values()[Mth.clamp(state.getInt("scene"), 0, Scene.values().length - 1)];
        if (scene == Scene.MEMORY) {
            return null;
        }
        if (VIS.equals(state.getString("rune"))) {
            Dream drawn = DREAMS.get(state.getString("drawnRune"));
            return drawn == null ? null : state.getInt("drawnSide") == 0 ? drawn.first() : drawn.second();
        }
        Dream dream = DREAMS.get(state.getString("rune"));
        return dream == null ? null : scene == Scene.FIRST ? dream.first() : dream.second();
    }

    /** The kern undressed and its halls built anew for {@code scene}; returns where its rift is. */
    private static Vec3 enter(ServerLevel level, BlockPos origin, CompoundTag state, Scene scene) {
        UUID mage = state.getUUID("mage");
        RandomSource random = level.getRandom();
        boolean vis = VIS.equals(state.getString("rune"));
        UnaryOperator<BlockState> swap = vis ? FeverDream::crystal
                : DREAMS.getOrDefault(state.getString("rune"), DREAMS.get("igni")).swap();
        KernDressing.takeOff(level, mage);
        clearThings(level, origin);
        BlockPos island = origin.offset(0, KernRealm.Y, 0);
        state.putInt("scene", scene.ordinal());
        state.putInt("seconds", 0);
        if (vis && scene != Scene.MEMORY) {
            // Of vis: any extreme of the four sources, drawn anew.
            state.putString("drawnRune", SOURCES.get(random.nextInt(SOURCES.size())));
            state.putInt("drawnSide", random.nextInt(2));
        }
        Extreme extreme = extreme(state);
        BlockPos rift;
        if (extreme == null) {
            rift = FeverHalls.build(level, mage, island, FeverHalls.OLD, memories(level, mage, island, state, random),
                    swap, random, state);
        } else {
            BlockPos placed = extreme.rule().place(level, mage, island, state, random);
            if (placed != null) {
                rift = placed;
                // No doorways elsewhere here: what the halls before left in the state is forgotten.
                state.put("elsewheres", new net.minecraft.nbt.ListTag());
            } else {
                rift = FeverHalls.build(level, mage, island, extreme.stuff(), List.of(), UnaryOperator.identity(),
                        random, state);
                extreme.rule().built(level, mage, island, rift, state, random);
                scatter(level, island, random, extreme.things().get());
            }
        }
        if (extreme == null || extreme.rule().ringed()) {
            ring(level, mage, rift);
        }
        Dream mine = DREAMS.get(state.getString("rune"));
        boolean verb = mine != null && mine.verb();
        int length = extreme == null ? 0 : extreme.rule().length();
        state.putLong("until", level.getGameTime() + (length > 0 ? length
                : verb ? VERB_SCENE_TICKS + random.nextInt(VERB_SCENE_SPREAD)
                : SCENE_TICKS + random.nextInt(SCENE_SPREAD)));
        return Vec3.atCenterOf(rift);
    }

    /**
     * The places where the body lived the rune (book 1.3), for the rooms of the memories. With no memory yet (an
     * experience lived before memories were kept), the kern's own ground is remembered instead.
     */
    private static List<BodyMemories.Piece> memories(ServerLevel level, UUID mage, BlockPos island, CompoundTag state,
                                                     RandomSource random) {
        List<String> experiences = Understanding.lesson(state.getString("rune"))
                .map(Understanding.RuneLesson::experiences).orElse(List.of());
        List<BodyMemories.Piece> pieces = new ArrayList<>(BodyMemories.of(level.getServer(), mage, experiences));
        while (pieces.size() < 3) {
            int x = random.nextInt(2 * KernRealm.RADIUS - 10) - KernRealm.RADIUS + 5;
            int z = random.nextInt(2 * KernRealm.RADIUS - 10) - KernRealm.RADIUS + 5;
            pieces.add(BodyMemories.Piece.of(level, island.offset(x, 1, z)));
        }
        return pieces;
    }

    // ------------------------------------------------------------------ the air of each extreme

    /** igni, the heat: the air sets the dreamer alight. */
    private static void burn(ServerPlayer dreamer, CompoundTag state) {
        if (!dreamer.isInWaterRainOrBubble()) {
            dreamer.setRemainingFireTicks(Math.max(dreamer.getRemainingFireTicks(), 60));
            dreamer.hurt(dreamer.damageSources().onFire(), 1.0F);
        }
    }

    /** igni, the cold: the air freezes the dreamer. */
    private static void freeze(ServerPlayer dreamer, CompoundTag state) {
        dreamer.setTicksFrozen(Math.min(dreamer.getTicksRequiredToFreeze() + 20, dreamer.getTicksFrozen() + 40));
        if (dreamer.isFullyFrozen()) {
            dreamer.hurt(dreamer.damageSources().freeze(), 1.0F);
        }
    }

    /** aqua, the drought: the air dries the body out. */
    private static void dryOut(ServerPlayer dreamer, CompoundTag state) {
        if (!dreamer.isInWaterRainOrBubble()) {
            dreamer.hurt(dreamer.damageSources().dryOut(), 1.0F);
        }
    }

    /** aura, the gale: the pressure in the ears, and the halls' sounds, heard twice. */
    private static void pressure(ServerPlayer dreamer, CompoundTag state) {
        dreamer.hurt(dreamer.damageSources().generic(), 1.0F);
        if (state.getInt("seconds") % 4 == 0) {
            float pitch = 0.6F + dreamer.getRandom().nextFloat() * 0.5F;
            dreamer.playNotifySound(SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.AMBIENT, 1.0F, pitch);
            SpellTicks.schedule(dreamer.server, 12, () -> dreamer.playNotifySound(SoundEvents.WOODEN_DOOR_CLOSE,
                    SoundSource.AMBIENT, 0.4F, pitch));
        }
    }

    /** firmo, the weight: a body too heavy to fly, or to hurry. */
    private static void heavy(ServerPlayer dreamer, CompoundTag state) {
        if (dreamer.getAbilities().mayfly || dreamer.getAbilities().flying) {
            dreamer.getAbilities().mayfly = false;
            dreamer.getAbilities().flying = false;
            dreamer.onUpdateAbilities();
        }
        dreamer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, false));
    }

    /** firmo, the burial: every few seconds the walls of every room come in by a row. */
    private static void closeIn(ServerPlayer dreamer, CompoundTag state) {
        int seconds = state.getInt("seconds");
        int rows = seconds / CLOSING_SECONDS;
        if (seconds % CLOSING_SECONDS != 0 || rows < 1 || rows > CLOSING_ROWS) {
            return;
        }
        CompoundTag dream = Dreams.dream(dreamer);
        BlockPos island = new BlockPos(dream.getInt("ox"), KernRealm.Y, dream.getInt("oz"));
        FeverHalls.closeIn(dreamer.serverLevel(), dreamer.getUUID(), island, FeverHalls.BURIAL, rows);
        dreamer.playNotifySound(SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 1.0F, 0.5F);
    }

    // ------------------------------------------------------------------ the memories, turned

    /**
     * What a memory holds, held still in the dream: what would flow off the kern (water, lava, a block full of water) is
     * set fast, and what would fall (sand, gravel) is stone.
     */
    static BlockState still(BlockState state) {
        if (state.is(Blocks.WATER) || !state.getFluidState().isEmpty() && state.getBlock()
                instanceof net.minecraft.world.level.block.LiquidBlock) {
            return Blocks.PACKED_ICE.defaultBlockState();
        }
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            state = state.setValue(BlockStateProperties.WATERLOGGED, false);
        }
        if (state.getBlock() instanceof net.minecraft.world.level.block.FallingBlock) {
            return state.is(Blocks.SAND) ? Blocks.SANDSTONE.defaultBlockState()
                    : state.is(Blocks.RED_SAND) ? Blocks.RED_SANDSTONE.defaultBlockState()
                    : state.is(Blocks.GRAVEL) ? Blocks.COBBLESTONE.defaultBlockState()
                    : Blocks.STONE.defaultBlockState();
        }
        return state.getFluidState().isEmpty() ? state : Blocks.AIR.defaultBlockState();
    }

    /** igni: the hot is cold and the cold is hot. */
    private static BlockState hotForCold(BlockState state) {
        if (state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.ICE)
                || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)) {
            return Blocks.MAGMA_BLOCK.defaultBlockState();
        }
        if (state.is(Blocks.SNOW)) {
            return Blocks.AIR.defaultBlockState();
        }
        if (state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK)) {
            return Blocks.BLUE_ICE.defaultBlockState();
        }
        if (state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES)) {
            return Blocks.SNOW_BLOCK.defaultBlockState();
        }
        return still(state);
    }

    /**
     * aqua: water is sand, and sand is water held still, as glass the colour of water: water let loose in a room would
     * wash away what stands in it.
     */
    private static BlockState wetForDry(BlockState state) {
        if (state.is(Blocks.WATER)) {
            return Blocks.SANDSTONE.defaultBlockState();
        }
        if (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.SANDSTONE)
                || state.is(Blocks.RED_SANDSTONE)) {
            return Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        }
        return still(state);
    }

    /** aura: what was full is glass, seen through, the ghost of what it was. */
    private static BlockState fullForEmpty(BlockState state) {
        BlockState held = still(state);
        if (held.isAir()) {
            return held;
        }
        return held.getFluidState().isEmpty() && held.isSolid() ? Blocks.GLASS.defaultBlockState()
                : Blocks.AIR.defaultBlockState();
    }

    /** vis: everything crystal, the four sources in one. */
    private static BlockState crystal(BlockState state) {
        BlockState held = still(state);
        return held.isAir() || !held.isSolid() ? held
                : (System.identityHashCode(held) & 1) == 0 ? Blocks.AMETHYST_BLOCK.defaultBlockState()
                : Blocks.CALCITE.defaultBlockState();
    }

    /** firmo: the stone is ore, the minerals in it laid bare. */
    private static BlockState stoneForOre(BlockState state) {
        BlockState held = still(state);
        if (held.is(BlockTags.BASE_STONE_OVERWORLD) || held.is(Blocks.COBBLESTONE) || held.is(Blocks.DIRT)
                || held.is(Blocks.GRASS_BLOCK)) {
            Block[] ores = {Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.COPPER_ORE, Blocks.GOLD_ORE, Blocks.REDSTONE_ORE,
                    Blocks.LAPIS_ORE};
            return ores[Math.floorMod(System.identityHashCode(held) + (int) (System.nanoTime() >> 8), ores.length)]
                    .defaultBlockState();
        }
        return held;
    }

    // ------------------------------------------------------------------ the rift, and the dream's things

    /** The rift: a ring of crying obsidian round where it is, standing up. */
    private static void ring(ServerLevel level, UUID mage, BlockPos at) {
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI / 8.0D;
            BlockPos stone = at.offset(Mth.floor(Math.cos(angle) * 2.5D), Mth.floor(Math.sin(angle) * 2.5D), 0);
            if (!stone.equals(at)) {
                KernDressing.put(level, mage, stone, Blocks.CRYING_OBSIDIAN.defaultBlockState());
            }
        }
    }

    private static List<ItemStack> items(Item... kinds) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Item kind : kinds) {
            stacks.add(new ItemStack(kind));
        }
        return stacks;
    }

    private static List<ItemStack> waterBottles() {
        return List.of(PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER));
    }

    /** What the dreamer needs, lying about the rooms: in a dream it is easy to find. */
    private static void scatter(ServerLevel level, BlockPos island, RandomSource random, List<ItemStack> kinds) {
        for (int n = 0; n < 8 && !kinds.isEmpty(); n++) {
            Vec3 at = Vec3.atCenterOf(FeverHalls.middle(island, random.nextInt(FeverHalls.ROOMS),
                    random.nextInt(FeverHalls.ROOMS))).add(random.nextInt(5) - 2, 0.0D, random.nextInt(5) - 2);
            ItemEntity thing = new ItemEntity(level, at.x, at.y, at.z, dreamt(kinds.get(n % kinds.size()).copy()));
            thing.getPersistentData().putBoolean(DREAMT, true);
            thing.setUnlimitedLifetime();
            level.addFreshEntity(thing);
        }
    }

    /** The dream's things, gone from the kern: what lay about, and what the dreamer dropped of it. */
    private static void clearThings(ServerLevel level, BlockPos origin) {
        AABB kern = new AABB(origin.offset(-80, KernRealm.Y - 40, -80), origin.offset(80, KernRealm.Y + 60, 80));
        level.getEntitiesOfClass(ItemEntity.class, kern, thing -> thing.getPersistentData().getBoolean(DREAMT)
                || thing.getItem().hasTag() && thing.getItem().getTag().getBoolean(DREAMT)).forEach(ItemEntity::discard);
        level.getEntities((net.minecraft.world.entity.Entity) null, kern,
                thing -> !(thing instanceof ServerPlayer) && thing.getPersistentData().getBoolean(DREAMT))
                .forEach(net.minecraft.world.entity.Entity::discard);
    }

    /** A thing of the dream: wherever it is dropped, it goes with the dream. */
    private static ItemStack dreamt(ItemStack stack) {
        stack.getOrCreateTag().putBoolean(DREAMT, true);
        return stack;
    }

    // ------------------------------------------------------------------ in hand

    private static boolean holds(ServerPlayer player, Predicate<ItemStack> kind) {
        return kind.test(player.getMainHandItem()) || kind.test(player.getOffhandItem());
    }

    private static boolean cold(ItemStack stack) {
        return stack.is(Items.ICE) || stack.is(Items.PACKED_ICE) || stack.is(Items.BLUE_ICE)
                || stack.is(Items.SNOW_BLOCK) || stack.is(Items.SNOWBALL) || stack.is(Items.POWDER_SNOW_BUCKET);
    }

    private static boolean warm(ItemStack stack) {
        return stack.is(Items.TORCH) || stack.is(Items.BLAZE_ROD) || stack.is(Items.MAGMA_BLOCK)
                || stack.is(Items.LAVA_BUCKET) || stack.is(Items.FIRE_CHARGE) || stack.is(Items.CAMPFIRE);
    }
}
