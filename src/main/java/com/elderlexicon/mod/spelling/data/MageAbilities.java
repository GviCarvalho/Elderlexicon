package com.elderlexicon.mod.spelling.data;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spelling.network.SpellingNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The mage's abilities (docs/habilidades-design.md). Before them, this is only Minecraft. The trance wakes on the first
 * night slept with 100 XP or more: the player wakes up sick, and from then on can enter the trance and learn runes. The
 * state of flow is won later, in Nahvá's infinite tower, which a mage with the trance reaches by dying. In creative,
 * every ability is had.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class MageAbilities {

    /** The experience that, slept on, wakes the trance. */
    public static final int TRANCE_EXPERIENCE = 100;
    private static final String WAKING_TAG = ElderLexicon.MODID + "_trance_waking";

    private MageAbilities() {
    }

    /** In Nahvá every spirit speaks: there the will is law (book 2.1.2). */
    public static boolean hasTrance(ServerPlayer player) {
        return player.isCreative() || SpellingRepertoireHelper.get(player).hasTrance()
                || com.elderlexicon.mod.nahva.Nahva.isNahva(player.level());
    }

    public static boolean hasFlow(ServerPlayer player) {
        return player.isCreative() || SpellingRepertoireHelper.get(player).hasFlow();
    }

    /**
     * A player fallen asleep: with 100 XP or more and the trance still asleep in them, this night wakes it; they will
     * know it when they wake. True when it does (and so the night brings no other dream).
     */
    public static boolean sleptForTrance(ServerPlayer player) {
        if (SpellingRepertoireHelper.get(player).hasTrance() || player.totalExperience < TRANCE_EXPERIENCE) {
            return false;
        }
        player.getPersistentData().putBoolean(WAKING_TAG, true);
        return true;
    }

    /** The morning after: the player wakes up sick, and the trance is woken in them. */
    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.getPersistentData().getBoolean(WAKING_TAG)) {
            return;
        }
        player.getPersistentData().remove(WAKING_TAG);
        grantTrance(player);
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 240, 0));
        player.sendSystemMessage(Component.translatable("message.elderlexicon.trance_woken")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
    }

    /** Gives the trance; true when the player did not have it. */
    public static boolean grantTrance(ServerPlayer player) {
        SpellingRepertoire repertoire = SpellingRepertoireHelper.get(player);
        boolean changed = repertoire.setTrance(true);
        SpellingNetwork.syncToPlayer(player, repertoire);
        return changed;
    }

    /** Gives the state of flow (the trance too, which it needs); true when the player did not have it. */
    public static boolean grantFlow(ServerPlayer player) {
        SpellingRepertoire repertoire = SpellingRepertoireHelper.get(player);
        repertoire.setTrance(true);
        boolean changed = repertoire.setFlow(true);
        SpellingNetwork.syncToPlayer(player, repertoire);
        return changed;
    }

    /** Takes the abilities away again (for testing): flow, or both. */
    public static void revoke(ServerPlayer player, boolean trance) {
        SpellingRepertoire repertoire = SpellingRepertoireHelper.get(player);
        repertoire.setFlow(false);
        if (trance) {
            repertoire.setTrance(false);
        }
        SpellingNetwork.syncToPlayer(player, repertoire);
    }
}
