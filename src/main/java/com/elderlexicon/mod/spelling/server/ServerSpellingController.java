package com.elderlexicon.mod.spelling.server;

import com.elderlexicon.mod.command.SpellCostCalculator;
import com.elderlexicon.mod.ligabis.world.LigabisData;
import com.elderlexicon.mod.ligabis.world.LigabisManager;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.magic.lexicon.Rune;
import com.elderlexicon.mod.spell.SpellCastingService;
import com.elderlexicon.mod.spell.SpellTicks;
import com.elderlexicon.mod.spell.block.SpellBlock;
import com.elderlexicon.mod.galdraria.Engravings;
import com.elderlexicon.mod.spell.function.MarkHelper;
import com.elderlexicon.mod.spelling.custom.CustomRuneHelper;
import com.elderlexicon.mod.spelling.data.SpellingRepertoire;
import com.elderlexicon.mod.spelling.data.SpellingRepertoireHelper;
import com.elderlexicon.mod.spelling.entity.PlacedScrollEntity;
import com.elderlexicon.mod.spelling.item.GrimoireItem;
import com.elderlexicon.mod.spelling.item.SpellConduitItem;
import com.elderlexicon.mod.spelling.item.WandItem;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.vita.VitaSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Validates and executes Spelling cast requests server-side.
 */
public final class ServerSpellingController {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_SEQUENCE = 7;
    private static final long MAX_LATENCY_MS = 2_500L;
    private static final long SUCCESS_COOLDOWN_MS = 600L;
    private static final long FAILURE_COOLDOWN_MS = 350L;
    /** How long after the mage's voice the echo shard of a held wand repeats it: half a second. */
    private static final int ECHO_DELAY_TICKS = 10;

    private static final ServerSpellingController INSTANCE = new ServerSpellingController();

    private final SpellCastingService castingService = new SpellCastingService();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    /** A rune, mark or number; a number may carry a minus sign ({@code -30 ubis}). */

    private ServerSpellingController() {
    }

    public static ServerSpellingController getInstance() {
        return INSTANCE;
    }

    public void handleSpellCast(ServerPlayer player, List<String> runes, long activationTimestampMs) {
        if (player == null) {
            return;
        }
        long now = Util.getMillis();
        List<String> said = sanitize(runes);
        // A spell said while the spelling still recovers is not heard, and so not echoed either.
        boolean heard = remainingCooldown(player, now) <= 0L;
        SpellCastResponse response = process(player, said, activationTimestampMs, now, false);
        SpellingNetwork.sendSpellCastResult(player, SpellFeedback.forPlayer(player, response));
        if (heard && !said.isEmpty() && WandItem.holdsEcho(player)) {
            SpellTicks.schedule(player.server, ECHO_DELAY_TICKS, () -> echo(player, said));
        }
    }

    /**
     * The echo shard set in a held wand repeats what its bearer just said (docs/varinhas-design.md), word for word:
     * {@code igni vocant iactare} as itself, a lone {@code surgit} as a lone surgit. The echo is cast as the mage's own
     * spell, through the same wand, but it does not wait for the spelling to recover and is not echoed again.
     */
    private void echo(ServerPlayer player, List<String> said) {
        if (!player.isAlive() || player.hasDisconnected() || !WandItem.holdsEcho(player)) {
            return;
        }
        long now = Util.getMillis();
        SpellCastResponse response = process(player, said, now, now, true);
        SpellingNetwork.sendSpellCastResult(player, SpellFeedback.forPlayer(player, response));
    }

