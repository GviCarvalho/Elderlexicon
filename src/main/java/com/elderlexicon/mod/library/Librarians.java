package com.elderlexicon.mod.library;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.mind.Intent;
import com.elderlexicon.mod.mind.Mind;
import com.elderlexicon.mod.mind.Phrase;
import com.elderlexicon.mod.mind.PhraseCatalog;
import com.elderlexicon.mod.spell.SpellCastingService;
import com.elderlexicon.mod.spelling.network.RecitationPacket;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The librarian of the language (docs/mago-design.md, 2 and 5d).
 * <ul>
 *   <li><b>It sells guides:</b> of a rune (what it is, how it is written, how it is lived) and of a family (the
 *       grammar), a book and some emeralds each; the deeper its trade, the deeper the guides.</li>
 *   <li><b>It defends itself with the language:</b> a monster near, or a blow, and it stands still for the two seconds
 *       of a trance and reads the veil: it is lost to whoever hunted it. A blow during the trance breaks it.</li>
 * </ul>
 * A villager has no goals but a brain of its own, so its trance is simpler than a mage's: it stands where it is.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class Librarians {

    /** The trance: two seconds, as everyone's (book, cap. 5). */
    private static final int TRANCE_TICKS = 40;
    /** How near a monster must be for it to read. */
    private static final double THREAT_REACH = 8.0D;
    /** How long a blow makes it read for, after it. */
    private static final int HURT_RECENTLY_TICKS = 40;
    /** How long it waits before another trance: after reading, after a broken one. */
    private static final int READ_REST_TICKS = 600;
    private static final int BROKEN_REST_TICKS = 100;

    /** A librarian in a trance: how long it has been in it, and what it reads. */
    private record Trance(int ticks, List<String> words) {
    }

    private static final Map<UUID, Trance> TRANCES = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> RESTING = new ConcurrentHashMap<>();
    private static final SpellCastingService SPIRIT = new SpellCastingService();

    private Librarians() {
    }

    // ------------------------------------------------------------------ the guides it sells

    @SubscribeEvent
    public static void onTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.LIBRARIAN) {
            return;
        }
        var trades = event.getTrades();
        trades.get(1).add(runeGuide(RuneGuides.Family.FONTS, 5));
        trades.get(1).add(familyGuide(RuneGuides.Family.FONTS, 4));
        trades.get(2).add(runeGuide(RuneGuides.Family.FUNCTIONS, 6));
        trades.get(2).add(familyGuide(RuneGuides.Family.FUNCTIONS, 5));
        trades.get(3).add(runeGuide(RuneGuides.Family.FILTERS, 7));
        trades.get(3).add(familyGuide(RuneGuides.Family.FILTERS, 6));
        trades.get(4).add(runeGuide(RuneGuides.Family.FACTS, 7));
        trades.get(4).add(familyGuide(RuneGuides.Family.FACTS, 6));
        trades.get(5).add(runeGuide(null, 8));
    }

    /** A rune's guide of {@code family} (any family, when null), drawn when the offer is made. */
    private static VillagerTrades.ItemListing runeGuide(RuneGuides.Family family, int price) {
        return (trader, random) -> {
            List<String> runes = family == null
                    ? java.util.Arrays.stream(RuneGuides.Family.values()).flatMap(each -> RuneGuides.runesOf(each)
                    .stream()).toList()
                    : RuneGuides.runesOf(family);
            if (runes.isEmpty()) {
                return null;
            }
            String rune = runes.get(random.nextInt(runes.size()));
            return RuneGuides.runeGuide(rune).map(guide -> offer(price, guide, random)).orElse(null);
        };
    }

    private static VillagerTrades.ItemListing familyGuide(RuneGuides.Family family, int price) {
        return (trader, random) -> offer(price, RuneGuides.familyGuide(family), random);
    }

    private static MerchantOffer offer(int price, ItemStack guide, RandomSource random) {
        return new MerchantOffer(new ItemStack(Items.EMERALD, price + random.nextInt(3)), new ItemStack(Items.BOOK),
                guide, 4, 10, 0.05F);
    }

    // ------------------------------------------------------------------ its trance

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.level().isClientSide()
                || villager.getVillagerData().getProfession() != VillagerProfession.LIBRARIAN || villager.isBaby()) {
            return;
        }
        Trance trance = TRANCES.get(villager.getUUID());
        if (trance != null) {
            hold(villager, trance);
            return;
        }
        if (villager.tickCount % 10 != 0 || !villager.isAlive()
                || RESTING.getOrDefault(villager.getUUID(), 0L) > villager.level().getGameTime()) {
            return;
        }
        if (threatened(villager)) {
            veil().ifPresent(words -> begin(villager, words));
        }
    }

    /** A blow during the trance breaks it: nothing is read. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity hurt = event.getEntity();
        if (hurt.level().isClientSide() || event.getSource().getEntity() == null
                || TRANCES.remove(hurt.getUUID()) == null) {
            return;
        }
        recite(hurt, List.of(), true);
        hurt.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        RESTING.put(hurt.getUUID(), hurt.level().getGameTime() + BROKEN_REST_TICKS);
    }

    /** Whether a monster is near, or something just struck it. */
    private static boolean threatened(Villager villager) {
        Optional<LivingEntity> hostile = villager.getBrain().getMemory(MemoryModuleType.NEAREST_HOSTILE);
        if (hostile.isPresent() && hostile.get().distanceTo(villager) <= THREAT_REACH) {
            return true;
        }
        return villager.getLastHurtByMob() != null
                && villager.tickCount - villager.getLastHurtByMobTimestamp() < HURT_RECENTLY_TICKS;
    }

    private static void begin(Villager villager, List<String> words) {
        TRANCES.put(villager.getUUID(), new Trance(0, words));
        // It stands where it is for the two seconds of the trance.
        villager.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, TRANCE_TICKS + 2, 9, false, false));
        villager.getNavigation().stop();
        villager.playSound(SoundEvents.VILLAGER_WORK_LIBRARIAN, 1.0F, 1.0F);
        recite(villager, List.of(), false);
    }

    private static void hold(Villager villager, Trance trance) {
        int ticks = trance.ticks() + 1;
        List<String> words = trance.words();
        if (ticks < TRANCE_TICKS) {
            TRANCES.put(villager.getUUID(), new Trance(ticks, words));
            if (ticks % 4 == 0) {
                int spoken = Math.min(words.size(), (int) Math.ceil(words.size() * ticks / (double) (TRANCE_TICKS - 5)));
                recite(villager, words.subList(0, spoken), false);
            }
            return;
        }
        TRANCES.remove(villager.getUUID());
        RESTING.put(villager.getUUID(), villager.level().getGameTime() + READ_REST_TICKS);
        recite(villager, words, true);
        villager.playSound(SoundEvents.EVOKER_CAST_SPELL, 1.0F, 1.2F);
        try {
            SPIRIT.cast(villager, words);
        } catch (RuntimeException exception) {
            com.mojang.logging.LogUtils.getLogger().error("A librarian failed reading {}", words, exception);
        }
    }

    private static void recite(LivingEntity reciter, List<String> words, boolean finished) {
        SpellingNetwork.broadcastRecitation(reciter, new RecitationPacket(reciter.getId(), words, finished));
    }

    /** Whether {@code villager} is in a trance now (for a test). */
    public static boolean inTrance(LivingEntity villager) {
        return TRANCES.containsKey(villager.getUUID());
    }

    /**
     * What it reads: the sentence of the catalog that vanishes the longest, cheapest among those (a librarian knows the
     * runes of its trade). Empty until the catalog is made.
     */
    static Optional<List<String>> veil() {
        return PhraseCatalog.ready().flatMap(catalog -> catalog.phrases().stream()
                .filter(phrase -> phrase.acts().stream().anyMatch(act -> Mind.serves(act) == Intent.VEIL))
                .filter(phrase -> phrase.acts().stream().allMatch(act -> Mind.serves(act) == Intent.VEIL))
                .max(Comparator.comparingDouble((Phrase phrase) -> phrase.acts().stream()
                                .mapToDouble(act -> act.seconds().orElse(2.0D)).max().orElse(0.0D))
                        .thenComparing(Comparator.comparingDouble(Phrase::cost).reversed()))
                .map(Phrase::words));
    }
}
