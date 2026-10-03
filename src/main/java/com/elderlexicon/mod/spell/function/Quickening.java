package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.magic.matter.Matter;
import com.elderlexicon.mod.magic.matter.Particles;
import com.elderlexicon.mod.spell.life.Animation;
import com.elderlexicon.mod.spell.life.Beings;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The spirit holding what is invoked in one instant before it takes form (docs/vita-design.md): every energy a vocant
 * releases, and all the matter a vocant brings from the world with tenet, waits to the end of the tick, and what is
 * released together in one place is weighed by the law of animation, in particles (docs/particulas-design.md). If it
 * binds into a being, the being is born and nothing of it takes form as water, air, fire, earth, light or the things
 * brought; if not, each takes its form as it would have.
 */
@Mod.EventBusSubscriber(modid = ElderLexicon.MODID)
public final class Quickening {

    /** What is released this close together in one instant is weighed together. */
    private static final double TOGETHER = 2.5D;

    /** What one release offers: the particles it would give a body, or the Vis it would give the anchor. */
    private record Offer(ServerLevel level, ServerPlayer caster, Vec3 at, Particles body, double vis,
                         Runnable manifest) {
    }

    private static final List<Offer> PENDING = new ArrayList<>();

    private Quickening() {
    }

    /** {@code umu} of {@code element} invoked at {@code at}; {@code manifest} gives it its form, if no being binds. */
    static void offer(ServerLevel level, ServerPlayer caster, Vec3 at, VitaElement element, double umu,
                      Runnable manifest) {
        if (element == VitaElement.BALANCED) {
            PENDING.add(new Offer(level, caster, at, Particles.NONE, umu, manifest));
        } else {
            PENDING.add(new Offer(level, caster, at, Particles.of(element, Particles.ofUmu(umu)), 0.0D, manifest));
        }
    }

    /**
     * Matter brought from the world to {@code at} (the ingredients of a body: flesh, water, air); {@code manifest} puts
     * it down, if no being binds.
     */
    static void offer(ServerLevel level, ServerPlayer caster, Vec3 at, List<Matter> brought, Runnable manifest) {
        Particles body = Particles.NONE;
        for (Matter matter : brought) {
            body = body.plus(matter.particles());
        }
        PENDING.add(new Offer(level, caster, at, body, 0.0D, manifest));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        List<Offer> offers = new ArrayList<>(PENDING);
        PENDING.clear();
        boolean[] taken = new boolean[offers.size()];
        for (int i = 0; i < offers.size(); i++) {
            if (taken[i]) {
                continue;
            }
            List<Offer> together = new ArrayList<>();
            taken[i] = true;
            together.add(offers.get(i));
            for (int j = i + 1; j < offers.size(); j++) {
                Offer other = offers.get(j);
                if (!taken[j] && other.level() == offers.get(i).level()
                        && together.stream().anyMatch(o -> o.at().distanceTo(other.at()) <= TOGETHER)) {
                    taken[j] = true;
                    together.add(other);
                }
            }
            weigh(together);
        }
    }

    private static void weigh(List<Offer> together) {
        Particles body = Particles.NONE;
        double vis = 0.0D;
        Vec3 sum = Vec3.ZERO;
        for (Offer offer : together) {
            body = body.plus(offer.body());
            vis += offer.vis();
            sum = sum.add(offer.at());
        }
        Optional<Animation.Being> being = vis >= Animation.ANCHOR ? Animation.quicken(body, vis) : Optional.empty();
        if (being.isEmpty()) {
            together.forEach(offer -> offer.manifest().run());
            return;
        }
        Offer first = together.get(0);
        Vec3 at = sum.scale(1.0D / together.size());
        Beings.bear(first.level(), first.caster(), at, being.get());
        if (being.get().leftoverVis() > 1.0E-3D) {
            // What Vis was beyond the anchor goes as light.
            VisSpots.light(first.level(), at, being.get().leftoverVis());
        }
    }
}
