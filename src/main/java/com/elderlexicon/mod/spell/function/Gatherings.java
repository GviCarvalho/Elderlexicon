package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A condensation being gathered (docs/condensacao-design.md): for as long as it charges, what was captured keeps
 * streaming from where it was into one point, and there it grows into an orb of its element, with a sound rising as
 * it fills. When the charge is full the function releases it.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class Gatherings {

    private static final List<Gathering> GATHERINGS = new ArrayList<>();

    private Gatherings() {
    }

    /** Where a captured source was, and what it was. */
    record Origin(BlockPos pos, BlockState state, double value) {
    }

    /** {@code body} is set when what is gathered comes out of a mage: it streams from them, wherever they go. */
    private record Gathering(ServerLevel level, VitaElement element, List<Origin> origins, Supplier<Vec3> body,
                             Supplier<Vec3> point, long start, int ticks) {
    }

    /** Streams {@code origins} into {@code point} for {@code ticks}, growing an orb of {@code element} there. */
    static void gather(ServerLevel level, VitaElement element, List<Origin> origins, Supplier<Vec3> point, int ticks) {
        if (origins.isEmpty() || ticks <= 0) {
            return;
        }
        GATHERINGS.add(new Gathering(level, element, List.copyOf(origins), null, point, level.getGameTime(), ticks));
    }

    /**
     * Streams what {@code mage} gives of {@code element} out of their body into {@code point} for {@code ticks}: the
     * energy is seen leaving them and gathering where it will be released.
     */
    public static void fromBody(ServerLevel level, net.minecraft.world.entity.Entity mage, VitaElement element,
                                Supplier<Vec3> point, int ticks) {
        if (ticks <= 0) {
            return;
        }
        Supplier<Vec3> body = () -> mage.position().add(0.0D, mage.getBbHeight() * 0.6D, 0.0D);
        GATHERINGS.add(new Gathering(level, element, List.of(), body, point, level.getGameTime(), ticks));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || GATHERINGS.isEmpty()) {
            return;
        }
        for (Gathering gathering : new ArrayList<>(GATHERINGS)) {
            ServerLevel level = gathering.level();
            long age = level.getGameTime() - gathering.start();
            if (age > gathering.ticks()) {
                GATHERINGS.remove(gathering);
                continue;
            }
            double filled = (double) age / gathering.ticks();
            Vec3 point = gathering.point().get();
            // What was captured keeps streaming in, a little from each source in turn.
            if (age % 2 == 0) {
                if (gathering.body() != null) {
                    stream(level, gathering.element(), gathering.body().get(), Blocks.DIRT.defaultBlockState(), point, 3);
                } else {
                    for (int i = 0; i < 2; i++) {
                        Origin origin = gathering.origins().get(level.random.nextInt(gathering.origins().size()));
                        stream(level, gathering.element(), origin, point, 2);
                    }
                }
            }
            if (age % 10 == 0) {
                level.playSound(null, point.x, point.y, point.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0F,
                        (float) (0.5D + 1.5D * filled));
            }
        }
    }

    /**
     * {@code count} particles of the element streaming from where the source was to {@code toward}: fire as flames,
     * water as the glints a conduit draws in, earth as dust of what it was (arcing, being heavy), air as wisps.
     */
    static void stream(ServerLevel level, VitaElement element, Origin origin, Vec3 toward, int count) {
        stream(level, element, Vec3.atCenterOf(origin.pos()), origin.state(), toward, count);
    }

    /** The same, from any point; {@code state} is what earth's dust looks like. Vis streams as glyphs of mana. */
    static void stream(ServerLevel level, VitaElement element, Vec3 from, BlockState state, Vec3 toward, int count) {
        Vec3 path = toward.subtract(from);
        for (int i = 0; i < count; i++) {
            double ox = (level.random.nextDouble() - 0.5D) * 0.6D;
            double oy = (level.random.nextDouble() - 0.5D) * 0.6D;
            double oz = (level.random.nextDouble() - 0.5D) * 0.6D;
            switch (element) {
                // Nautilus glints fly from where they appear plus their motion back to where they appear: spawned at
                // the destination with the way back as motion, they stream from the source into it.
                case AQUA -> level.sendParticles(ParticleTypes.NAUTILUS, toward.x, toward.y, toward.z, 0,
                        -path.x + ox, -path.y + oy, -path.z + oz, 1.0D);
                case IGNI -> {
                    Vec3 v = path.scale(1.0D / 18.0D);
                    level.sendParticles(ParticleTypes.FLAME, from.x + ox, from.y + oy, from.z + oz, 0, v.x, v.y, v.z, 1.0D);
                }
                case FIRMO -> {
                    Vec3 v = path.scale(1.0D / 12.0D).add(0.0D, 0.25D, 0.0D);
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), from.x + ox,
                            from.y + oy, from.z + oz, 0, v.x, v.y, v.z, 1.0D);
                }
                // Glyphs fly from where they appear plus their motion back to where they appear, like the glints of
                // water: spawned at the point with the way back as motion, they stream from the mage into it.
                case BALANCED -> level.sendParticles(ParticleTypes.ENCHANT, toward.x, toward.y, toward.z, 0,
                        -path.x + ox, -path.y + oy, -path.z + oz, 1.0D);
                default -> {
                    Vec3 v = path.scale(1.0D / 20.0D);
                    level.sendParticles(ParticleTypes.CLOUD, from.x + ox, from.y + oy, from.z + oz, 0, v.x, v.y, v.z, 1.0D);
                }
            }
        }
    }

    private static ParticleOptions orbOf(Gathering gathering) {
        return switch (gathering.element()) {
            case IGNI -> ParticleTypes.FLAME;
            case AQUA -> ParticleTypes.BUBBLE_POP;
            case FIRMO -> new BlockParticleOption(ParticleTypes.BLOCK, gathering.origins().get(0).state());
            default -> ParticleTypes.WHITE_ASH;
        };
    }
}
