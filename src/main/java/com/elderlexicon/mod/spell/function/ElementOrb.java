package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.spell.AirPressure;
import com.elderlexicon.mod.spell.Density;
import com.elderlexicon.mod.spell.Heat;
import com.elderlexicon.mod.spell.Pressure;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A condensation made visible (docs/condensacao-design.md): while it charges, the orb sits where the element is being
 * gathered and grows, and it changes as more is pressed into it: fire goes from orange to white to electric blue,
 * earth becomes denser rock (and carbon, diamond), water deepens and freezes into pressed ice, air thickens and spins
 * faster. Released, it flies straight and does what it carries where it strikes.
 */
public class ElementOrb extends Entity {

    /** What the orb looks like right now: layers of swirling air, fire or water, or a block. */
    public enum Look {
        AIR,
        FIRE,
        WATER,
        BLOCK,
        /** Vis, pure energy: an orb of light. */
        LIGHT,
        /** Earth pressed into a black hole: a sphere of black no light leaves. */
        VOID
    }

    private static final EntityDataAccessor<Integer> ELEMENT = SynchedEntityData.defineId(ElementOrb.class, EntityDataSerializers.INT);
    /** How intense it will be once all of it is gathered. */
    private static final EntityDataAccessor<Float> FULL = SynchedEntityData.defineId(ElementOrb.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(ElementOrb.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COAL = SynchedEntityData.defineId(ElementOrb.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(ElementOrb.class, EntityDataSerializers.BOOLEAN);
    /** What it is being converted into once gathered, and how long that takes (0: it is not converted). */
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(ElementOrb.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CONVERT = SynchedEntityData.defineId(ElementOrb.class, EntityDataSerializers.INT);
    /** Every element of its conversions in order, four bits each (the element's place + 1), the first lowest. */
    private static final EntityDataAccessor<Integer> CHAIN = SynchedEntityData.defineId(ElementOrb.class, EntityDataSerializers.INT);

    /** The smallest the orb starts at, and how much more it grows while it charges. */
    private static final float SEED_SIZE = 0.2F;
    private static final float GROWTH = 0.8F;
    /**
     * How long the spirit holds what the orb carries once it is released: if it has hit nothing by then, what it holds
     * is let go where the orb is (in the air, if it was thrown at the sky).
     */
    private static final int FLIGHT_TICKS = 40;
    /** The glints Vis sheds: the green-yellow of experience. */
    private static final DustParticleOptions VIS_DUST =
            new DustParticleOptions(new org.joml.Vector3f(0.6F, 1.0F, 0.2F), 1.0F);
    /** A released orb is a body like any other: it falls (as an arrow does) and the air slows it. */
    private static final double GRAVITY = 0.04D;
    private static final double DRAG = 0.99D;
    /** An orb never released (its mage gone) fades after its charge and this long. */
    private static final int FORGOTTEN_TICKS = 60;

    // Server side only.
    @Nullable
    private Supplier<Vec3> gatheringPoint;
    @Nullable
    private Consumer<Vec3> onStrike;
    @Nullable
    private Consumer<HitResult> onHit;
    @Nullable
    private UUID caster;
    private int flightStart;
    private int lastStage = -1;

    public ElementOrb(EntityType<? extends ElementOrb> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /**
     * An orb gathering {@code element} at {@code point} for {@code chargeTicks}, growing and changing until it is as
     * intense as {@code full}; {@code coal} is the coal in condensed earth (it may become diamond). The caller adds it
     * to the level.
     */
    public static ElementOrb gathering(ServerLevel level, Entity caster, java.util.List<VitaElement> chain, double full,
                                       int coal, Supplier<Vec3> point, int chargeTicks) {
        VitaElement element = chain.get(0);
        VitaElement becomes = chain.get(chain.size() - 1);
        int convertTicks = com.elderlexicon.mod.spell.Conversion.chainTicks(full, chain);
        ElementOrb orb = new ElementOrb(ElderLexicon.ELEMENT_ORB.get(), level);
        orb.caster = caster.getUUID();
        orb.gatheringPoint = point;
        orb.entityData.set(ELEMENT, element.ordinal());
        orb.entityData.set(FULL, (float) full);
        orb.entityData.set(CHARGE, Math.max(1, chargeTicks));
        orb.entityData.set(COAL, coal);
        orb.entityData.set(TARGET, (becomes == null ? element : becomes).ordinal());
        orb.entityData.set(CONVERT, Math.max(0, convertTicks));
        int packed = 0;
        for (int i = 0; i < Math.min(7, chain.size()); i++) {
            packed |= (chain.get(i).ordinal() + 1) << (4 * i);
        }
        orb.entityData.set(CHAIN, packed);
        Vec3 at = point.get();
        orb.setPos(at.x, at.y, at.z);
        return orb;
    }

    /** Releases the orb along {@code velocity}; where it strikes, {@code onStrike} does what it carries. */
    public void launch(Vec3 velocity, Consumer<Vec3> onStrike) {
        this.onStrike = onStrike;
        this.gatheringPoint = null;
        this.flightStart = tickCount;
        entityData.set(FLYING, true);
        setDeltaMovement(velocity);
        hasImpulse = true;
    }

    /**
     * Releases the orb along {@code velocity}; {@code onHit} is told what it struck (a creature, a block's face, or
     * nothing when it flew out of its time), so what it carries can land on it.
     */
    public void strike(Vec3 velocity, Consumer<HitResult> onHit) {
        launch(velocity, null);
        this.onHit = onHit;
    }

    /** The gathering is over and what it held is released some other way: the orb is gone. */
    public void spend() {
        discard();
    }

    // ------------------------------------------------------------------ how it looks as it fills

    /** The elements of its conversions, in order (just the one when it is not converted). */
    private java.util.List<VitaElement> chain() {
        java.util.List<VitaElement> chain = new java.util.ArrayList<>();
        int packed = entityData.get(CHAIN);
        VitaElement[] elements = VitaElement.values();
        for (int i = 0; i < 7; i++) {
            int code = (packed >> (4 * i)) & 0xF;
            if (code == 0 || code > elements.length) {
                break;
            }
            chain.add(elements[code - 1]);
        }
        if (chain.isEmpty()) {
            chain.add(elementAt(ELEMENT));
        }
        return chain;
    }

    /** Where it is in its life: which element it shows, how intense, and which stage (to mark each crossing). */
    private record Stage(VitaElement shown, double intensity, int index, VitaElement becoming) {
    }

    /**
     * While it gathers, the captured element growing; then each conversion of its chain in turn, each taking its own
     * time: in the first half the matter is unmade (still the old element, whole, trembling), in the second it is
     * remade as the next one, growing into all of it (the UMU are kept); at the end, the last element, whole.
     */
    private Stage stage(float partialTick) {
        double full = entityData.get(FULL);
        java.util.List<VitaElement> chain = chain();
        VitaElement last = chain.get(chain.size() - 1);
        if (flying()) {
            return new Stage(last, full, 1000, null);
        }
        double t = tickCount + partialTick - entityData.get(CHARGE);
        if (t < 0.0D || chain.size() == 1) {
            return new Stage(chain.get(0), full * filled(partialTick), 0, null);
        }
        for (int i = 0; i + 1 < chain.size(); i++) {
            int step = com.elderlexicon.mod.spell.Conversion.stepTicks(full, chain.get(i), chain.get(i + 1));
            if (step <= 0) {
                continue;
            }
            if (t < step) {
                double p = t / step;
                return p < 0.5D ? new Stage(chain.get(i), full, 1 + 2 * i, chain.get(i + 1))
                        : new Stage(chain.get(i + 1), full * (p - 0.5D) * 2.0D, 2 + 2 * i, chain.get(i + 1));
            }
            t -= step;
        }
        return new Stage(last, full, 1000, null);
    }

    /** The element the orb shows right now (see {@link #stage}). */
    public VitaElement element() {
        return stage(0.0F).shown();
    }

    private VitaElement elementAt(EntityDataAccessor<Integer> slot) {
        VitaElement[] elements = VitaElement.values();
        int index = entityData.get(slot);
        return index >= 0 && index < elements.length ? elements[index] : VitaElement.AURA;
    }

    public boolean flying() {
        return entityData.get(FLYING);
    }

    /** How much of it is gathered, from 0 to 1 (1 once it flies). */
    public float filled(float partialTick) {
        return flying() ? 1.0F : Mth.clamp((tickCount + partialTick) / entityData.get(CHARGE), 0.0F, 1.0F);
    }

    /** How intense it is right now (see {@link #stage}). */
    public double intensity(float partialTick) {
        return stage(partialTick).intensity();
    }

    public float size(float partialTick) {
        return SEED_SIZE + GROWTH * filled(partialTick);
    }

    public Look look(float partialTick) {
        return switch (element()) {
            case IGNI -> Look.FIRE;
            case FIRMO -> Density.rock(intensity(partialTick)) == Density.Rock.BLACK_HOLE ? Look.VOID : Look.BLOCK;
            case AQUA -> Pressure.band(intensity(partialTick)) == Pressure.Band.ICE ? Look.BLOCK : Look.WATER;
            case BALANCED -> Look.LIGHT;
            default -> Look.AIR;
        };
    }

    /** The block the orb looks like right now, for earth and pressed water. */
    public BlockState block(float partialTick) {
        double intensity = intensity(partialTick);
        if (element() == VitaElement.AQUA) {
            return Blocks.BLUE_ICE.defaultBlockState();
        }
        int coal = entityData.get(COAL);
        if (coal > 0 && element() == elementAt(ELEMENT)) {
            // Carbon: coal until it is pressed hard enough to be diamond.
            return Density.diamonds(coal, intensity) > 0 ? Blocks.DIAMOND_BLOCK.defaultBlockState()
                    : Blocks.COAL_BLOCK.defaultBlockState();
        }
        return EarthSpots.blockOf(Density.rock(Math.max(Density.SOIL, intensity)));
    }

    /** The colour of its layers right now: fire by its heat, water deepening, air white. */
    public int color(float partialTick) {
        double intensity = intensity(partialTick);
        return switch (element()) {
            case IGNI -> fireColor(intensity);
            case AQUA -> blend(0x4FAFFF, 0x1A4FC8, Mth.clamp(intensity / Pressure.ICE, 0.0D, 1.0D));
            // Vis: the green and yellow of experience, its everyday form.
            case BALANCED -> blend(0x7FFF20, 0xE8FF60, Mth.clamp(Math.log1p(intensity) / Math.log1p(100.0D), 0.0D, 1.0D));
            case FIRMO -> 0x000000; // a black hole: no light leaves it
            default -> 0xF2F8FF;
        };
    }

    /** How opaque air is (pressed air thickens) and how fast its layers turn; 1 for the others. */
    public float thickness(float partialTick) {
        if (element() != VitaElement.AURA) {
            return 1.0F;
        }
        return (float) (0.45D + 0.55D * Mth.clamp(intensity(partialTick) / AirPressure.BOMB, 0.0D, 1.0D));
    }

    public float spin(float partialTick) {
        if (element() != VitaElement.AURA) {
            return 1.0F;
        }
        return (float) (1.0D + intensity(partialTick) / 15.0D);
    }

    /**
     * Fire's colour by its heat, shading smoothly between the bands: orange for common fire, white at 3, the bluish
     * white of melting heat at 10, electric blue for plasma at 30.
     */
    public static int fireColor(double heat) {
        double[] heats = {Heat.COMMON, 3.0D, 10.0D, 30.0D};
        int[] colors = {0xFF7A1A, 0xFFE8B0, 0xD8ECFF, 0x6FB8FF};
        if (heat <= heats[0]) {
            return colors[0];
        }
        for (int i = 1; i < heats.length; i++) {
            if (heat < heats[i]) {
                double t = Math.log(heat / heats[i - 1]) / Math.log(heats[i] / heats[i - 1]);
                return blend(colors[i - 1], colors[i], t);
            }
        }
        return colors[colors.length - 1];
    }

    private static int blend(int from, int to, double t) {
        int r = (int) Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }

    /** Which stage it has reached (a band of heat, a rock, ice, a band of pressure), to mark each crossing. */
    private int stage() {
        double intensity = intensity(0.0F);
        int converted = stage(0.0F).index() * 100;
        return converted + switch (element()) {
            case IGNI -> Heat.band(intensity).ordinal();
            case FIRMO -> entityData.get(COAL) > 0 ? (Density.diamonds(entityData.get(COAL), intensity) > 0 ? 1 : 0)
                    : Density.rock(Math.max(Density.SOIL, intensity)).ordinal();
            case AQUA -> Pressure.band(intensity).ordinal();
            case BALANCED -> 0;
            default -> AirPressure.band(intensity).ordinal();
        };
    }

    // ------------------------------------------------------------------ life

    @Override
    protected void defineSynchedData() {
        entityData.define(ELEMENT, VitaElement.AURA.ordinal());
        entityData.define(FULL, 1.0F);
        entityData.define(CHARGE, 1);
        entityData.define(COAL, 0);
        entityData.define(FLYING, false);
        entityData.define(TARGET, VitaElement.AURA.ordinal());
        entityData.define(CONVERT, 0);
        entityData.define(CHAIN, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (flying()) {
            fly();
        } else if (!level().isClientSide) {
            gather();
        }
        if (level().isClientSide) {
            trail();
        }
    }

    private void gather() {
        if (gatheringPoint != null) {
            Vec3 at = gatheringPoint.get();
            setPos(at.x, at.y, at.z);
        }
        int stage = stage();
        if (lastStage >= 0 && stage != lastStage) {
            crossed();
        }
        lastStage = stage;
        if (tickCount > entityData.get(CHARGE) + entityData.get(CONVERT) + FORGOTTEN_TICKS) {
            discard();
        }
    }

    /** It crossed into a new stage: a burst and a sound mark it. */
    private void crossed() {
        ServerLevel level = (ServerLevel) level();
        ParticleOptions burst = switch (look(0.0F)) {
            case FIRE -> ParticleTypes.FLAME;
            case BLOCK -> new BlockParticleOption(ParticleTypes.BLOCK, block(0.0F));
            case WATER -> ParticleTypes.SPLASH;
            case AIR -> ParticleTypes.CLOUD;
            case LIGHT -> VIS_DUST;
            case VOID -> ParticleTypes.REVERSE_PORTAL;
        };
        level.sendParticles(burst, getX(), getY() + 0.3D, getZ(), 16, 0.3D, 0.3D, 0.3D, 0.05D);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F,
                0.6F + 0.3F * (stage() % 100));
    }

    private void fly() {
        setDeltaMovement(getDeltaMovement().scale(DRAG).add(0.0D, -GRAVITY, 0.0D));
        Vec3 from = position();
        Vec3 to = from.add(getDeltaMovement());
        if (!level().isClientSide) {
            HitResult hit = hitBetween(from, to);
            boolean late = tickCount - flightStart > FLIGHT_TICKS;
            if (hit.getType() != HitResult.Type.MISS || late) {
                Vec3 at = hit.getType() == HitResult.Type.MISS ? from : hit.getLocation();
                discard();
                if (onStrike != null) {
                    onStrike.accept(at);
                }
                if (onHit != null) {
                    onHit.accept(hit.getType() == HitResult.Type.MISS
                            ? BlockHitResult.miss(at, net.minecraft.core.Direction.UP, net.minecraft.core.BlockPos.containing(at))
                            : hit);
                }
                return;
            }
        }
        setPos(to.x, to.y, to.z);
    }

    /** What the orb meets on its way from {@code from} to {@code to}: a creature (never its caster) or a block. */
    private HitResult hitBetween(Vec3 from, Vec3 to) {
        // Water stops it too: struck fast, its surface is as hard as the ground.
        BlockHitResult block = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        EntityHitResult creature = ProjectileUtil.getEntityHitResult(level(), this, from, end,
                new AABB(from, end).inflate(1.0D), candidate -> candidate instanceof LivingEntity && candidate.isAlive()
                        && !candidate.isSpectator() && !candidate.getUUID().equals(caster));
        return creature != null ? creature : block;
    }

    private void trail() {
        Stage now = stage(0.0F);
        if (now.becoming() != null) {
            ParticleOptions shed = switch (now.becoming()) {
                case IGNI -> ParticleTypes.FLAME;
                case AQUA -> ParticleTypes.SPLASH;
                case FIRMO -> ParticleTypes.ASH;
                default -> ParticleTypes.CLOUD;
            };
            double spread = size(0.0F) * 0.6D;
            level().addParticle(shed, getX() + (random.nextDouble() - 0.5D) * spread,
                    getY() + (random.nextDouble() - 0.5D) * spread, getZ() + (random.nextDouble() - 0.5D) * spread,
                    0.0D, 0.03D, 0.0D);
            level().addParticle(ParticleTypes.ENCHANT, getX(), getY(), getZ(), (random.nextDouble() - 0.5D) * 1.5D,
                    (random.nextDouble() - 0.5D) * 1.5D, (random.nextDouble() - 0.5D) * 1.5D);
        }
        ParticleOptions particle = switch (look(0.0F)) {
            case FIRE -> intensity(0.0F) >= 3.0D ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
            case AIR -> ParticleTypes.CLOUD;
            case WATER -> ParticleTypes.BUBBLE_POP;
            case LIGHT -> VIS_DUST;
            case VOID -> ParticleTypes.REVERSE_PORTAL;
            case BLOCK -> null;
        };
        if (particle == null || random.nextFloat() > (flying() ? 1.0F : 0.4F)) {
            return;
        }
        double spread = size(0.0F) * 0.4D;
        level().addParticle(particle, getX() + (random.nextDouble() - 0.5D) * spread,
                getY() + (random.nextDouble() - 0.5D) * spread, getZ() + (random.nextDouble() - 0.5D) * spread,
                0.0D, 0.01D, 0.0D);
        if (element() == VitaElement.IGNI && Heat.band(intensity(0.0F)) == Heat.Band.PLASMA && random.nextFloat() < 0.5F) {
            level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(),
                    (random.nextDouble() - 0.5D) * 0.4D, (random.nextDouble() - 0.5D) * 0.4D,
                    (random.nextDouble() - 0.5D) * 0.4D);
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false; // it only lives for a moment of a spell
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
