package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.vita.VitaElement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpediuntFunctionHandlerTest {

    @Test
    void computeImpulseAlignsHorizontally() {
        Vec3 origin = new Vec3(0.0D, 64.0D, 0.0D);
        Vec3 target = new Vec3(1.0D, 64.0D, 0.0D);

        Vec3 impulse = ImpediuntFunctionHandler.computeImpulse(origin, target);

        assertEquals(0.35D, impulse.length(), 1.0E-6D);
        assertEquals(0.35D, impulse.x, 1.0E-6D);
        assertEquals(0.0D, impulse.z, 1.0E-6D);
    }

    @Test
    void executesRepelAgainstGateway() {
        FakeGateway gateway = new FakeGateway();
        gateway.targets = List.of(
            new FakeTarget(new Vec3(1.0D, 0.0D, 0.0D), gateway.pushes()),
            new FakeTarget(new Vec3(0.0D, 0.0D, -2.0D), gateway.pushes())
        );

        ImpediuntFunctionHandler handler = new ImpediuntFunctionHandler(gateway);
        SpellContext context = new SpellContext(null, List.of(), Optional.empty(), VitaElement.BALANCED, 0.0D, List.of(), List.of());

        handler.execute(context, VitaElement.BALANCED);

        assertEquals(2, gateway.pushes().size());
        Vec3 first = gateway.pushes().get(0);
        assertEquals(0.35D, first.x, 1.0E-6D);
        assertEquals(0.2D, first.y, 1.0E-6D);
        assertEquals(0.0D, first.z, 1.0E-6D);

        Vec3 second = gateway.pushes().get(1);
        assertEquals(0.0D, second.x, 1.0E-6D);
        assertEquals(0.2D, second.y, 1.0E-6D);
        assertEquals(-0.35D, second.z, 1.0E-6D);

        assertTrue(gateway.repelled);
        assertEquals(VitaElement.BALANCED, gateway.lastElement);
        assertFalse(gateway.noMatching);
    }

    @Test
    void filtersTargetsByElement() {
        FakeGateway gateway = new FakeGateway();
        gateway.targets = List.of(
            new FakeTarget(new Vec3(1.0D, 0.0D, 0.0D), gateway.pushes(), VitaElement.AQUA),
            new FakeTarget(new Vec3(-1.0D, 0.0D, 0.0D), gateway.pushes(), VitaElement.IGNI)
        );

        ImpediuntFunctionHandler handler = new ImpediuntFunctionHandler(gateway);
        SpellContext context = new SpellContext(null, List.of(), Optional.empty(), VitaElement.AQUA, 0.0D, List.of(), List.of());

        handler.execute(context, VitaElement.AQUA);

        assertEquals(1, gateway.pushes().size(), "only aqua-aligned target should move");
        Vec3 push = gateway.pushes().get(0);
        assertEquals(0.35D, push.x, 1.0E-6D);
        assertEquals(0.2D, push.y, 1.0E-6D);
        assertEquals(0.0D, push.z, 1.0E-6D);
        assertTrue(gateway.repelled);
        assertEquals(VitaElement.AQUA, gateway.lastElement);
        assertFalse(gateway.noMatching);
    }

    @Test
    void notifiesWhenNoTargetsMatchFilter() {
        FakeGateway gateway = new FakeGateway();
        gateway.targets = List.of(
            new FakeTarget(new Vec3(1.0D, 0.0D, 0.0D), gateway.pushes(), VitaElement.AQUA)
        );

        ImpediuntFunctionHandler handler = new ImpediuntFunctionHandler(gateway);
        SpellContext context = new SpellContext(null, List.of(), Optional.empty(), VitaElement.AQUA, 0.0D, List.of(), List.of());

        handler.execute(context, VitaElement.IGNI);

        assertEquals(0, gateway.pushes().size());
        assertFalse(gateway.repelled);
        assertNull(gateway.lastElement);
        assertTrue(gateway.noMatching);
    }

    @Test
    void emitsParticlesWhenRepellingSourceObjects() {
        FakeGateway gateway = new FakeGateway();
        gateway.targets = List.of(
            new FakeTarget(new Vec3(0.0D, 0.0D, 1.0D), gateway.pushes(), true, VitaElement.IGNI)
        );

        ImpediuntFunctionHandler handler = new ImpediuntFunctionHandler(gateway);
        SpellContext context = new SpellContext(null, List.of(), Optional.empty(), VitaElement.IGNI, 0.0D, List.of(), List.of());

        handler.execute(context, VitaElement.IGNI);

        assertEquals(1, gateway.pushes().size());
        assertTrue(gateway.sourceParticlesEmitted);
        assertTrue(gateway.repelled);
        assertEquals(VitaElement.IGNI, gateway.lastElement);
    }

    @Test
    void sourceObjectsRequireElement() {
        FakeGateway gateway = new FakeGateway();
        gateway.targets = List.of(
            new FakeTarget(new Vec3(0.0D, 0.0D, 1.0D), gateway.pushes(), true, VitaElement.AQUA)
        );

        ImpediuntFunctionHandler handler = new ImpediuntFunctionHandler(gateway);
        SpellContext context = new SpellContext(null, List.of(), Optional.empty(), VitaElement.BALANCED, 0.0D, List.of(), List.of());

        handler.execute(context, VitaElement.BALANCED);

        assertEquals(0, gateway.pushes().size());
        assertFalse(gateway.sourceParticlesEmitted);
        assertTrue(gateway.noMatching);
    }

    private static final class FakeGateway implements ImpediuntFunctionHandler.Gateway {
        private List<ImpediuntFunctionHandler.Target> targets = List.of();
        private final List<Vec3> pushes = new ArrayList<>();
        private boolean repelled;
        private VitaElement lastElement;
        private boolean noMatching;
        private boolean sourceParticlesEmitted;

        @Override
        public boolean isPlayerValid(ServerPlayer player) {
            return true;
        }

        @Override
        public Vec3 resolveOrigin(ServerPlayer player) {
            return Vec3.ZERO;
        }

        @Override
        public List<ImpediuntFunctionHandler.Target> findTargets(ServerPlayer player, double radius) {
            return targets;
        }

        @Override
        public void notifyNoTargets(ServerPlayer player) {
        }

        @Override
        public void notifyNoMatchingTargets(ServerPlayer player) {
            noMatching = true;
        }

        @Override
        public void onRepel(ServerPlayer player, VitaElement element, String elementRuneId, int affectedTargets) {
            lastElement = element;
            repelled = affectedTargets > 0;
        }

        @Override
        public void spawnSourceParticles(ServerPlayer player, VitaElement element, String elementRuneId, Vec3 location) {
            sourceParticlesEmitted = true;
        }

        List<Vec3> pushes() {
            return pushes;
        }
    }

    private static final class FakeTarget implements ImpediuntFunctionHandler.Target {
        private final Vec3 position;
        private final List<Vec3> pushes;
        private final List<VitaElement> affinities;
        private final boolean sourceObject;

        private FakeTarget(Vec3 position, List<Vec3> pushes, VitaElement... affinities) {
            this(position, pushes, false, affinities);
        }

        private FakeTarget(Vec3 position, List<Vec3> pushes, boolean sourceObject, VitaElement... affinities) {
            this.position = position;
            this.pushes = pushes;
            this.sourceObject = sourceObject;
            this.affinities = affinities == null ? List.of() : List.of(affinities);
        }

        @Override
        public Vec3 position() {
            return position;
        }

        @Override
        public void push(double x, double y, double z) {
            pushes.add(new Vec3(x, y, z));
        }

        @Override
        public boolean matches(VitaElement element) {
            if (element == null) {
                return false;
            }
            if (sourceObject && element.isBalanced()) {
                return false;
            }
            if (element.isBalanced()) {
                return true;
            }
            return affinities.contains(element);
        }

        @Override
        public boolean consumesSource() {
            return sourceObject;
        }
    }
}
