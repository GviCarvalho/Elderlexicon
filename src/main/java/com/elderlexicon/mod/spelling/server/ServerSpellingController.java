package com.elderlexicon.mod.spelling.server;

import com.elderlexicon.mod.command.SpellCostCalculator;
import com.elderlexicon.mod.ligabis.world.LigabisData;
import com.elderlexicon.mod.ligabis.world.LigabisManager;
import com.elderlexicon.mod.spell.SpellCastingService;
import com.elderlexicon.mod.spell.SpellTicks;
import com.elderlexicon.mod.spell.block.SpellBlock;
import com.elderlexicon.mod.spell.function.MarkHelper;
import com.elderlexicon.mod.spell.mark.NumberGlyphs;
import com.elderlexicon.mod.spelling.client.RuneSgaMapper;
import com.elderlexicon.mod.spelling.custom.CustomRuneHelper;
import com.elderlexicon.mod.spelling.data.SpellingRepertoire;
import com.elderlexicon.mod.spelling.data.SpellingRepertoireHelper;
import com.elderlexicon.mod.spelling.entity.PlacedScrollEntity;
import com.elderlexicon.mod.spelling.item.GrimoireItem;
import com.elderlexicon.mod.spelling.item.SpellConduitItem;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates and executes Spelling cast requests server-side.
 */
public final class ServerSpellingController {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_SEQUENCE = 7;
    private static final long MAX_LATENCY_MS = 2_500L;
    private static final long SUCCESS_COOLDOWN_MS = 600L;
    private static final long FAILURE_COOLDOWN_MS = 350L;
    private static final FusionResolver FUSIONS = FusionResolver.load();
    private static final Component FUSION_RUNE_DENIED = Component.literal("Grimorio aceita apenas runas originais.");

    private static final ServerSpellingController INSTANCE = new ServerSpellingController();

