package com.elderlexicon.mod.nahva;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spelling.data.SpellingRepertoire;
import com.elderlexicon.mod.spelling.data.SpellingRepertoireHelper;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import com.elderlexicon.mod.vita.VitaImbalanceTier;
import com.elderlexicon.mod.vita.VitaSystem;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Understanding a rune (docs/aprendizado-runas-design.md): knowing what {@code igni} means teaches nothing; the mage
 * must live, in the body, what it is: be cold and freezing, warm and burning. Each second the body is looked at, and
 * every experience of a rune not yet learned that it is living is kept. When all of a rune's are lived, the rune is
 * understood, and only its dream in Nahvá still keeps it from the mage. The experiences each rune asks are data
 * ({@value #RESOURCE}); what each experience is, is here.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class Understanding {

    public static final String RESOURCE = "/data/elderlexicon/lexicon/understanding.json";

    /**
     * A rune to be understood: the experiences it asks, and the runes that must be learned before it can be understood
     * at all (vis, the balance of the four sources, asks the four).
     */
    public record RuneLesson(String rune, List<String> experiences, List<String> requires) {
    }

    /**
     * What each experience is, as the body lives it: the extremes of each source in the body (the Vita), and what the
     * world does to it. What happens in a moment (a long fall, a blast, being buried) is marked when it happens
     * ({@link #lived}) and read on the next look.
     */
    private static final Map<String, Predicate<ServerPlayer>> EXPERIENCES = Map.ofEntries(
            Map.entry("igni_cold", player -> VitaSystem.getIgniTier(player) == VitaImbalanceTier.SLIGHTLY_LOW),
            Map.entry("igni_hypothermia", player -> VitaSystem.getIgniTier(player) == VitaImbalanceTier.SEVERELY_LOW),
            Map.entry("igni_warm", player -> VitaSystem.getIgniTier(player) == VitaImbalanceTier.SLIGHTLY_HIGH),
            Map.entry("igni_boiling", player -> VitaSystem.getIgniTier(player) == VitaImbalanceTier.SEVERELY_HIGH),
            Map.entry("burning", ServerPlayer::isOnFire),
            Map.entry("freezing", ServerPlayer::isFullyFrozen),
            // aqua: flow, dissolution, humidity.
            Map.entry("aqua_thirst", player -> VitaSystem.getAquaTier(player) == VitaImbalanceTier.SLIGHTLY_LOW),
            Map.entry("aqua_parched", player -> VitaSystem.getAquaTier(player) == VitaImbalanceTier.SEVERELY_LOW),
            Map.entry("aqua_full", player -> VitaSystem.getAquaTier(player) == VitaImbalanceTier.SLIGHTLY_HIGH),
            Map.entry("aqua_overflowing", player -> VitaSystem.getAquaTier(player) == VitaImbalanceTier.SEVERELY_HIGH),
            Map.entry("drowning", player -> player.isUnderWater() && player.getAirSupply() <= 0),
            Map.entry("swept", Understanding::swept),
            // aura: pressure, breath, sound.
            Map.entry("aura_low", player -> VitaSystem.getAuraTier(player) == VitaImbalanceTier.SLIGHTLY_LOW),
            Map.entry("aura_critical", player -> VitaSystem.getAuraTier(player) == VitaImbalanceTier.SEVERELY_LOW),
            Map.entry("aura_high", player -> VitaSystem.getAuraTier(player) == VitaImbalanceTier.SLIGHTLY_HIGH),
            Map.entry("aura_overloaded", player -> VitaSystem.getAuraTier(player) == VitaImbalanceTier.SEVERELY_HIGH),
            Map.entry("long_fall", player -> justLived(player, "long_fall")),
            Map.entry("blast", player -> justLived(player, "blast")),
            // firmo: mass, minerals, solidity.
            Map.entry("firmo_low", player -> VitaSystem.getFirmoTier(player) == VitaImbalanceTier.SLIGHTLY_LOW),
            Map.entry("firmo_deficient", player -> VitaSystem.getFirmoTier(player) == VitaImbalanceTier.SEVERELY_LOW),
            Map.entry("firmo_high", player -> VitaSystem.getFirmoTier(player) == VitaImbalanceTier.SLIGHTLY_HIGH),
            Map.entry("firmo_rigid", player -> VitaSystem.getFirmoTier(player) == VitaImbalanceTier.SEVERELY_HIGH),
            Map.entry("buried", player -> justLived(player, "buried")),
            Map.entry("depths", player -> player.level().dimension() == net.minecraft.world.level.Level.OVERWORLD
                    && player.getY() < 0.0D),
            // The functions, the verbs: what the body does that is each of them (docs/aprendizado-runas-design.md,
            // 4c). Most happen in a moment, and are marked when they do (below).
            Map.entry("shoot", player -> justLived(player, "shoot")),
            Map.entry("throw", player -> justLived(player, "throw")),
            Map.entry("reel", player -> justLived(player, "reel")),
            Map.entry("knocked", player -> justLived(player, "knocked")),
            Map.entry("drop", player -> justLived(player, "drop")),
            Map.entry("pick_up", player -> justLived(player, "pick_up")),
            Map.entry("pearl", player -> justLived(player, "pearl")),
            Map.entry("lure", player -> justLived(player, "lure")),
            Map.entry("shield", player -> justLived(player, "shield")),
            Map.entry("shove", player -> justLived(player, "shove")),
            Map.entry("smelt", player -> justLived(player, "smelt")),
            Map.entry("brew", player -> justLived(player, "brew")),
            Map.entry("eat", player -> justLived(player, "eat")),
            Map.entry("tame", player -> justLived(player, "tame")),
            Map.entry("rename", player -> justLived(player, "rename")),
            Map.entry("name_tag", player -> justLived(player, "name_tag")),
            Map.entry("read", player -> justLived(player, "read")),
            Map.entry("brush", player -> justLived(player, "brush")),
            Map.entry("surrounded", player -> player.level().getEntitiesOfClass(
                    net.minecraft.world.entity.monster.Monster.class, player.getBoundingBox().inflate(6.0D),
                    net.minecraft.world.entity.LivingEntity::isAlive).size() >= 4),
            Map.entry("leash", player -> !player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                    player.getBoundingBox().inflate(10.0D), mob -> mob.getLeashHolder() == player).isEmpty()),
            Map.entry("ride", player -> player.getVehicle() instanceof net.minecraft.world.entity.LivingEntity),
            Map.entry("sign_book", player -> signed(player.getMainHandItem(), player)
                    || signed(player.getOffhandItem(), player)),
            Map.entry("spyglass", ServerPlayer::isScoping),
            // vis: the mana itself, emptied and brimming, and the four sources in balance in the body.
            Map.entry("vis_empty", player -> com.elderlexicon.mod.spell.Casters.vis(player) < 1.0D),
            Map.entry("vis_full", player -> player.experienceLevel >= 30),
            Map.entry("vis_balance", player -> VitaSystem.getIgniTier(player) == VitaImbalanceTier.BALANCED
                    && VitaSystem.getAquaTier(player) == VitaImbalanceTier.BALANCED
                    && VitaSystem.getAuraTier(player) == VitaImbalanceTier.BALANCED
                    && VitaSystem.getFirmoTier(player) == VitaImbalanceTier.BALANCED));

    /** How long something that happened in a moment is still being lived, for the look each second to see it. */
    private static final long MOMENT = 40L;
    private static final String LIVED = ElderLexicon.MODID + "_lived_";

    /** A fall of this many blocks or more, lived through, is the long fall. */
    private static final float LONG_FALL = 20.0F;

    private static volatile Map<String, RuneLesson> lessons;

    private Understanding() {
    }

    /** Every rune that can be understood, by id. */
    public static Map<String, RuneLesson> lessons() {
        Map<String, RuneLesson> found = lessons;
        if (found == null) {
            synchronized (Understanding.class) {
                found = lessons;
                if (found == null) {
                    found = read();
                    lessons = found;
                }
            }
        }
        return found;
    }

    /** Carried along by flowing water: in water that is not a source, being moved by it. */
    private static boolean swept(ServerPlayer player) {
        net.minecraft.world.level.material.FluidState water = player.level().getFluidState(player.blockPosition());
        return player.isInWater() && water.is(net.minecraft.tags.FluidTags.WATER) && !water.isSource()
                && player.getDeltaMovement().horizontalDistanceSqr() > 0.01D;
    }

    private static boolean justLived(ServerPlayer player, String experience) {
        return player.level().getGameTime() - player.getPersistentData().getLong(LIVED + experience) < MOMENT;
    }

    /** {@code experience} happened to {@code player} just now (a fall, a blast). */
    private static void lived(ServerPlayer player, String experience) {
        player.getPersistentData().putLong(LIVED + experience, player.level().getGameTime());
    }

    /** A book the mage wrote and signed, in their hand. */
    private static boolean signed(net.minecraft.world.item.ItemStack stack, ServerPlayer player) {
        return stack.is(net.minecraft.world.item.Items.WRITTEN_BOOK) && stack.hasTag()
                && player.getGameProfile().getName().equals(stack.getTag().getString("author"));
    }

    // ------------------------------------------------------------------ the verbs, as they happen

    @SubscribeEvent
    public static void onShoot(net.minecraftforge.event.entity.player.ArrowLooseEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.hasAmmo()) {
            lived(player, "shoot");
        }
    }

    @SubscribeEvent
    public static void onThrow(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile thrown
                && !(thrown instanceof net.minecraft.world.entity.projectile.AbstractArrow
                && !(thrown instanceof net.minecraft.world.entity.projectile.ThrownTrident))
                && !(thrown instanceof net.minecraft.world.entity.projectile.FishingHook)
                && thrown.getOwner() instanceof ServerPlayer player) {
            lived(player, "throw");
        }
    }

    @SubscribeEvent
    public static void onReel(net.minecraftforge.event.entity.player.ItemFishedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            lived(player, "reel");
        }
    }

    @SubscribeEvent
    public static void onKnocked(net.minecraftforge.event.entity.living.LivingKnockBackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getStrength() >= 0.6F) {
            lived(player, "knocked");
        }
    }

    @SubscribeEvent
    public static void onDrop(net.minecraftforge.event.entity.item.ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            lived(player, "drop");
        }
    }

    @SubscribeEvent
    public static void onPickUp(net.minecraftforge.event.entity.player.PlayerEvent.ItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            lived(player, "pick_up");
        }
    }

    @SubscribeEvent
    public static void onPearl(net.minecraftforge.event.entity.EntityTeleportEvent.EnderPearl event) {
        lived(event.getPlayer(), "pearl");
    }

    @SubscribeEvent
    public static void onInteractEntity(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        net.minecraft.world.item.ItemStack held = event.getItemStack();
        if (event.getTarget() instanceof net.minecraft.world.entity.animal.Animal animal && animal.isFood(held)) {
            lived(player, "lure");
        }
        if (held.is(net.minecraft.world.item.Items.NAME_TAG) && held.hasCustomHoverName()) {
            lived(player, "name_tag");
        }
    }

    @SubscribeEvent
    public static void onShield(net.minecraftforge.event.entity.living.ShieldBlockEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            lived(player, "shield");
        }
    }

    @SubscribeEvent
    public static void onShove(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.isSprinting()) {
            lived(player, "shove");
        }
    }

    @SubscribeEvent
    public static void onSmelt(net.minecraftforge.event.entity.player.PlayerEvent.ItemSmeltedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            lived(player, "smelt");
        }
    }

    @SubscribeEvent
    public static void onBrew(net.minecraftforge.event.brewing.PlayerBrewedPotionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            lived(player, "brew");
        }
    }

    @SubscribeEvent
    public static void onEat(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getItem().isEdible()) {
            lived(player, "eat");
        }
    }

    @SubscribeEvent
    public static void onTame(net.minecraftforge.event.entity.living.AnimalTameEvent event) {
        if (event.getTamer() instanceof ServerPlayer player) {
            lived(player, "tame");
        }
    }

    @SubscribeEvent
    public static void onRename(net.minecraftforge.event.entity.player.AnvilRepairEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getOutput().hasCustomHoverName()) {
            lived(player, "rename");
        }
    }

    @SubscribeEvent
    public static void onUseBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        net.minecraft.world.level.block.state.BlockState block = player.level().getBlockState(event.getPos());
        if (block.is(net.minecraft.world.level.block.Blocks.LECTERN)
                && block.getValue(net.minecraft.world.level.block.LecternBlock.HAS_BOOK)) {
            lived(player, "read");
        }
        if (event.getItemStack().is(net.minecraft.world.item.Items.BRUSH)
                && (block.is(net.minecraft.world.level.block.Blocks.SUSPICIOUS_SAND)
                || block.is(net.minecraft.world.level.block.Blocks.SUSPICIOUS_GRAVEL))) {
            lived(player, "brush");
        }
    }

    @SubscribeEvent
    public static void onUseItem(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getItemStack().is(net.minecraft.world.item.Items.WRITTEN_BOOK)) {
            lived(player, "read");
        }
    }

    @SubscribeEvent
    public static void onFall(net.minecraftforge.event.entity.living.LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getDistance() >= LONG_FALL) {
            lived(player, "long_fall");
        }
    }

    @SubscribeEvent
    public static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
            lived(player, "blast");
        } else if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)) {
            lived(player, "buried");
        }
    }

    public static Optional<RuneLesson> lesson(String rune) {
        return Optional.ofNullable(lessons().get(rune));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 20 != 0 || player.isCreative() || player.isSpectator()
                // The dead live nothing; and a body a moment dead has already let go of what it knew.
                || !player.isAlive() || Nahva.isNahva(player.level())) {
            return;
        }
        SpellingRepertoire repertoire = SpellingRepertoireHelper.get(player);
        // Runes are understood only by a mage whose trance is woken.
        if (!repertoire.hasTrance()) {
            return;
        }
        boolean changed = false;
        for (RuneLesson lesson : lessons().values()) {
            if (repertoire.isUnlocked(lesson.rune())
                    || !lesson.requires().stream().allMatch(repertoire::isUnlocked)) {
                continue;
            }
            // The kern keeps the place where each experience is lived (book 1.3), for the dream: the first time, and
            // again for one lived before places were kept.
            for (String experience : lesson.experiences()) {
                Predicate<ServerPlayer> living = EXPERIENCES.get(experience);
                if (living != null && !BodyMemories.has(player, experience) && living.test(player)) {
                    BodyMemories.remember(player, experience);
                }
            }
            if (repertoire.isUnderstood(lesson.rune())) {
                continue;
            }
            for (String experience : lesson.experiences()) {
                Predicate<ServerPlayer> living = EXPERIENCES.get(experience);
                if (living != null && living.test(player) && repertoire.experience(lesson.rune(), experience)) {
                    changed = true;
                    player.displayClientMessage(Component.translatable("experience.elderlexicon." + experience)
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
                }
            }
            if (repertoire.experienced(lesson.rune()).containsAll(lesson.experiences())
                    && repertoire.understand(lesson.rune())) {
                changed = true;
                player.sendSystemMessage(Component.translatable("understood.elderlexicon." + lesson.rune())
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
        if (changed) {
            SpellingNetwork.syncToPlayer(player, repertoire);
        }
    }

    private static Map<String, RuneLesson> read() {
        InputStream stream = Understanding.class.getResourceAsStream(RESOURCE);
        if (stream == null) {
            return Map.of();
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject runes = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("runes");
            Map<String, RuneLesson> read = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : runes.entrySet()) {
                JsonObject lesson = entry.getValue().getAsJsonObject();
                List<String> experiences = new ArrayList<>();
                lesson.getAsJsonArray("experiences").forEach(experience -> experiences.add(experience.getAsString()));
                List<String> requires = new ArrayList<>();
                if (lesson.has("requires")) {
                    lesson.getAsJsonArray("requires").forEach(rune -> requires.add(rune.getAsString()));
                }
                read.put(entry.getKey(), new RuneLesson(entry.getKey(), Collections.unmodifiableList(experiences),
                        Collections.unmodifiableList(requires)));
            }
            return Collections.unmodifiableMap(read);
        } catch (java.io.IOException | RuntimeException unreadable) {
            return Map.of();
        }
    }
}
