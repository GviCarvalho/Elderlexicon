package com.elderlexicon.mod.university;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.mind.Degree;
import com.elderlexicon.mod.mind.Personality;
import com.elderlexicon.mod.spelling.data.MageAbilities;
import com.elderlexicon.mod.wizard.Dwelling;
import com.elderlexicon.mod.wizard.Wizard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import com.elderlexicon.mod.spelling.item.WandItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * When the universities send their Emissary to a player (docs/mago-design.md, 5j), and what it brings.
 * <ul>
 *   <li><b>The invitation.</b> Some days after the trance wakes in a player who is no student, now and then in the
 *       daylight, an Emissary comes walking to them with the Rector's letter and a map to the nearest house. Turned
 *       down, the house tries again after some days.</li>
 *   <li><b>The call back.</b> A student whose period is over, who has been away from every house for a day and more,
 *       is sought out with the house's call: the new examination, or the fee owed.</li>
 * </ul>
 * What it is to whom is kept with the player: when the next visit may come.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class Emissaries {

    private static final TagKey<Structure> UNIVERSITIES = TagKey.create(Registries.STRUCTURE,
            new ResourceLocation(ElderLexicon.MODID, "university"));
    private static final String TAG = ElderLexicon.MODID + "_emissary";
    private static final long DAY = 24000L;
    /** How often a player's chances are weighed, and the chance each time once a visit is due (about once a day). */
    private static final int CHECK = 200;
    private static final float CHANCE = 1.0F / 40.0F;
    /** After the trance wakes, the first visit is due in two to four days. */
    private static final long FIRST_AFTER = 2L * DAY;
    private static final long FIRST_SPREAD = 2L * DAY;
    /** After a visit, the next may come after this long. */
    private static final long AGAIN_AFTER = 5L * DAY;
    /** A student this far from every house is away. */
    private static final double AWAY = 256.0D;
    /** How far from the player it appears, walking to them. */
    private static final int FROM = 24;
    private static final int FROM_SPREAD = 8;
    /** How far a house is looked for, in chunks. */
    private static final int SEARCH = 100;

    private Emissaries() {
    }

    // ------------------------------------------------------------------ when it comes

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % CHECK != 0 || player.level().dimension() != Level.OVERWORLD
                || !MageAbilities.hasTrance(player) || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        CompoundTag kept = player.getPersistentData().getCompound(TAG);
        if (!kept.contains("Next")) {
            kept.putLong("Next", now + FIRST_AFTER + (long) (level.getRandom().nextFloat() * FIRST_SPREAD));
            player.getPersistentData().put(TAG, kept);
            return;
        }
        if (now < kept.getLong("Next") || !level.isDay() || level.getRandom().nextFloat() >= CHANCE) {
            return;
        }
        Optional<Emissary.Message> message = due(player, now);
        if (message.isEmpty()) {
            return;
        }
        if (send(player, message.get()).isPresent()) {
            kept.putLong("Next", now + AGAIN_AFTER);
            player.getPersistentData().put(TAG, kept);
        }
    }

    /** What the house has to tell {@code player} now, if anything. */
    private static Optional<Emissary.Message> due(ServerPlayer player, long now) {
        Optional<Enrollments.Student> student = Enrollments.of(player.serverLevel()).find(player.getUUID())
                .filter(found -> found.rank().isPresent());
        if (student.isEmpty()) {
            return Optional.of(Emissary.Message.INVITATION);
        }
        boolean over = now >= student.get().paidUntil() + DAY;
        Optional<BlockPos> house = nearest(player);
        boolean away = house.isPresent() && horizontal(player.position(), Vec3.atCenterOf(house.get())) > AWAY;
        return over && away ? Optional.of(Emissary.Message.RETURN) : Optional.empty();
    }

    /**
     * An Emissary sent to {@code player} with {@code message}, walking to them from a little way off. Empty when there
     * is no house in this world, or no ground near them for it to come from.
     */
    public static Optional<Wizard> send(ServerPlayer player, Emissary.Message message) {
        ServerLevel level = player.serverLevel();
        Optional<BlockPos> house = nearest(player);
        if (house.isEmpty()) {
            return Optional.empty();
        }
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = FROM + random.nextInt(FROM_SPREAD);
            BlockPos at = Emissary.ground(level, player.position().add(Math.cos(angle) * distance, 0.0D,
                    Math.sin(angle) * distance));
            if (at == null || Math.abs(at.getY() - player.getY()) > 12) {
                continue;
            }
            Wizard emissary = ElderLexicon.WIZARD.get().create(level);
            if (emissary == null) {
                return Optional.empty();
            }
            emissary.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
            emissary.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
            // A master of the language: the house does not send apprentices out alone.
            // It knows the call it takes them to the house with (a place, a mark, vocant), whatever else it knows;
            // and the four sources, the readings it heals itself with on the road.
            java.util.Set<String> known = new java.util.LinkedHashSet<>(Degree.MASTER.draw(Lexicons.get(), random));
            known.addAll(List.of("ego", "ubis", "vocant", "quantum", "igni", "aqua", "aura", "firmo"));
            emissary.setMind(Degree.MASTER, Personality.draw(random), known);
            emissary.dwellIn(Dwelling.EMISSARY);
            emissary.setCustomName(Component.translatable("university.elderlexicon.emissary"));
            emissary.sendOn(new Emissary(emissary, player.getUUID(), message, house.get()));
            level.addFreshEntity(emissary);
            return Optional.of(emissary);
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ the houses

    /** The nearest university to {@code player}, if this world has one. */
    static Optional<BlockPos> nearest(ServerPlayer player) {
        ServerLevel level = player.serverLevel().getServer().overworld();
        return Optional.ofNullable(level.findNearestMapStructure(UNIVERSITIES, player.blockPosition(), SEARCH, false));
    }

    static int distance(ServerPlayer player, BlockPos house) {
        double far = horizontal(player.position(), Vec3.atCenterOf(house));
        return far < 100.0D ? (int) Math.round(far / 10.0D) * 10 : (int) Math.round(far / 100.0D) * 100;
    }

    /** The way to {@code house} from {@code player}, as a compass says it: norte, nordeste, ... */
    static String heading(ServerPlayer player, BlockPos house) {
        double dx = house.getX() + 0.5D - player.getX();
        double dz = house.getZ() + 0.5D - player.getZ();
        // North is -z; the angle runs clockwise from it.
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        String[] ways = {"norte", "nordeste", "leste", "sudeste", "sul", "sudoeste", "oeste", "noroeste"};
        return ways[Mth.positiveModulo((int) Math.round(angle / 45.0D), 8)];
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        return Math.sqrt(Mth.square(a.x - b.x) + Mth.square(a.z - b.z));
    }

    /** What a student away owes the house: the new examination, or the fee set by it. */
    static String owing(ServerPlayer player) {
        long now = player.getServer().overworld().getGameTime();
        Enrollments.Student student = Enrollments.of(player.serverLevel()).find(player.getUUID()).orElse(null);
        if (student != null && student.owes(now)) {
            return student.fee() <= 0 ? "o novo exame já foi feito, e a casa tem algo a lhe pagar."
                    : "a mensalidade do novo período é de " + student.fee() + " esmeraldas.";
        }
        return "é hora do novo exame, que fixa a mensalidade do próximo período.";
    }

    // ------------------------------------------------------------------ the house's gem

    /** The gem the house sets in its Emissaries' wands (lexicon/foci.json): no one else has it. */
    private static final String SEAL = "university_seal";

    /**
     * The house's gem set in {@code emissary}'s wand, charged with {@code umu} of vis: what the journey it is to read
     * costs. It holds it in its hand, as any caster holds its focus; it falls with no one.
     */
    static void seal(Wizard emissary, double umu) {
        ItemStack wand = new ItemStack(ElderLexicon.WAND.get());
        if (!(wand.getItem() instanceof WandItem item)) {
            return;
        }
        com.elderlexicon.mod.magic.lexicon.Foci.Setting gem = com.elderlexicon.mod.magic.lexicon.Foci.settings().get(SEAL);
        if (gem == null) {
            return;
        }
        item.applySetting(wand, gem);
        item.draw(wand, WandItem.VIS, Math.max(0.0D, item.reserve(wand, WandItem.VIS) - umu));
        wand.setHoverName(Component.translatable("item.elderlexicon.university_wand"));
        emissary.setItemSlot(EquipmentSlot.MAINHAND, wand);
        emissary.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    // ------------------------------------------------------------------ what it brings

    /** The Rector's letter and the map to the house, into {@code player}'s hands. */
    static void deliver(ServerPlayer player, Emissary.Message message, BlockPos house) {
        ItemHandlerHelper.giveItemToPlayer(player, letter(player, message, house));
        ItemHandlerHelper.giveItemToPlayer(player, map(player.serverLevel(), house));
    }

    /** A map to the house, as a cartographer's explorer map: the land around it drawn, the house marked. */
    static ItemStack map(ServerLevel level, BlockPos house) {
        ServerLevel overworld = level.getServer().overworld();
        ItemStack map = MapItem.create(overworld, house.getX(), house.getZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(overworld, map);
        MapItemSavedData.addTargetDecoration(map, house, "+", MapDecoration.Type.TARGET_X);
        map.setHoverName(Component.translatable("item.elderlexicon.university_map"));
        return map;
    }

    /** The Rector's letter: a written book that says what the house is, what it asks, and how to come. */
    static ItemStack letter(ServerPlayer player, Emissary.Message message, BlockPos house) {
        String name = player.getGameProfile().getName();
        String where = "a uns " + distance(player, house) + " blocos de onde o encontramos, ao "
                + heading(player, house);
        String[] pages = message == Emissary.Message.INVITATION ? new String[]{
                "A " + name + ",\n\nO conselho da Universidade soube que o transe despertou em você. Poucos o têm; "
                        + "menos ainda sabem o que fazer com ele.\n\nA casa o convida a estudar a Língua Antiga.",
                "Na Universidade se aprende o que cada runa é, como vivê-la para entendê-la, e como as frases se "
                        + "escrevem: as fontes, as funções, os filtros e os fatos.\n\nOs mestres respondem a quem "
                        + "pergunta. O Arquivo guarda os guias.",
                "Quem chega pede admissão ao Rector. O conselho o examina, e você paga pelo que sabe, em esmeraldas, "
                        + "a cada sete dias: quem sabe pouco paga caro; quem sabe muito paga pouco, ou nada.\n\n"
                        + "Traga esmeraldas.",
                "A casa fica " + where + ". O mapa que acompanha esta carta a marca com um X.\n\nSe quiser, o "
                        + "emissário o leva até lá.\n\nO conselho o espera.\n\n    O Rector"}
                : new String[]{
                "A " + name + ",\n\nO período terminou, e a casa não o vê há dias.\n\nO conselho o chama de volta: "
                        + owing(player),
                "Quem não volta não deixa de ser da casa; mas não está em dia com ela, e os mestres não o examinam "
                        + "até que esteja.\n\nA casa fica " + where + ". O mapa a marca com um X.\n\n    O Rector"};
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = book.getOrCreateTag();
        tag.putString("title", message == Emissary.Message.INVITATION ? "Convite da Universidade"
                : "Chamado da Universidade");
        tag.putString("author", "O Rector");
        ListTag list = new ListTag();
        for (String page : pages) {
            list.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(page))));
        }
        tag.put("pages", list);
        tag.putBoolean("resolved", true);
        return book;
    }

    /** Whether {@code player} has an Emissary on its way to them now, near them: for a test. */
    @Nullable
    public static Wizard near(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(Wizard.class, player.getBoundingBox().inflate(64.0D),
                wizard -> wizard.errand().filter(errand -> errand.player().equals(player.getUUID())).isPresent())
                .stream().findFirst().orElse(null);
    }
}