    private final SpellCastingService castingService = new SpellCastingService();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    /** A rune, mark or number; a number may carry a minus sign ({@code -30 ubis}). */
    private static final Pattern RUNE_TOKEN = Pattern.compile("(-?\\b[A-Za-z0-9_]+)\\b");

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
        SpellCastResponse response = process(player, sanitize(runes), activationTimestampMs, now);
        SpellingNetwork.sendSpellCastResult(player, response);
    }

    private SpellCastResponse process(ServerPlayer player,
                                      List<String> runes,
                                      long activationTimestampMs,
                                      long now) {
        if (runes.isEmpty()) {
            return applyFailure(player, Component.literal("Spell requer ao menos um termo."), now, false, runes);
        }
        if (runes.size() > MAX_SEQUENCE) {
            return applyFailure(player, Component.literal("Sequencia excede o limite permitido."), now, true, runes);
        }
        if (runes.size() == 1 && "surgit".equalsIgnoreCase(runes.get(0))) {
            SpellCastResponse framedResponse = processFramedPageSpell(player, now);
            if (framedResponse != null) {
                return framedResponse;
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
        long cooldownRemaining = remainingCooldown(player, now);
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
            return applyFailure(player, Component.literal("Nenhum grimorio em maos."), now, true, List.of("surgit"));
        }
        GrimoireExtractionResult extraction = extractRunesFromGrimoire(grimoire);
        if (extraction.hasForbiddenRune()) {
            return applyFailure(player, FUSION_RUNE_DENIED, now, true, List.of("surgit"));
        }
        if (extraction.block().isEmpty()) {
            return applyFailure(player, Component.literal("Pagina do grimorio sem feitico."), now, true, List.of("surgit"));
        }
        return castPage(player, extraction.block(), now);
    }

    /**
     * Surgit on placed scrolls: aiming straight at one reads that one only; aiming at none reads every scroll within
     * touch (the reach the player has to break or use blocks). Returns null when there is no scroll in reach, so the
     * page in hand or the grimoire is read instead.
     */
    private SpellCastResponse processFramedPageSpell(ServerPlayer player, long now) {
        Entity aimed = findTargetedScrollEntity(player);
        if (aimed != null) {
            return castPlacedScroll(player, aimed, now, true);
        }
        List<Entity> inTouch = scrollsInTouch(player);
        if (inTouch.isEmpty()) {
            return null;
        }
        return inTouch.size() == 1 ? castPlacedScroll(player, inTouch.get(0), now, true) : castLinkedScrolls(player, inTouch, now);
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
            List<String> runes = lastSuccess == null ? List.of("surgit") : lastSuccess.runes();
            return new SpellCastResponse(true, message, warnings, maxCooldown, runes);
        }

        if (firstFailure != null) {
            return new SpellCastResponse(false, firstFailure.message(), warnings, maxCooldown, firstFailure.runes());
        }
        return applyFailure(player, Component.literal("Nenhum pergaminho vinculado valido."), now, true, List.of("surgit"));
    }

    private SpellCastResponse castPlacedScroll(ServerPlayer player, Entity scrollEntity, long now, boolean enforceReach) {
        if (scrollEntity == null || scrollEntity.isRemoved() || !scrollEntity.isAlive()) {
            return applyFailure(player, Component.literal("Pergaminho vinculado indisponivel."), now, true, List.of("surgit"));
        }
        if (enforceReach) {
            double reach = Math.max(1.0D, player.getBlockReach());
            if (player.distanceToSqr(scrollEntity) > reach * reach) {
                return applyFailure(player, Component.literal("Pergaminho fora de alcance."), now, true, List.of("surgit"));
            }
        }
        ItemStack displayed = displayedScroll(scrollEntity);
        if (!isDetachedPage(displayed)) {
            return applyFailure(player, Component.literal("Suporte sem pergaminho destacado."), now, true, List.of("surgit"));
        }
        CompoundTag tag = displayed.getTag();
        String pageText = tag == null ? "" : tag.getString("DetachedPageText");
        if (pageText == null) {
            pageText = "";
        }
        GrimoireExtractionResult extraction = extractRunesFromText(pageText);
        if (extraction.hasForbiddenRune()) {
            return applyFailure(player, FUSION_RUNE_DENIED, now, true, List.of("surgit"));
        }
        if (extraction.block().isEmpty()) {
            return applyFailure(player, Component.literal("Pagina destacada sem feitico."), now, true, List.of("surgit"));
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
        if (extraction.hasForbiddenRune()) {
            return applyFailure(player, FUSION_RUNE_DENIED, now, true, List.of("surgit"));
        }
        if (extraction.block().isEmpty()) {
            return applyFailure(player, Component.literal("Pagina destacada sem feitico."), now, true, List.of("surgit"));
        }

        SpellCastResponse response = castPage(player, extraction.block(), now);
        if (response.success()) {
            page.shrink(1);
            player.getInventory().setChanged();
        }
        return response;
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
        SpellBlock block = SpellBlock.parse(pageText, this::normalizeRune);
        boolean forbidden = block.allRuneIds().stream()
                .anyMatch(rune -> FUSIONS.isKnownRune(rune) && !FUSIONS.isOriginalRune(rune));
        return new GrimoireExtractionResult(block, forbidden);
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
            if (!bound && caster.distanceToSqr(candidate) > reach * reach) {
                continue;
            }
            scrolls.add(candidate);
        }
        for (Entity scroll : scrolls) {
            castRitualScroll(caster, scroll);
        }
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
        return scrolls.size() + waiting;
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
        GrimoireExtractionResult extraction = extractRunesFromText(tag == null ? "" : tag.getString("DetachedPageText"));
        if (extraction.hasForbiddenRune()) {
            caster.sendSystemMessage(FUSION_RUNE_DENIED);
            return;
        }
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
        SpellCastingService.Result result = castingService.castBlock(caster, spells, delayed -> {
            caster.sendSystemMessage(delayed.message());
            finishCast(caster, delayed, shown).forEach(caster::sendSystemMessage);
        });
        caster.sendSystemMessage(Component.literal("Ritual: ").append(result.message()));
        (result.failed() ? result.warnings() : finishCast(caster, result, shown)).forEach(caster::sendSystemMessage);
    }

    private SpellCastResponse castPage(ServerPlayer player, SpellBlock block, long now) {
        List<SpellCastingService.TimedSpell> spells = new ArrayList<>();
        List<String> expanded = new ArrayList<>();
        for (SpellBlock.Line line : block.lines()) {
            List<String> lineRunes = CustomRuneHelper.expandRunes(player, line.runeIds());
            spells.add(new SpellCastingService.TimedSpell(lineRunes, block.delaySteps(line)));
            expanded.addAll(lineRunes);
        }
        List<String> shown = List.copyOf(expanded);

        SpellCastingService.Result result = castingService.castBlock(player, spells, delayed -> {
            List<Component> delayedWarnings = finishCast(player, delayed, shown);
            player.sendSystemMessage(delayed.message());
            delayedWarnings.forEach(player::sendSystemMessage);
        });
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
        Matcher matcher = RUNE_TOKEN.matcher(token);
        if (!matcher.find()) {
            return "";
        }
        String cleaned = matcher.group(1);
        if (cleaned == null || cleaned.isBlank()) {
            return "";
        }
        // Numbers are written one glyph per digit ("SQ" is 20); older pages may still mix glyphs and digits ("S0").
        Optional<String> number = NumberGlyphs.read(cleaned);
        if (number.isPresent()) {
            return number.get();
        }
        if (cleaned.length() == 1) {
            return RuneSgaMapper.runeForGlyph(cleaned.charAt(0)).orElse("");
        }
        return cleaned.toLowerCase(Locale.ROOT);
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

    private record GrimoireExtractionResult(SpellBlock block, boolean hasForbiddenRune) {
        private static final GrimoireExtractionResult EMPTY = new GrimoireExtractionResult(SpellBlock.empty(), false);
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
