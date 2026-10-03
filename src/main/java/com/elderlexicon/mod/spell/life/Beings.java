package com.elderlexicon.mod.spell.life;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.magic.matter.Composition;
import com.elderlexicon.mod.magic.matter.Materials;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Beings bound by the law of animation (docs/vita-design.md): born where the energies met, as big as their body,
 * without a kern (they live, breathe and bleed, but do not act: their goals are taken away, and stay away when the
 * world is loaded again), and malformed where their body is off from their kind's: each element too much or too little
 * brings what the body suffers from it (ReadmeVITA 5), harder the further off, lethal when far enough.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class Beings {

    /** The kind of the table a homunculus is: a person's proportion, which its traits are measured against. */
    private static final String HOMUNCULUS = "homunculus";
    private static final String SOULLESS = ElderLexicon.MODID + ":soulless";
    private static final String MALFORMED = ElderLexicon.MODID + ":malformed";
    /** The core a transformation gave a creature, its share of each element (docs/particulas-design.md, stage 5). */
    private static final String CORE = ElderLexicon.MODID + ":core";
    private static final int SUFFERING_TICKS = 40;

    private Beings() {
    }

    /** Whether a being was made without a kern (and so a mage's kern can live in it). */
    public static boolean isSoulless(Entity entity) {
        return entity != null && entity.getPersistentData().getBoolean(SOULLESS);
    }

    /** The creature a kind of being of the table is shown by, when the game has it and it is a mob. */
    private static Optional<Mob> create(ServerLevel level, com.elderlexicon.mod.magic.matter.Being kind) {
        return EntityType.byString(kind.entity())
                .map(type -> type.create(level))
                .filter(Mob.class::isInstance)
                .map(Mob.class::cast);
    }

    /** A being is born at {@code at}; its maker is told what came to be. */
    public static void bear(ServerLevel level, ServerPlayer maker, Vec3 at, Animation.Being being) {
        Optional<Mob> made = create(level, being.kind());
        if (made.isEmpty()) {
            return;
        }
        Mob mob = made.get();
        mob.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360.0F, 0.0F);
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(Math.max(1.0D, being.health()));
        }
        mob.setHealth((float) Math.max(1.0D, being.health()));
        if (HOMUNCULUS.equals(being.kind().id())) {
            shape(mob, being);
        }
        mob.setPersistenceRequired();
        CompoundTag data = mob.getPersistentData();
        data.putBoolean(SOULLESS, true);
        if (!being.sound()) {
            CompoundTag off = new CompoundTag();
            being.deviation().forEach((element, deviation) -> off.putDouble(element.runeId(), deviation));
            data.put(MALFORMED, off);
        }
        level.addFreshEntity(mob);
        level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 0.5D, at.z, 30, 0.4D, 0.6D, 0.4D, 0.02D);
        level.sendParticles(ParticleTypes.HEART, at.x, at.y + 1.0D, at.z, 5, 0.3D, 0.3D, 0.3D, 0.0D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.NEUTRAL, 1.0F, 0.8F);
        if (maker != null) {
            maker.sendSystemMessage(Component.literal(describe(mob, being)));
        }
    }

    // ------------------------------------------------------------------ the core of a creature

    /** The kind of the table a creature of the game is ({@code minecraft:cow} is a cow); empty when it has none. */
    public static Optional<com.elderlexicon.mod.magic.matter.Being> kindOf(Entity entity) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id == null ? Optional.empty() : Materials.get().beingShownBy(id.toString());
    }

    /**
     * The core of a creature (docs/particulas-design.md, section 6): the one a transformation gave it, or else its
     * kind's, as far off as it was born; empty for a creature the table has no kind for.
     */
    public static Optional<Composition> coreOf(LivingEntity living) {
        CompoundTag data = living.getPersistentData();
        if (data.contains(CORE)) {
            Optional<Composition> stored = shares(data.getCompound(CORE), null);
            if (stored.isPresent()) {
                return stored;
            }
        }
        Optional<com.elderlexicon.mod.magic.matter.Being> kind = kindOf(living);
        if (kind.isEmpty()) {
            return Optional.empty();
        }
        if (!data.contains(MALFORMED)) {
            return Optional.of(kind.get().recipe());
        }
        return shares(data.getCompound(MALFORMED), kind.get().recipe()).or(() -> Optional.of(kind.get().recipe()));
    }

    /** The shares a tag holds, each added to {@code base}'s when there is one (a deviation); empty when they are none. */
    private static Optional<Composition> shares(CompoundTag tag, Composition base) {
        Map<VitaElement, Double> shares = new EnumMap<>(VitaElement.class);
        double total = 0.0D;
        for (VitaElement element : Animation.ELEMENTS) {
            double share = Math.max(0.0D, (base == null ? 0.0D : base.share(element)) + tag.getDouble(element.runeId()));
            shares.put(element, share);
            total += share;
        }
        return total > 1.0E-9D ? Optional.of(Composition.of(shares)) : Optional.empty();
    }

    /** A creature keeps the core it was given, and is malformed as far as its body is off from its kind's. */
    private static void keep(CompoundTag data, Animation.Being being, Composition core) {
        CompoundTag shares = new CompoundTag();
        core.shares().forEach((element, share) -> shares.putDouble(element.runeId(), share));
        data.put(CORE, shares);
        malform(data, being);
    }

    private static void malform(CompoundTag data, Animation.Being being) {
        if (being.sound()) {
            data.remove(MALFORMED);
        } else {
            CompoundTag off = new CompoundTag();
            being.deviation().forEach((element, deviation) -> off.putDouble(element.runeId(), deviation));
            data.put(MALFORMED, off);
        }
    }

    /**
     * A player's body is as far off from its kind's as their core is (stage 6): it suffers what a malformed body suffers,
     * and is sound again when the core comes back to the kind's. The core itself is the Vita's.
     */
    public static void deviate(LivingEntity living, Animation.Being being) {
        malform(living.getPersistentData(), being);
    }

    /** A creature given another core that is still of its kind: only how far off its body is changes. */
    public static void reform(LivingEntity living, Animation.Being being, Composition core) {
        keep(living.getPersistentData(), being, core);
        if (HOMUNCULUS.equals(being.kind().id()) && living instanceof Mob mob) {
            shape(mob, being);
        }
    }

    /**
     * A creature whose core became another kind's takes that kind's body where it stands: with the life it had (its
     * body converted, 5 UMU for each point), its name, its kern or the lack of one, and as far off from the new kind as
     * its body now is (docs/particulas-design.md, stage 5). The old body is gone. Empty when the game has no creature for
     * the kind.
     */
    public static Optional<Mob> become(LivingEntity old, Animation.Being being, Composition core) {
        ServerLevel level = (ServerLevel) old.level();
        Optional<Mob> made = create(level, being.kind());
        if (made.isEmpty()) {
            return Optional.empty();
        }
        Mob mob = made.get();
        mob.moveTo(old.getX(), old.getY(), old.getZ(), old.getYRot(), old.getXRot());
        mob.setYHeadRot(old.getYHeadRot());
        mob.setDeltaMovement(old.getDeltaMovement());
        double most = Math.max(1.0D, old.getMaxHealth());
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(most);
        }
        mob.setHealth((float) Math.max(1.0D, Math.min(most, being.health())));
        if (old.hasCustomName()) {
            mob.setCustomName(old.getCustomName());
            mob.setCustomNameVisible(old.isCustomNameVisible());
        }
        if (old.hasCustomName() || old instanceof Mob was && was.isPersistenceRequired()) {
            mob.setPersistenceRequired();
        }
        if (old instanceof AgeableMob young && mob instanceof AgeableMob grown) {
            grown.setAge(young.getAge());
        }
        old.getActiveEffects().forEach(effect -> mob.addEffect(new MobEffectInstance(effect)));
        mob.setRemainingFireTicks(old.getRemainingFireTicks());
        CompoundTag data = mob.getPersistentData();
        if (isSoulless(old)) {
            data.putBoolean(SOULLESS, true);
        }
        keep(data, being, core);
        if (HOMUNCULUS.equals(being.kind().id())) {
            shape(mob, being);
        }
        old.discard();
        level.addFreshEntity(mob);
        level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + 0.5D, mob.getZ(), 20, 0.4D, 0.6D, 0.4D, 0.02D);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED,
                SoundSource.NEUTRAL, 1.0F, 1.2F);
        return Optional.of(mob);
    }

    /** What a body is off in, for the spirit to say: " Imperfeito: excesso de água, falta de ar.", or nothing. */
    public static String imperfections(Animation.Being being) {
        List<String> offs = new ArrayList<>();
        being.deviation().forEach((element, deviation) -> {
            if (Math.abs(deviation) >= Animation.SOUND) {
                offs.add((deviation > 0 ? "excesso de " : "falta de ") + elementName(element));
            }
        });
        return offs.isEmpty() ? "" : " Imperfeito: " + String.join(", ", offs) + ".";
    }

    private static String describe(Mob mob, Animation.Being being) {
        StringBuilder text = new StringBuilder("A energia se ligou num corpo: ")
                .append(mob.getName().getString())
                .append(String.format(Locale.ROOT, " (%.1f de vida). Nasceu sem kern: vive, mas não age.", being.health()));
        return text.append(imperfections(being)).toString();
    }

    private static String elementName(VitaElement element) {
        return switch (element) {
            case AQUA -> "água";
            case AURA -> "ar";
            case IGNI -> "fogo";
            case FIRMO -> "terra";
            default -> "vis";
        };
    }

    /** A homunculus takes its traits from its body: air makes it quick, earth hard, fire unburnt. */
    private static void shape(Mob mob, Animation.Being being) {
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            double person = being.kind().recipe().share(VitaElement.AURA);
            speed.setBaseValue(0.25D * Math.sqrt(Math.max(0.05D, being.share(VitaElement.AURA)) / person));
        }
        AttributeInstance armor = mob.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.setBaseValue(Math.min(30.0D, 20.0D * being.share(VitaElement.FIRMO)));
        }
        AttributeInstance knockback = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockback != null) {
            knockback.setBaseValue(Math.min(1.0D, 2.0D * being.share(VitaElement.FIRMO)));
        }
        if (being.share(VitaElement.IGNI) >= 0.2D) {
            mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false,
                    false));
        }
    }

    // ------------------------------------------------------------------ living without a kern, and malformed

    /** A being without a kern has no goals: it lives, but does not act (also after the world is loaded again). */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!event.getLevel().isClientSide() && entity instanceof Mob mob && mob.getPersistentData().getBoolean(SOULLESS)) {
            mob.goalSelector.removeAllGoals(goal -> true);
            mob.targetSelector.removeAllGoals(goal -> true);
        }
    }

    /** What a malformed body suffers, every two seconds: from each element too much or too little. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity living = event.getEntity();
        if (living.level().isClientSide() || living.tickCount % SUFFERING_TICKS != 0
                || !living.getPersistentData().contains(MALFORMED)) {
            return;
        }
        CompoundTag off = living.getPersistentData().getCompound(MALFORMED);
        for (VitaElement element : Animation.ELEMENTS) {
            double deviation = off.getDouble(element.runeId());
            int tier = tier(deviation);
            if (tier > 0) {
                suffer(living, element, deviation > 0, tier);
            }
        }
    }

    /** How badly off an element is: 0 sound, 1 mild, 2 grave, 3 deadly. */
    static int tier(double deviation) {
        double off = Math.abs(deviation);
        if (off < Animation.SOUND) {
            return 0;
        }
        if (off < 0.08D) {
            return 1;
        }
        return off < 0.2D ? 2 : 3;
    }

    private static void suffer(LivingEntity living, VitaElement element, boolean excess, int tier) {
        int duration = SUFFERING_TICKS + 20;
        switch (element) {
            case AQUA -> {
                if (excess) {
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, tier - 1));
                } else {
                    living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, tier - 1));
                    if (tier >= 3) {
                        living.hurt(living.damageSources().dryOut(), 2.0F); // it dries out from within
                    }
                }
            }
            case AURA -> {
                if (excess) {
                    living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, tier - 1));
                } else if (tier >= 2) {
                    living.hurt(living.damageSources().drown(), tier - 1.0F); // it cannot draw breath
                }
            }
            case IGNI -> {
                if (excess && tier >= 2) {
                    living.setSecondsOnFire(tier);
                } else if (!excess && tier >= 2) {
                    living.setTicksFrozen(living.getTicksRequiredToFreeze() + 20 * tier);
                }
            }
            case FIRMO -> {
                if (excess) {
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, tier));
                } else if (tier >= 2) {
                    living.hurt(living.damageSources().starve(), tier - 1.0F); // its body wastes away
                }
            }
            default -> {
            }
        }
    }
}
