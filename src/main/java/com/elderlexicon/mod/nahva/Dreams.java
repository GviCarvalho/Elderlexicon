package com.elderlexicon.mod.nahva;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.SpellTicks;
import com.elderlexicon.mod.spelling.data.MageAbilities;
import com.elderlexicon.mod.spelling.data.RuneUnlocks;
import com.elderlexicon.mod.spelling.data.SpellingRepertoire;
import com.elderlexicon.mod.spelling.data.SpellingRepertoireHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Going to Nahvá, being there, and coming back (docs/aprendizado-runas-design.md, docs/habilidades-design.md).
 * <ul>
 * <li><b>The vivid dream.</b> A mage who has understood a rune may, on a night asleep, be taken to its realm: there the
 * kern's will is law (book 2.1.2), so they fly and their UMU does not run out, but they bring nothing in their hands.
 * They wake by escaping through the rift, having learned the rune, or by dying, having learned nothing; either way in
 * their bed, with all they had, and the waking is always bad.</li>
 * <li><b>Death.</b> A mage with the trance and without the state of flow who dies wakes as a spirit on their own
 * island in Nahvá, their kern, to rest there forever. From its edge the road leads to the infinite tower (UMU without
 * end, but no flight: the climb is the trial). Through its rift they come back to life where they died, in a sorry
 * state, with the state of flow; through any of its doors of rest they give up and go back to the island. Dying in
 * Nahvá as a spirit, on the island or in the tower, is dying for real.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class Dreams {

    /** The chance, each night asleep, that an understood rune comes as a dream. */
    public static final float DREAM_CHANCE = 0.35F;
    private static final String TAG = ElderLexicon.MODID + "_dream";
    /** What a mage come back from the tower has: three hearts, and food that is low but does not starve them. */
    private static final float REVIVED_HEALTH = 6.0F;
    private static final int REVIVED_FOOD = 6;
    /** How near the rift's middle the dreamer must come to go through it. */
    private static final double RIFT_REACH = 1.6D;

    /** The mages already given their chance this sleep. */
    private static final Set<UUID> ROLLED = new HashSet<>();
    /** The mages whose next death is real: it is let through. */
    private static final Set<UUID> REAL_DEATH = new HashSet<>();

    private Dreams() {
    }

    /** True while {@code player} is in Nahvá, dreaming or climbing the tower. */
    public static boolean isDreaming(ServerPlayer player) {
        return player != null && player.getPersistentData().contains(TAG);
    }

    /** What is kept of the dream: where the mage left from, what they had, and the realm's own state. */
    static CompoundTag dream(ServerPlayer player) {
        return player.getPersistentData().getCompound(TAG);
    }

    // ------------------------------------------------------------------ falling asleep

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (isDreaming(player)) {
            dreaming(player);
            return;
        }
        if (!player.isSleeping()) {
            ROLLED.remove(player.getUUID());
            return;
        }
        if (player.isSleepingLongEnough() && ROLLED.add(player.getUUID())) {
            // A night with 100 XP wakes the trance; no rune is dreamed of before it.
            if (MageAbilities.sleptForTrance(player)) {
                return;
            }
            List<String> waiting = waitingRunes(player);
            if (!waiting.isEmpty() && player.getRandom().nextFloat() < DREAM_CHANCE) {
                enter(player, waiting.get(player.getRandom().nextInt(waiting.size())));
            }
        }
    }

    /** The runes the mage has understood and not yet learned, that have a dream. */
    private static List<String> waitingRunes(ServerPlayer player) {
        SpellingRepertoire repertoire = SpellingRepertoireHelper.get(player);
        List<String> waiting = new ArrayList<>();
        for (String rune : repertoire.understood()) {
            if (!repertoire.isUnlocked(rune) && FeverDream.dreams(rune)) {
                waiting.add(rune);
            }
        }
        return waiting;
    }

    /** Takes the sleeping mage into the fever dream of {@code rune}, on their own kern. */
    public static boolean enter(ServerPlayer player, String rune) {
        Optional<Understanding.RuneLesson> lesson = Understanding.lesson(rune);
        if (lesson.isEmpty()) {
            return false;
        }
        if (player.isSleeping()) {
            player.stopSleepInBed(false, false);
        }
        if (!FeverDream.dreams(rune)) {
            return false;
        }
        if (!go(player, FeverDream.REALM, rune, "dream.elderlexicon.enter." + rune)) {
            return false;
        }
        FeverDream.tell(player, dream(player).getCompound("state"));
        return true;
    }

    /** Takes a mage who has just died to their island, as a spirit. */
    public static boolean enterKern(ServerPlayer player) {
        if (!go(player, Realms.KERN, "", "kern.elderlexicon.enter")) {
            return false;
        }
        Phantur.speak(player, "kern");
        return true;
    }

    /** Takes a mage to the infinite tower: from their island by the road, or (for testing) straight away. */
    public static boolean enterTower(ServerPlayer player) {
        if (isDreaming(player)) {
            if (Realms.KERN.equals(dream(player).getString("realm"))) {
                takeTheRoad(player);
                return true;
            }
            return false;
        }
        if (!go(player, Realms.TOWER, "", "tower.elderlexicon.enter")) {
            return false;
        }
        towerGreets(player);
        return true;
    }

    /** From the island's gate, the road to the tower; for now it is short. What was the island's waits there. */
    static void takeTheRoad(ServerPlayer player) {
        CompoundTag dream = dream(player);
        dream.put("kernInventory", player.getInventory().save(new ListTag()));
        player.getInventory().clearContent();
        Phantur.speak(player, "kern_leave");
        if (travel(player, dream, Realms.TOWER, "tower.elderlexicon.road")) {
            towerGreets(player);
        }
    }

    /** Through a door of rest: the spirit gives up the climb, and goes to its island. */
    static void giveUp(ServerPlayer player) {
        CompoundTag dream = dream(player);
        Loans.repay(player, dream);
        player.getInventory().clearContent();
        player.getInventory().load(dream.getList("kernInventory", Tag.TAG_COMPOUND));
        dream.remove("kernInventory");
        player.inventoryMenu.broadcastChanges();
        Phantur.speak(player, "rest");
        travel(player, dream, Realms.KERN, "kern.elderlexicon.rest");
    }

    private static void towerGreets(ServerPlayer player) {
        Phantur.speak(player, Phantur.climbed(player) == 1 ? "first" : "again");
    }

    /** Takes the mage to {@code realm}: everything they were is kept, to be given back. */
    private static boolean go(ServerPlayer player, String realm, String rune, String message) {
        ServerLevel nahva = Nahva.level(player.server);
        if (nahva == null || isDreaming(player)) {
            return false;
        }
        CompoundTag dream = new CompoundTag();
        dream.putString("rune", rune);
        dream.putString("dimension", player.level().dimension().location().toString());
        dream.putDouble("x", player.getX());
        dream.putDouble("y", player.getY());
        dream.putDouble("z", player.getZ());
        dream.putFloat("yaw", player.getYRot());
        dream.putFloat("pitch", player.getXRot());
        dream.put("inventory", player.getInventory().save(new ListTag()));
        dream.putFloat("health", Math.max(1.0F, player.getHealth()));
        dream.putInt("food", player.getFoodData().getFoodLevel());
        dream.putFloat("saturation", player.getFoodData().getSaturationLevel());
        player.getPersistentData().put(TAG, dream);

        // In the kern the body wants for nothing, and holds nothing.
        player.getInventory().clearContent();
        player.removeAllEffects();
        player.clearFire();
        player.setTicksFrozen(0);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.fallDistance = 0.0F;
        if (!travel(player, dream, realm, message)) {
            player.getPersistentData().remove(TAG);
            restore(player, dream);
            return false;
        }
        return true;
    }

    /** Lays {@code realm} out and takes the one already in Nahvá (or just come) there. */
    private static boolean travel(ServerPlayer player, CompoundTag dream, String realm, String message) {
        ServerLevel nahva = Nahva.level(player.server);
        BlockPos origin = Nahva.origin(player.server, player.getUUID(), realm);
        CompoundTag state = new CompoundTag();
        if (Realms.TOWER.equals(realm)) {
            // What the tower will remember of the mage, if one of its rooms is a memory.
            state.put("memory", MemoryRoom.source(player, dream));
        }
        if (FeverDream.REALM.equals(realm)) {
            // Whose kern it dresses, and with which rune.
            state.putUUID("mage", player.getUUID());
            state.putString("rune", dream.getString("rune"));
        }
        Optional<Realms.Built> built = Realms.build(realm, nahva, origin, state);
        if (built.isEmpty()) {
            return false;
        }
        dream.putString("realm", realm);
        dream.putInt("ox", origin.getX());
        dream.putInt("oz", origin.getZ());
        dream.put("state", state);
        Vec3 rift = built.get().rift();
        dream.putDouble("riftX", rift.x);
        dream.putDouble("riftY", rift.y);
        dream.putDouble("riftZ", rift.z);
        player.fallDistance = 0.0F;
        Vec3 spawn = built.get().spawn();
        player.teleportTo(nahva, spawn.x, spawn.y, spawn.z, Realms.TOWER.equals(realm) ? 180.0F : -90.0F, 0.0F);
        // After the journey: changing dimension sends the abilities of the game mode again.
        GameType mode = player.gameMode.getGameModeForPlayer();
        player.getAbilities().mayfly = Realms.flies(realm) || mode == GameType.CREATIVE || mode == GameType.SPECTATOR;
        player.getAbilities().flying = Realms.flies(realm) && !Realms.KERN.equals(realm)
                || player.getAbilities().flying && player.getAbilities().mayfly;
        player.onUpdateAbilities();
        player.displayClientMessage(Component.translatable(message)
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), true);
        return true;
    }

    // ------------------------------------------------------------------ in Nahvá

    private static void dreaming(ServerPlayer player) {
        if (!Nahva.isNahva(player.level())) {
            // Taken out of Nahvá by something else: it is over, and nothing was won.
            end(player, false);
            return;
        }
        CompoundTag dream = dream(player);
        String realm = dream.getString("realm");
        // The body of the dream flies, whatever the game mode says.
        if (Realms.flies(realm) && !(FeverDream.REALM.equals(realm) && FeverDream.grounded(dream))
                && !player.getAbilities().mayfly) {
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
        }
        if (FeverDream.REALM.equals(realm)) {
            FeverDream.everyTick(player, dream);
        }
        Vec3 rift = new Vec3(dream.getDouble("riftX"), dream.getDouble("riftY"), dream.getDouble("riftZ"));
        if (player.position().add(0.0D, player.getBbHeight() / 2.0D, 0.0D).distanceTo(rift) < RIFT_REACH) {
            end(player, true);
            return;
        }
        if (Realms.TOWER.equals(realm)) {
            TowerRealm.everyTick(player, dream);
        } else if (Realms.KERN.equals(realm)) {
            KernRealm.everyTick(player, dream);
            if (player.tickCount % 20 == 0) {
                Realms.pulse(realm, player, dream);
            }
            return;
        }
        if (!isDreaming(player)) {
            return;
        }
        if (player.tickCount % 10 == 0) {
            player.serverLevel().sendParticles(player, ParticleTypes.REVERSE_PORTAL, true, rift.x, rift.y, rift.z,
                    12, 0.6D, 0.6D, 0.6D, 0.02D);
        }
        if (player.tickCount % 20 == 0) {
            Realms.pulse(realm, player, dream);
        }
    }

    /**
     * Death. In a dream of a rune, it is waking, having learned nothing. As a spirit, on the island or in the tower, it
     * is death for real. Anywhere else, a mage with the trance and without the state of flow does not die: their spirit
     * wakes on its island.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (REAL_DEATH.remove(player.getUUID())) {
            return;
        }
        if (isDreaming(player)) {
            event.setCanceled(true);
            player.setHealth(1.0F);
            if (Realms.spirit(dream(player).getString("realm"))) {
                // A spirit dying is dying for real: done the tick after, out of this death.
                if (Realms.TOWER.equals(dream(player).getString("realm"))) {
                    Phantur.speak(player, "fall");
                }
                SpellTicks.schedule(player.server, 1, () -> end(player, false));
            } else {
                end(player, false);
            }
            return;
        }
        if (!player.isCreative() && !player.isSpectator() && SpellingRepertoireHelper.get(player).hasTrance()
                && !SpellingRepertoireHelper.get(player).hasFlow() && Nahva.level(player.server) != null) {
            event.setCanceled(true);
            player.setHealth(1.0F);
            SpellTicks.schedule(player.server, 1, () -> {
                if (player.isAlive() && !isDreaming(player)) {
                    enterKern(player);
                }
            });
        }
    }

    /**
     * Leaving the game in Nahvá: a dream ends in waking; a spirit stays where it stood, on its island or in the tower,
     * and goes on from there when the mage comes back.
     */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isDreaming(player)
                && !Realms.spirit(dream(player).getString("realm"))) {
            end(player, false);
        }
        ROLLED.remove(event.getEntity().getUUID());
        Phantur.forget(event.getEntity().getUUID());
        MirrorRoom.forget(event.getEntity().getUUID());
    }

    // ------------------------------------------------------------------ coming back

    /** The dream or the climb is over: {@code won} through the rift, or not. */
    public static void end(ServerPlayer player, boolean won) {
        if (!isDreaming(player)) {
            return;
        }
        CompoundTag dream = dream(player);
        Loans.repay(player, dream);
        player.getPersistentData().remove(TAG);
        String realm = dream.getString("realm");
        if (FeverDream.REALM.equals(realm)) {
            FeverDream.over(player, dream);
        }
        restore(player, dream);
        if (!Realms.spirit(realm)) {
            wake(player, dream, won);
        } else if (won && Realms.TOWER.equals(realm)) {
            Phantur.speak(player, "won");
            revive(player, dream);
        } else {
            dieForReal(player, dream);
        }
    }

    /** Gives back what the mage had and where they were, and takes the dream's body away. */
    private static void restore(ServerPlayer player, CompoundTag dream) {
        player.getInventory().clearContent();
        player.getInventory().load(dream.getList("inventory", Tag.TAG_COMPOUND));
        player.inventoryMenu.broadcastChanges();
        player.removeAllEffects();
        player.clearFire();
        player.setTicksFrozen(0);
        GameType mode = player.gameMode.getGameModeForPlayer();
        player.getAbilities().mayfly = mode == GameType.CREATIVE || mode == GameType.SPECTATOR;
        player.getAbilities().flying = mode == GameType.SPECTATOR;
        player.onUpdateAbilities();
        player.fallDistance = 0.0F;
    }

    private static ServerLevel home(ServerPlayer player, CompoundTag dream) {
        ResourceLocation dimension = ResourceLocation.tryParse(dream.getString("dimension"));
        ServerLevel home = dimension == null ? null
                : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        return home == null || Nahva.isNahva(home) ? player.server.overworld() : home;
    }

    /** Waking from a dream of a rune: in bed, as they lay down; {@code escaped}, they have learned it. Always bad. */
    private static void wake(ServerPlayer player, CompoundTag dream, boolean escaped) {
        player.setHealth(Math.max(1.0F, dream.getFloat("health")));
        player.getFoodData().setFoodLevel(dream.getInt("food"));
        player.getFoodData().setSaturation(dream.getFloat("saturation"));
        player.teleportTo(home(player, dream), dream.getDouble("x"), dream.getDouble("y"), dream.getDouble("z"),
                dream.getFloat("yaw"), dream.getFloat("pitch"));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1200, 0));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600, 0));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 1200, 0));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PHANTOM_AMBIENT, SoundSource.PLAYERS,
                0.8F, 0.6F);
        String rune = dream.getString("rune");
        if (escaped && !rune.isEmpty()) {
            RuneUnlocks.unlock(player, rune);
        } else {
            player.displayClientMessage(Component.translatable("dream.elderlexicon.woke_unlearned")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
        }
    }

    /**
     * Back from the tower through its rift: alive again where they died (or the nearest place a body can stand), in a
     * sorry state, for they truly died and came back; with the state of flow.
     */
    private static void revive(ServerPlayer player, CompoundTag dream) {
        ServerLevel home = home(player, dream);
        Vec3 spot = safeSpot(home, new Vec3(dream.getDouble("x"), dream.getDouble("y"), dream.getDouble("z")));
        player.teleportTo(home, spot.x, spot.y, spot.z, dream.getFloat("yaw"), dream.getFloat("pitch"));
        // Cruel, but not past surviving: little life and an empty stomach that does not yet starve, and a body slow to
        // obey for a while; time enough to find food and a roof.
        player.setHealth(REVIVED_HEALTH);
        player.getFoodData().setFoodLevel(REVIVED_FOOD);
        player.getFoodData().setSaturation(0.0F);
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 300, 0));
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1800, 0));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600, 0));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 1800, 0));
        player.level().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 0.5F);
        MageAbilities.grantFlow(player);
        player.sendSystemMessage(Component.translatable("tower.elderlexicon.revived")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
    }

    /**
     * Dead as a spirit, on the island or in the tower: the death that was put off happens, where it happened, and the
     * mage is born again as anyone is. In hardcore, it is the end.
     */
    private static void dieForReal(ServerPlayer player, CompoundTag dream) {
        player.teleportTo(home(player, dream), dream.getDouble("x"), dream.getDouble("y"), dream.getDouble("z"),
                dream.getFloat("yaw"), dream.getFloat("pitch"));
        REAL_DEATH.add(player.getUUID());
        player.kill();
        REAL_DEATH.remove(player.getUUID());
    }

    /** Where a body can stand near {@code at}: out of lava, water and the void, on something solid. */
    private static Vec3 safeSpot(ServerLevel level, Vec3 at) {
        BlockPos start = BlockPos.containing(at);
        if (start.getY() < level.getMinBuildHeight() || start.getY() > level.getMaxBuildHeight()) {
            start = new BlockPos(start.getX(), Math.max(level.getMinBuildHeight() + 1,
                    Math.min(level.getMaxBuildHeight() - 2, level.getSeaLevel())), start.getZ());
        }
        for (int radius = 0; radius <= 8; radius++) {
            for (int dy = 0; dy <= 16; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                            continue;
                        }
                        BlockPos feet = start.offset(dx, dy, dz);
                        if (standable(level, feet)) {
                            return Vec3.atBottomCenterOf(feet);
                        }
                    }
                }
            }
        }
        BlockPos spawn = level.getSharedSpawnPos();
        return Vec3.atBottomCenterOf(level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types
                .MOTION_BLOCKING_NO_LEAVES, spawn));
    }

    private static boolean standable(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
                && level.getFluidState(feet).isEmpty() && level.getFluidState(feet.above()).isEmpty()
                && !level.getBlockState(feet).blocksMotion() && !level.getBlockState(feet.above()).blocksMotion()
                && !level.getBlockState(feet.below()).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK);
    }
}
