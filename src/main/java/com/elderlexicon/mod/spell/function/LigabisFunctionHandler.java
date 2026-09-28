package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.ligabis.Aspect;
import com.elderlexicon.mod.ligabis.world.LigabisManager;
import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.magic.lexicon.SourceSpec;
import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The bind operation ({@code ligabis}, docs/ligabis-design.md): the aspect written before the marks is what the bond
 * binds, as the lexicon says of it (firmo integrity, igni heat, aqua breath, aura motion, vis reading), and the Ligabis
 * engine ({@link LigabisManager}) reads the sentence, makes the bond and marks what the mage aims at.
 */
public final class LigabisFunctionHandler implements SpellFunctionHandler {

    private static final double RANGE = 12.0D;

    @Override
    public void execute(SpellContext context, VitaElement element) {
        ServerPlayer player = context.player();
        if (!SpellEffects.isPlayerValid(player)) {
            return;
        }
        Optional<Aspect> aspect = boundAspect(context.words());
        if (aspect.isEmpty()) {
            player.sendSystemMessage(Component.literal("Ligabis requer um elemento valido antes da runa."));
            return;
        }
        LigabisManager manager = LigabisManager.get();
        if (manager == null) {
            player.sendSystemMessage(Component.literal("Ligabis ainda nao esta pronto neste mundo."));
            return;
        }
        SpellEffects.SpellImpact aimed = SpellEffects.findImpact(player, RANGE);
        Entity target = aimed.entity();
        BlockPos blockPos = aimed.blockPos();
        boolean hasBlock = blockPos != null && !player.serverLevel().getBlockState(blockPos).isAir();
        if (target == null && aspect.get() == Aspect.AURA) {
            // A bond of motion may hold a dropped item, which the aim never picks: it needs its own ray.
            target = findLooseItem(player, RANGE);
        }
        if (target == null && !hasBlock) {
            // Mirando pro nada: o mago se marca.
            target = player;
        }
        manager.castLink(player, context.words(), target, hasBlock ? blockPos : null);
    }

    /** What the sentence binds: the first source written that binds something, as the lexicon says. */
    private static Optional<Aspect> boundAspect(List<String> lexemes) {
        Lexicon lexicon = Lexicons.get();
        for (String word : lexemes) {
            if (lexicon.isBinding(word)) {
                break;
            }
            Optional<String> bond = lexicon.source(word).map(SourceSpec::bond);
            if (bond.isPresent()) {
                try {
                    return Optional.of(Aspect.valueOf(bond.get().trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException unknown) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /** {@code SpellEffects.findImpact} only picks "pickable" entities, which a dropped item never is. */
    private Entity findLooseItem(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player.level(),
                player,
                eye,
                end,
                searchBox,
                candidate -> candidate instanceof ItemEntity && candidate != player);
        return hit == null ? null : hit.getEntity();
    }
}