    private SpellCastResponse process(ServerPlayer player,
                                      List<String> runes,
                                      long activationTimestampMs,
                                      long now,
                                      boolean echoing) {
        if (runes.isEmpty()) {
            return applyFailure(player, Component.literal("Spell requer ao menos um termo."), now, false, runes);
        }
        if (runes.size() > MAX_SEQUENCE) {
            return applyFailure(player, Component.literal("Sequencia excede o limite permitido."), now, true, runes);
        }
        if (runes.size() == 1 && readsWhatIsSeen(runes.get(0))) {
            SpellCastResponse framedResponse = processFramedPageSpell(player, now);
            if (framedResponse != null) {
                return framedResponse;
            }
            SpellCastResponse engravedResponse = processEngravedItem(player, now);
            if (engravedResponse != null) {
                return engravedResponse;
            }
            SpellCastResponse scrollResponse = processDetachedPageSpell(player, now);
            if (scrollResponse != null) {
                return scrollResponse;
            }
            return processGrimoireSpell(player, now);
        }
        if (activationTimestampMs <= 0L) {
            return applyFailure(player, Component.literal("Marcador de tempo invalido."), now, true, runes);
        }
        long elapsed = Math.max(0L, now - activationTimestampMs);
        if (elapsed > MAX_LATENCY_MS) {
            return applyFailure(player, Component.literal("Entrada expirou devido a latencia."), now, true, runes);
        }
        long cooldownRemaining = echoing ? 0L : remainingCooldown(player, now);
        if (cooldownRemaining > 0L) {
            return SpellCastResponse.cooldown(Component.literal("Spelling recarregando."), cooldownRemaining);
        }

        List<String> expandedRunes = CustomRuneHelper.expandRunes(player, runes);
        if (expandedRunes.isEmpty()) {
            return applyFailure(player, Component.literal("Sequencia vazia."), now, false, runes);
        }
        if (expandedRunes.size() > MAX_SEQUENCE) {
            return applyFailure(player, Component.literal("Sequencia expandida excede o limite permitido."), now, true, expandedRunes);
        }

        if (!playerHasRunes(player, expandedRunes)) {
            return applyFailure(player, Component.literal("Sequencia contem runas nao atribuidas."), now, true, expandedRunes);
        }


        SpellCastingService.Result result = castingService.cast(player, expandedRunes);
        long appliedCooldown = applyCooldown(player, now, result.success() ? SUCCESS_COOLDOWN_MS : FAILURE_COOLDOWN_MS);

        if (result.failed()) {
            return new SpellCastResponse(false, result.message(), result.warnings(), appliedCooldown, List.copyOf(expandedRunes));
        }

        double umuSpent = result.umuSpent();
        double umuExtra = result.umuExtra();
        double totalUmu = umuSpent + umuExtra;
        VitaElement conduitElement = resolveElement(result.primaryElement(), expandedRunes);
        List<Component> responseWarnings = new ArrayList<>(result.warnings());

        double bodyLoad = Math.max(0.0D, result.bodyLoad());
        applyNauseaEffect(player, bodyLoad);
        if (bodyLoad > 1.0E-4D && !result.focusActive()) {
            double stored = bodyLoad * 0.20D;
            VitaSystem.restoreElementEnergy(player, conduitElement, stored);
            responseWarnings.add(Component.translatable(
                    "overlay.elderlexicon.spelling.absorbed",
                    SpellCostCalculator.formatCost(stored),
                    elementComponent(conduitElement)));
        }
        LOGGER.debug("Spelling sequence {} executed for {} (nausea {}s)", expandedRunes, player.getGameProfile().getName(), bodyLoad);
        return new SpellCastResponse(true, result.message(), responseWarnings, appliedCooldown, List.copyOf(expandedRunes));
    }

    /**
     * Whether a word alone makes the spirit read what the mage sees (surgit, book 5.1): a verb of the senses, as the
     * lexicon says, with nothing else written.
     */
    private static boolean readsWhatIsSeen(String word) {
        return Lexicons.get().verb(word).map(verb -> verb.sense() != null).orElse(false);
    }

    /** The word that reads, shown with the result of a reading. */
    private static List<String> reading() {
        return Lexicons.get().runes().stream()
                .filter(rune -> rune.verb().map(verb -> verb.sense() != null).orElse(false))
                .map(Rune::id)
                .findFirst()
                .map(List::of)
                .orElse(List.of());
    }

    private boolean playerHasRunes(ServerPlayer player, List<String> runes) {
        Set<String> allowed = new HashSet<>();
        try {
            SpellingRepertoire repertoire = SpellingRepertoireHelper.get(player);
            allowed.addAll(normalizeRunes(repertoire.slots()));
        } catch (IllegalStateException exception) {
            LOGGER.warn("Spelling repertoire missing for {}; falling back to defaults", player.getGameProfile().getName(), exception);
            allowed.addAll(normalizeRunes(SpellingRepertoire.defaultRunes()));
        }
        if (allowed.isEmpty()) {
            allowed.addAll(normalizeRunes(SpellingRepertoire.defaultRunes()));
        }

        for (String rune : runes) {
            String normalized = rune == null ? "" : rune.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                continue;
            }
            if (CustomRuneHelper.findCustomRune(player, normalized).isPresent()) {
                continue;
            }
            if (!allowed.contains(normalized)) {
                return false;
            }
        }
        return true;
    }

    private SpellCastResponse applyFailure(ServerPlayer player,
                                           Component message,
                                           long now,
                                           boolean setCooldown,
                                           List<String> runes) {
        long applied = setCooldown ? applyCooldown(player, now, FAILURE_COOLDOWN_MS) : remainingCooldown(player, now);
        return new SpellCastResponse(false, message, List.of(), applied, List.copyOf(runes));
    }

    private long remainingCooldown(ServerPlayer player, long now) {
        return Math.max(0L, cooldowns.getOrDefault(player.getUUID(), 0L) - now);
    }

    private long applyCooldown(ServerPlayer player, long now, long durationMs) {
        long applied = Math.max(durationMs, 0L);
        if (applied <= 0L) {
            return 0L;
        }
        long cooldownEnd = Math.max(cooldowns.getOrDefault(player.getUUID(), 0L), now + applied);
        cooldowns.put(player.getUUID(), cooldownEnd);
        return Math.max(0L, cooldownEnd - now);
    }

    private SpellCastResponse processGrimoireSpell(ServerPlayer player, long now) {
        ItemStack grimoire = findGrimoire(player);
        if (grimoire.isEmpty()) {
            return applyFailure(player, Component.literal("Nenhum grimorio em maos."), now, true, reading());
        }
        GrimoireExtractionResult extraction = extractRunesFromGrimoire(grimoire);
        if (extraction.block().isEmpty()) {
            return applyFailure(player, Component.literal("Pagina do grimorio sem feitico."), now, true, reading());
        }
        // Once the spirit has read the page, the grimoire can describe it: it keeps the text read and what it cost.
        String text = pageText(grimoire);
        return castPage(player, extraction.block(), now,
                (spent, more) -> GrimoireItem.recordReading(grimoire, text, spent, more));
    }

    private static String pageText(ItemStack grimoire) {
        CompoundTag tag = grimoire.getTag();
        if (tag == null || !tag.contains("pages", 9)) {
            return "";
        }
        ListTag pages = tag.getList("pages", 8);
        return pages.isEmpty() ? "" : pages.getString(Mth.clamp(GrimoireItem.getStoredPage(grimoire), 0, pages.size() - 1));
    }

    /**
     * Surgit on placed scrolls: aiming straight at one reads that one only; aiming at none reads every scroll within
     * touch (the reach the player has to break or use blocks). Returns null when there is no scroll in reach, so the
     * page in hand or the grimoire is read instead.
     */
    private SpellCastResponse processFramedPageSpell(ServerPlayer player, long now) {
        Entity aimed = findTargetedScrollEntity(player);
        if (aimed instanceof PlacedScrollEntity placed) {
            // A scroll that is a piece of a circle: the whole circle is read, from the heart out.
            Optional<CirclePieces.Circle> circle = CirclePieces.around(player.serverLevel(), placed.supportPos(),
                    placed.getFace());
            if (circle.isPresent()) {
                return castCircle(player, circle.get(), now, 1.0D, "");
            }
        }
        if (aimed != null) {
            return castPlacedScroll(player, aimed, now, true);
        }
        SpellCastResponse inscribed = processInscription(player, now);
        if (inscribed != null) {
            return inscribed;
        }
        // Circles within touch are read whole, without aiming at them (the aim is free for the spells themselves).
        List<CirclePieces.Circle> circles = circlesInTouch(player);
        if (!circles.isEmpty()) {
            SpellCastResponse read = null;
            for (CirclePieces.Circle circle : circles) {
                read = castCircle(player, circle, now, 1.0D, "");
            }
            return read;
        }
        List<Entity> inTouch = scrollsInTouch(player);
        if (inTouch.isEmpty()) {
            return null;
        }
        return inTouch.size() == 1 ? castPlacedScroll(player, inTouch.get(0), now, true) : castLinkedScrolls(player, inTouch, now);
    }

    /**
     * Surgit aimed at runes written on a block face: the circle they are a piece of, or the runes alone. Returns null
     * when the aim is on no written face.
     */
    private SpellCastResponse processInscription(ServerPlayer player, long now) {
        double reach = Math.max(1.0D, player.getBlockReach());
        HitResult hit = player.pick(reach, 0.0F, false);
        if (!(hit instanceof net.minecraft.world.phys.BlockHitResult blockHit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = blockHit.getBlockPos();
        net.minecraft.core.Direction face = blockHit.getDirection();
        Optional<com.elderlexicon.mod.spelling.inscription.Inscription> written =
                com.elderlexicon.mod.spelling.inscription.Inscriptions.of(player.serverLevel()).at(pos, face);
        if (written.isEmpty()) {
            return null;
        }
        Optional<CirclePieces.Circle> circle = CirclePieces.around(player.serverLevel(), pos, face);
        if (circle.isPresent()) {
            return castCircle(player, circle.get(), now, 1.0D, "");
        }
        GrimoireExtractionResult extraction = extractRunesFromText(written.get().text());
        if (extraction.block().isEmpty()) {
            return applyFailure(player, Component.literal("Inscricao sem feitico."), now, true, reading());
        }
        return castPage(player, extraction.block(), now);
    }

    /**
     * Reads a magic circle (docs/circulos-design.md): its rings one after the other from the heart out, each ring
     * beginning when the one inside it is released, and the pieces of a ring adapted to be released together (a
     * shorter page begins later). Every spell comes out of the caster, and costs {@code costFactor} times its own.
     */
    private SpellCastResponse castCircle(ServerPlayer player, CirclePieces.Circle circle, long now, double costFactor,
                                         String prefix) {
        List<List<SpellBlock>> blocks = new ArrayList<>();
        List<List<List<Integer>>> releases = new ArrayList<>();
        for (List<Integer> ring : circle.rings()) {
            List<SpellBlock> ringBlocks = new ArrayList<>();
            List<List<Integer>> ringReleases = new ArrayList<>();
            for (int index : ring) {
                GrimoireExtractionResult extraction = extractRunesFromText(circle.pieces().get(index).text());
                SpellBlock block = extraction.block();
                ringBlocks.add(block);
                ringReleases.add(block.lines().stream().map(SpellBlock.Line::releasePosition).toList());
            }
            blocks.add(ringBlocks);
            releases.add(ringReleases);
        }
        List<List<List<Integer>>> steps = com.elderlexicon.mod.spell.circle.CircleTiming.schedule(releases);
        List<SpellCastingService.TimedSpell> spells = new ArrayList<>();
        List<String> expanded = new ArrayList<>();
        for (int r = 0; r < blocks.size(); r++) {
            for (int p = 0; p < blocks.get(r).size(); p++) {
                List<SpellBlock.Line> lines = blocks.get(r).get(p).lines();
                for (int l = 0; l < lines.size(); l++) {
                    List<String> lineRunes = CustomRuneHelper.expandRunes(player, lines.get(l).runeIds());
                    spells.add(new SpellCastingService.TimedSpell(lineRunes, steps.get(r).get(p).get(l)));
                    expanded.addAll(lineRunes);
                }
            }
        }
        if (spells.isEmpty()) {
            return applyFailure(player, Component.literal("Circulo sem feitico."), now, true, reading());
        }
        List<String> shown = List.copyOf(expanded);
        SpellCastingService.Result result = castingService.castBlock(player, spells,
                delayed -> SpellFeedback.later(player, delayed, finishCast(player, delayed, shown), prefix), costFactor);
        long appliedCooldown = applyCooldown(player, now, result.success() ? SUCCESS_COOLDOWN_MS : FAILURE_COOLDOWN_MS);
        if (result.failed()) {
            return new SpellCastResponse(false, result.message(), result.warnings(), appliedCooldown, shown);
        }
        List<Component> warnings = new ArrayList<>(finishCast(player, result, shown));
        warnings.add(Component.literal("Circulo lido: " + circle.pieces().size() + " pecas em " + circle.rings().size()
                + (circle.rings().size() == 1 ? " anel." : " aneis.")));
        return new SpellCastResponse(true, result.message(), warnings, appliedCooldown, shown);
    }

    /** The circles with a piece (a scroll or runes written on a block) within the player's touch, each once. */
    private List<CirclePieces.Circle> circlesInTouch(ServerPlayer player) {
        double reach = Math.max(1.0D, player.getBlockReach()) + 0.5D;
        Vec3 eye = player.getEyePosition();
        Set<Long> seen = new HashSet<>();
        List<CirclePieces.Circle> found = new ArrayList<>();
        for (Entity scroll : scrollsInTouch(player)) {
            if (scroll instanceof PlacedScrollEntity placed) {
                CirclePieces.around(player.serverLevel(), placed.supportPos(), placed.getFace())
                        .filter(circle -> seen.add(circle.key())).ifPresent(found::add);
            }
        }
        for (com.elderlexicon.mod.spelling.inscription.Inscription written
                : com.elderlexicon.mod.spelling.inscription.Inscriptions.of(player.serverLevel()).all()) {
            if (Vec3.atCenterOf(written.pos()).distanceToSqr(eye) <= reach * reach) {
                CirclePieces.around(player.serverLevel(), written.pos(), written.face())
                        .filter(circle -> seen.add(circle.key())).ifPresent(found::add);
            }
        }
        return found;
    }

    /** Scrolls on the ground or in frames within the player's touch, closest first. */
    private List<Entity> scrollsInTouch(ServerPlayer player) {
        double reach = Math.max(1.0D, player.getBlockReach());
        List<Entity> found = new ArrayList<>(player.serverLevel().getEntities(player, player.getBoundingBox().inflate(reach),
                candidate -> isScrollCarrier(candidate) && candidate.isAlive()
                        && player.getEyePosition().distanceToSqr(candidate.position()) <= reach * reach));
        found.sort(Comparator.comparingDouble(player::distanceToSqr));
        return found;
    }

    private SpellCastResponse castLinkedScrolls(ServerPlayer player, List<Entity> scrolls, long now) {
        List<Component> warnings = new ArrayList<>();
        SpellCastResponse firstFailure = null;
        SpellCastResponse lastSuccess = null;
        long maxCooldown = 0L;
        int triggered = 0;

        Set<UUID> seen = new HashSet<>();
        for (Entity scroll : scrolls) {
            if (scroll == null || !seen.add(scroll.getUUID())) {
                continue;
            }
            SpellCastResponse response = castPlacedScroll(player, scroll, now, false);
            maxCooldown = Math.max(maxCooldown, response.cooldownMs());
            if (response.success()) {
                triggered++;
                lastSuccess = response;
                warnings.addAll(response.warnings());
            } else {
                if (firstFailure == null) {
                    firstFailure = response;
                }
                warnings.add(Component.literal("Pergaminho vinculado ignorado: ")
                        .append(response.message()));
                warnings.addAll(response.warnings());
            }
        }

        if (triggered > 0) {
            warnings.add(Component.literal("Pergaminhos vinculados ativados: " + triggered + "."));
            Component message = lastSuccess == null
                    ? Component.literal("Pergaminhos vinculados ativados.")
                    : lastSuccess.message();
            List<String> runes = lastSuccess == null ? reading() : lastSuccess.runes();
            return new SpellCastResponse(true, message, warnings, maxCooldown, runes);
        }

        if (firstFailure != null) {
            return new SpellCastResponse(false, firstFailure.message(), warnings, maxCooldown, firstFailure.runes());
        }
        return applyFailure(player, Component.literal("Nenhum pergaminho vinculado valido."), now, true, reading());
    }

    private SpellCastResponse castPlacedScroll(ServerPlayer player, Entity scrollEntity, long now, boolean enforceReach) {
        if (scrollEntity == null || scrollEntity.isRemoved() || !scrollEntity.isAlive()) {
            return applyFailure(player, Component.literal("Pergaminho vinculado indisponivel."), now, true, reading());
        }
        if (enforceReach) {
            double reach = Math.max(1.0D, player.getBlockReach());
            if (player.distanceToSqr(scrollEntity) > reach * reach) {
                return applyFailure(player, Component.literal("Pergaminho fora de alcance."), now, true, reading());
            }
        }
        ItemStack displayed = displayedScroll(scrollEntity);
        if (!isDetachedPage(displayed)) {
            return applyFailure(player, Component.literal("Suporte sem pergaminho destacado."), now, true, reading());
        }
        CompoundTag tag = displayed.getTag();
        String pageText = tag == null ? "" : tag.getString("DetachedPageText");
        if (pageText == null) {
            pageText = "";
        }
        GrimoireExtractionResult extraction = extractRunesFromText(pageText);
        if (extraction.block().isEmpty()) {
            return applyFailure(player, Component.literal("Pagina destacada sem feitico."), now, true, reading());
        }
        return castPage(player, extraction.block(), now);
    }

    private String scrollActivationMark(Entity entity) {
        if (entity == null) {
            return null;
        }
        return MarkHelper.markForEntity(entity)
                .or(() -> MarkHelper.markForItem(displayedScroll(entity)))
                .orElse(null);
    }

    private ItemStack displayedScroll(Entity entity) {
        if (entity instanceof ItemFrame frame) {
            return frame.getItem();
        }
        if (entity instanceof PlacedScrollEntity placedScroll) {
            return placedScroll.getScroll();
        }
        return ItemStack.EMPTY;
    }

    private boolean isScrollCarrier(Entity entity) {
        return entity instanceof ItemFrame || entity instanceof PlacedScrollEntity;
    }

    private Entity findTargetedScrollEntity(ServerPlayer player) {
        double reach = Math.max(1.0D, player.getBlockReach());
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        if (look.lengthSqr() == 0.0D) {
            return null;
        }
        Vec3 end = eye.add(look.scale(reach));
        HitResult blockHit = player.pick(reach, 0.0F, false);
        double blockDistanceSq = blockHit != null ? blockHit.getLocation().distanceToSqr(eye) : reach * reach;

        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player.serverLevel(),
                player,
                eye,
                end,
                searchBox,
                entity -> entity.isPickable() && isScrollCarrier(entity));
        if (entityHit == null) {
            return null;
        }
        if (entityHit.getLocation().distanceToSqr(eye) > blockDistanceSq) {
            return null;
        }
        Entity entity = entityHit.getEntity();
        return isScrollCarrier(entity) ? entity : null;
    }

    private SpellCastResponse processDetachedPageSpell(ServerPlayer player, long now) {
        ItemStack page = findDetachedPage(player);
        if (page.isEmpty()) {
            return null;
        }
        CompoundTag tag = page.getTag();
        String pageText = tag == null ? "" : tag.getString("DetachedPageText");
        if (pageText == null) {
            pageText = "";
        }
        GrimoireExtractionResult extraction = extractRunesFromText(pageText);
        if (extraction.block().isEmpty()) {
            return applyFailure(player, Component.literal("Pagina destacada sem feitico."), now, true, reading());
        }

        SpellCastResponse response = castPage(player, extraction.block(), now);
        if (response.success()) {
            page.shrink(1);
            player.getInventory().setChanged();
        }
        return response;
    }

    /**
     * Surgit with an engraved thing in hand (docs/galdraria-design.md): the spirit reads the runes carved in it, the
     * main hand first. Returns null when neither hand holds an engraving.
     */
    private SpellCastResponse processEngravedItem(ServerPlayer player, long now) {
        for (ItemStack held : List.of(player.getMainHandItem(), player.getOffhandItem())) {
            Optional<String> engraved = Engravings.of(held);
            if (engraved.isPresent()) {
                GrimoireExtractionResult extraction = extractRunesFromText(engraved.get());
                if (extraction.block().isEmpty()) {
                    return applyFailure(player, Component.literal("Gravacao sem feitico."), now, true, reading());
                }
                return castPage(player, extraction.block(), now);
            }
        }
        return null;
    }

    private ItemStack findGrimoire(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof GrimoireItem) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof GrimoireItem) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    private ItemStack findDetachedPage(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (isDetachedPage(main)) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (isDetachedPage(off)) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    private boolean isDetachedPage(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains("DetachedPageText", 8);
    }

    private GrimoireExtractionResult extractRunesFromGrimoire(ItemStack stack) {
        if (!(stack.getItem() instanceof GrimoireItem)) {
            return GrimoireExtractionResult.EMPTY;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("pages", 9)) {
            return GrimoireExtractionResult.EMPTY;
        }
        ListTag pages = tag.getList("pages", 8);
        if (pages.isEmpty()) {
            return GrimoireExtractionResult.EMPTY;
        }
        int pageIndex = Mth.clamp(GrimoireItem.getStoredPage(stack), 0, pages.size() - 1);
        return extractRunesFromText(pages.getString(pageIndex));
    }

    /**
     * Reads a page as a block: every line is an independent spell, timed by rune position.
     */
    private GrimoireExtractionResult extractRunesFromText(String pageText) {
        return new GrimoireExtractionResult(SpellBlock.parse(pageText, this::normalizeRune));
    }

    /**
     * Casts a page block: expands custom runes per line, runs the spells together and applies the
     * cooldown and body effects. Spells released later report through chat when they fire.
     */
    /**
     * A ritual step ({@code r2 surgit} written in a scroll): the spirit reads every scroll carrying {@code mark}.
     * Without a bond it only reaches what the mage could touch; bound to the mark by a vis link
     * ({@code vis eu ligabis r2}) it reads them anywhere in the dimension, as long as they are loaded. Each scroll is
     * cast on its own and paid as usual, with the flesh tribute when the Vis runs out; there is no cooldown between
     * ritual steps. Taking the mark off a scroll is what breaks the chain.
     *
     * @return how many scrolls were read
     */
    public int readMarkedScrolls(ServerPlayer caster, String mark) {
        if (caster == null || mark == null || !caster.isAlive()) {
            return 0;
        }
        String wanted = MarkHelper.sanitizeMark(mark);
        LigabisManager manager = LigabisManager.get();
        Set<Long> inCircles = new HashSet<>();
        int circles = readMarkedCircles(caster, wanted, manager, inCircles);
        boolean bound = manager != null && manager.boundForReading(caster, wanted);
        double reach = Math.max(1.0D, caster.getBlockReach());
        List<Entity> scrolls = new ArrayList<>();
        Iterable<Entity> candidates = bound
                ? caster.serverLevel().getAllEntities()
                : caster.serverLevel().getEntities(caster, caster.getBoundingBox().inflate(reach), LigabisManager::isScrollCarrier);
        for (Entity candidate : candidates) {
            if (!LigabisManager.isScrollCarrier(candidate) || !candidate.isAlive() || !wanted.equals(scrollActivationMark(candidate))) {
                continue;
            }
            if (candidate instanceof PlacedScrollEntity placed
                    && inCircles.contains(com.elderlexicon.mod.spelling.inscription.Inscription.key(placed.supportPos(),
                    placed.getFace()))) {
                continue; // read with its circle
            }
            if (!bound && caster.distanceToSqr(candidate) > reach * reach) {
                continue;
            }
            scrolls.add(candidate);
        }
        for (Entity scroll : scrolls) {
            castRitualScroll(caster, scroll);
        }
        int engraved = readMarkedEngravings(caster, wanted, bound, reach);
        int waiting = 0;
        if (bound) {
            // Bound scrolls are read anywhere in the dimension: those in unloaded chunks are loaded, then read.
            Set<UUID> read = new HashSet<>();
            scrolls.forEach(scroll -> read.add(scroll.getUUID()));
            for (Map.Entry<UUID, LigabisData.StoredEntity> stored
                    : manager.scrollsMarked(wanted, caster.serverLevel().dimension()).entrySet()) {
                if (read.contains(stored.getKey())) {
                    continue;
                }
                LigabisData.StoredEntity where = stored.getValue();
                ChunkPos chunk = new ChunkPos(BlockPos.containing(where.x(), where.y(), where.z()));
                caster.serverLevel().getChunkSource().addRegionTicket(RITUAL_TICKET, chunk, 2, chunk);
                readWhenLoaded(caster, stored.getKey(), 0);
                waiting++;
            }
        }
        return scrolls.size() + waiting + circles + engraved;
    }

    /**
     * The engraved things carrying {@code mark} (docs/galdraria-design.md), read as marked scrolls are: those the
     * caster carries, and those lying or hung within touch; bound to the mark, those anywhere loaded in the dimension.
     */
    private int readMarkedEngravings(ServerPlayer caster, String mark, boolean bound, double reach) {
        List<String> texts = new ArrayList<>();
        Inventory inventory = caster.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            engravingMarked(inventory.getItem(slot), mark).ifPresent(texts::add);
        }
        Iterable<Entity> around = bound
                ? caster.serverLevel().getAllEntities()
                : caster.serverLevel().getEntities(caster, caster.getBoundingBox().inflate(reach),
                        entity -> entity instanceof ItemEntity || entity instanceof ItemFrame);
        for (Entity entity : around) {
            if (!entity.isAlive() || (!bound && caster.distanceToSqr(entity) > reach * reach)) {
                continue;
            }
            if (entity instanceof ItemEntity lying) {
                engravingMarked(lying.getItem(), mark).ifPresent(texts::add);
            } else if (entity instanceof ItemFrame frame && !LigabisManager.isScrollCarrier(frame)) {
                engravingMarked(frame.getItem(), mark).ifPresent(texts::add);
            }
        }
        texts.forEach(text -> castRitualText(caster, text, "Gravado: "));
        return texts.size();
    }

    private static Optional<String> engravingMarked(ItemStack stack, String mark) {
        return MarkHelper.markForItem(stack).filter(mark::equals).flatMap(found -> Engravings.of(stack));
    }

    /**
     * The circles that a piece marked {@code mark} is part of (a scroll carrying the mark, or a marked block with runes
     * written on it), each read whole from anywhere in the dimension, as long as it is loaded; the farther it is, the
     * more it costs. The keys of their pieces go into {@code pieces}, so they are not read again one by one.
     */
    private int readMarkedCircles(ServerPlayer caster, String mark, LigabisManager manager, Set<Long> pieces) {
        net.minecraft.server.level.ServerLevel level = caster.serverLevel();
        List<CirclePieces.Circle> found = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Entity candidate : level.getAllEntities()) {
            if (candidate instanceof PlacedScrollEntity placed && placed.isAlive()
                    && mark.equals(scrollActivationMark(placed))) {
                CirclePieces.around(level, placed.supportPos(), placed.getFace())
                        .filter(circle -> seen.add(circle.key())).ifPresent(found::add);
            }
        }
        if (manager != null) {
            com.elderlexicon.mod.spelling.inscription.Inscriptions inscriptions =
                    com.elderlexicon.mod.spelling.inscription.Inscriptions.of(level);
            manager.markedBlocks(level.dimension()).forEach((pos, carried) -> {
                if (!mark.equals(carried) || !level.isLoaded(pos)) {
                    return;
                }
                for (net.minecraft.core.Direction face : net.minecraft.core.Direction.values()) {
                    if (inscriptions.at(pos, face).isPresent()) {
                        CirclePieces.around(level, pos, face).filter(circle -> seen.add(circle.key()))
                                .ifPresent(found::add);
                    }
                }
            });
        }
        long now = System.currentTimeMillis();
        for (CirclePieces.Circle circle : found) {
            circle.pieces().forEach(piece -> pieces.add(piece.key()));
            double distance = caster.position().distanceTo(circle.center());
            SpellCastResponse response = castCircle(caster, circle, now,
                    com.elderlexicon.mod.spell.circle.CircleTiming.distanceFactor(distance), "Circulo: ");
            caster.displayClientMessage(response.message(), true);
        }
        return found.size();
    }

    /** Keeps a bound scroll's chunk loaded for a moment while the spirit reads it from afar. */
    private static final TicketType<ChunkPos> RITUAL_TICKET =
            TicketType.create("elderlexicon_ritual", Comparator.comparingLong(ChunkPos::toLong), 100);
    /** How long to wait for a loaded chunk's scroll to appear, in ticks. */
    private static final int RITUAL_LOAD_WAIT_TICKS = 40;

    private void readWhenLoaded(ServerPlayer caster, UUID scrollId, int waited) {
        Entity scroll = caster.serverLevel().getEntity(scrollId);
        if (scroll != null) {
            castRitualScroll(caster, scroll);
            return;
        }
        if (waited < RITUAL_LOAD_WAIT_TICKS) {
            SpellTicks.schedule(caster.server, 1, () -> readWhenLoaded(caster, scrollId, waited + 1));
        }
    }

    private void castRitualScroll(ServerPlayer caster, Entity scroll) {
        ItemStack displayed = displayedScroll(scroll);
        if (!isDetachedPage(displayed)) {
            return;
        }
        CompoundTag tag = displayed.getTag();
        castRitualText(caster, tag == null ? "" : tag.getString("DetachedPageText"), "Ritual: ");
    }

    /** Casts a written text called by its mark, each line a spell, paid as usual with no cooldown. */
    private void castRitualText(ServerPlayer caster, String text, String prefix) {
        GrimoireExtractionResult extraction = extractRunesFromText(text);
        if (extraction.block().isEmpty()) {
            return;
        }
        List<SpellCastingService.TimedSpell> spells = new ArrayList<>();
        List<String> expanded = new ArrayList<>();
        for (SpellBlock.Line line : extraction.block().lines()) {
            List<String> lineRunes = CustomRuneHelper.expandRunes(caster, line.runeIds());
            spells.add(new SpellCastingService.TimedSpell(lineRunes, extraction.block().delaySteps(line)));
            expanded.addAll(lineRunes);
        }
        List<String> shown = List.copyOf(expanded);
        SpellCastingService.Result result = castingService.castBlock(caster, spells,
                delayed -> SpellFeedback.later(caster, delayed, finishCast(caster, delayed, shown), prefix));
        SpellFeedback.later(caster, result, result.failed() ? result.warnings() : finishCast(caster, result, shown), prefix);
    }

    private SpellCastResponse castPage(ServerPlayer player, SpellBlock block, long now) {
        return castPage(player, block, now, null);
    }

    /** @param reading told what the page cost: at once, then more for each spell of it released later */
    private SpellCastResponse castPage(ServerPlayer player, SpellBlock block, long now,
                                       java.util.function.BiConsumer<Double, Boolean> reading) {
        List<SpellCastingService.TimedSpell> spells = new ArrayList<>();
        List<String> expanded = new ArrayList<>();
        for (SpellBlock.Line line : block.lines()) {
            List<String> lineRunes = CustomRuneHelper.expandRunes(player, line.runeIds());
            spells.add(new SpellCastingService.TimedSpell(lineRunes, block.delaySteps(line)));
            expanded.addAll(lineRunes);
        }
        List<String> shown = List.copyOf(expanded);

        SpellCastingService.Result result = castingService.castBlock(player, spells, delayed -> {
            if (reading != null && delayed.success()) {
                reading.accept(delayed.umuSpent(), true);
            }
            SpellFeedback.later(player, delayed, finishCast(player, delayed, shown), "");
        });
        if (reading != null && result.success()) {
            reading.accept(result.umuSpent(), false);
        }
        long appliedCooldown = applyCooldown(player, now, result.success() ? SUCCESS_COOLDOWN_MS : FAILURE_COOLDOWN_MS);
        if (result.failed()) {
            return new SpellCastResponse(false, result.message(), result.warnings(), appliedCooldown, shown);
        }
        return new SpellCastResponse(true, result.message(), finishCast(player, result, shown), appliedCooldown, shown);
    }

    /** Applies nausea and stored energy from a finished spell; returns the warnings to show. */
    private List<Component> finishCast(ServerPlayer player, SpellCastingService.Result result, List<String> runes) {
        List<Component> warnings = new ArrayList<>(result.warnings());
        if (result.failed()) {
            return warnings;
        }
        VitaElement conduitElement = resolveElement(result.primaryElement(), runes);
        double bodyLoad = Math.max(0.0D, result.bodyLoad());
        applyNauseaEffect(player, bodyLoad);
        if (bodyLoad > 1.0E-4D && !result.focusActive()) {
            double stored = bodyLoad * 0.20D;
            VitaSystem.restoreElementEnergy(player, conduitElement, stored);
            warnings.add(Component.translatable(
                    "overlay.elderlexicon.spelling.absorbed",
                    SpellCostCalculator.formatCost(stored),
                    elementComponent(conduitElement)));
        }
        return warnings;
    }

    private String normalizeRune(String token) {
        return com.elderlexicon.mod.spell.block.RuneTokens.normalize(token);
    }

    private void applyNauseaEffect(ServerPlayer player, double seconds) {
        int durationTicks = (int) Math.round(Math.max(0D, seconds) * 20D);
        if (durationTicks <= 0) {
            return;
        }
        if (hasReadyConduit(player)) {
            return;
        }
        try {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, durationTicks));
        } catch (Throwable t) {
            LOGGER.warn("Failed to apply nausea effect", t);
        }
    }

    private boolean hasReadyConduit(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        return conduitReady(player.getMainHandItem()) || conduitReady(player.getOffhandItem());
    }

    private boolean conduitReady(ItemStack stack) {
        if (!(stack.getItem() instanceof SpellConduitItem conduit)) {
            return false;
        }
        return conduit.remainingCapacity(stack) > 1.0E-4D;
    }

    private VitaElement resolveElement(VitaElement reported, List<String> runes) {
        if (reported != null && reported != VitaElement.BALANCED) {
            return reported;
        }
        for (String rune : runes) {
            VitaElement candidate = VitaElement.fromRuneId(rune);
            if (candidate != VitaElement.BALANCED) {
                return candidate;
            }
        }
        return VitaElement.BALANCED;
    }

    private Component elementComponent(VitaElement element) {
        String key = "element.elderlexicon." + element.name().toLowerCase(Locale.ROOT);
        return Component.translatable(key);
    }

    private List<String> sanitize(List<String> runes) {
        if (runes == null || runes.isEmpty()) {
            return List.of();
        }
        return runes.stream()
            .filter(rune -> rune != null && !rune.isBlank())
            .map(rune -> rune.trim())
            .toList();
    }

    private Set<String> normalizeRunes(List<String> runes) {
        Set<String> normalized = new HashSet<>();
        if (runes == null || runes.isEmpty()) {
            return normalized;
        }
        runes.stream()
                .filter(Objects::nonNull)
                .map(token -> token.trim().toLowerCase(Locale.ROOT))
                .filter(token -> !token.isBlank())
                .forEach(normalized::add);
        return normalized;
    }

    private record GrimoireExtractionResult(SpellBlock block) {
        private static final GrimoireExtractionResult EMPTY = new GrimoireExtractionResult(SpellBlock.empty());
    }

    /**
     * Immutable response returned to clients.
     */
    public record SpellCastResponse(boolean success,
                                    Component message,
                                    List<Component> warnings,
                                    long cooldownMs,
                                    List<String> runes) {

        public SpellCastResponse(boolean success,
                                 Component message,
                                 List<Component> warnings,
                                 long cooldownMs,
                                 List<String> runes) {
            this.success = success;
            this.message = Objects.requireNonNull(message, "message");
            this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
            this.cooldownMs = cooldownMs;
            this.runes = runes == null ? List.of() : List.copyOf(runes);
        }

        public static SpellCastResponse cooldown(Component message, long remaining) {
            return new SpellCastResponse(false, message, List.of(), Math.max(remaining, 0L), List.of());
        }
    }
}
