package com.elderlexicon.mod.spell.nature;

import com.elderlexicon.mod.spell.AirPressure;
import com.elderlexicon.mod.spell.Heat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The physical state of one world where energy is active (docs/interacoes-design.md): the temperature and pressure of
 * each block, kept only where they differ from the surroundings. The matter itself is the world's blocks; this holds
 * what Minecraft has no place for.
 * <p>
 * The laws never name an element: heat flows from hot to cold, pressure from high to low, and a block changes phase
 * when it is past its point and has taken the latent heat for it. Boiling water gives off vapor, and vapor is pressure;
 * pressure with nowhere to go bursts. Water thrown into the air falls as droplets and pools where it lands (as snow, if
 * the air is freezing); in freezing, stirred air, droplets and the ice they freeze into rub against each other and
 * separate charge, as in a storm cloud, and enough charge strikes as lightning. Whatever element
 * or spell brought the energy, these laws decide what it does when it meets the rest of the world.
 * <p>
 * Pure: the world is read through {@link Matter}, and what should change in it comes back in a {@link Step}.
 */
public final class NatureField {

    /** How the field reads the world: what each block is made of. Keys are packed like {@code BlockPos.asLong}. */
    public interface Matter {
        Material at(long key);
    }

    public record Change(long key, Material from, Material to) {
    }

    /** Water boiling at {@code key}, giving off {@code amount} UMU worth of vapor this step. */
    public record Steam(long key, double amount) {
    }

    /** Pressure with nowhere to go, released at once around {@code key}. */
    public record Burst(long key, double pressure) {
    }

    /** Charge that got too strong, released at once as lightning from around {@code key}. */
    public record Discharge(long key, double charge) {
    }

    public record Step(List<Change> changes, List<Steam> steams, List<Burst> bursts, List<Discharge> discharges) {

        public static final Step EMPTY = new Step(List.of(), List.of(), List.of(), List.of());
    }

    /** The share of a temperature difference a fully conducting face evens out each step. */
    static final double HEAT_FLOW = 0.15D;
    /** Hot air under cold air passes its heat up this many times faster: it rises. */
    static final double CONVECTION = 3.0D;
    /** The share of a pressure difference a face evens out each step. */
    static final double PRESSURE_FLOW = 0.12D;
    /** The share of its extra pressure air gives back to the wide atmosphere each step. */
    static final double AIR_RELAX = 0.05D;
    /** Pressure a gas gains per UMU of heat it takes in (it expands). */
    static final double EXPANSION = 0.4D;
    /** Pressure the vapor of one UMU of boiling gives. */
    static final double VAPOR = 1.0D;

    /** Water boils at this temperature under the open sky; less under less pressure, more under more. */
    public static final double BOIL = 0.1D;
    static final double BOIL_SLOPE = 0.03D;
    /** Water freezes below this. */
    public static final double FREEZE = -0.01D;
    /** Ice and snow melt above this. */
    public static final double MELT = 0.0D;
    /** Lava sets below this: the heat that melts stone (the {@link Heat.Band#MELTING} band). */
    public static final double LAVA_SETS = 10.0D;
    /** Lava that lost this much heat in one step was quenched, and sets as glass (obsidian). */
    static final double QUENCH = 0.1D;

    /** Latent heat, in UMU per block, of each change of phase. */
    public static final double LATENT_ICE = 1.0D;
    public static final double LATENT_SNOW = 0.3D;
    public static final double LATENT_BOIL = 5.0D;
    public static final double LATENT_FREEZE = 1.0D;

    /** The share of its droplets a block of air lets fall to the one below each step. */
    static final double FALL = 0.5D;
    /** UMU of water that make one block of it (a water source is 3 UMU of aqua). */
    public static final double WATER_UMU = 3.0D;
    /** The share of a puddle that soaks into the ground each step, until it is enough to make a block of water. */
    static final double SOAK = 0.01D;
    /** Stirring each pressure flow leaves in the air it crosses, per unit of pressure. */
    static final double FLOW_STIR = 0.5D;
    /** How readily droplets in stirred air separate charge: per UMU of droplets, the share of the stirring turned into charge. */
    static final double CHARGING = 1.0D;
    /** The share of its stirring the air loses each step as it calms. */
    static final double CALM = 0.25D;
    /** The share of its charge the air leaks each step. */
    static final double LEAK = 0.02D;
    /** Charge from this up gathers with its neighbours to see if together they strike. */
    static final double CHARGE_GATHERS = 0.02D;
    /** Charge that strikes as lightning, in UMU. */
    public static final double DISCHARGE = 4.0D;
    /** The widest a spray or a stirring is spread, in blocks (farther, it is too thin to matter). */
    static final double SPREAD_LIMIT = 10.0D;

    /** A block at rest is woken only for a difference bigger than this. */
    static final double WAKE_HEAT = 0.01D;
    static final double WAKE_PRESSURE = 0.05D;
    /** A cell this close to its surroundings is forgotten. */
    static final double REST_HEAT = 0.005D;
    static final double REST_PRESSURE = 0.02D;
    /** Pressure from this up gathers with its neighbours to see if together they burst. */
    static final double GATHERS = 1.0D;
    /** Steps between two bursts in one world: a burst is the world's slowest thing to work out. */
    static final int BURST_PAUSE = 10;
    /** How often, in steps, a cell reads its block again (someone may have changed it). */
    static final int REFRESH = 10;

    public static final int DEFAULT_MAX_CELLS = 20_000;

    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private static final class Cell {
        Material material;
        double heat;
        double pressure;
        double latent;
        double energyIn;
        double pressureIn;
        /** Droplets of water in the air, in UMU. */
        double moisture;
        double moistureIn;
        /** Water that fell here and lies on the ground, not yet a block of it. */
        double puddle;
        /** How stirred the air is: the energy of its motion, in UMU. */
        double turbulence;
        double stirIn;
        /** Charge separated in it, in UMU. */
        double charge;
        double before;
        int age;
        boolean stepping;

        Cell(Material material, double heat) {
            this.material = material;
            this.heat = heat;
        }
    }

    private final Map<Long, Cell> cells = new HashMap<>();
    private final int maxCells;
    private long steps;
    private long lastBurst = Long.MIN_VALUE / 2;

    public NatureField() {
        this(DEFAULT_MAX_CELLS);
    }

    public NatureField(int maxCells) {
        this.maxCells = maxCells;
    }

    // ------------------------------------------------------------------ reading

    public boolean isEmpty() {
        return cells.isEmpty();
    }

    public int size() {
        return cells.size();
    }

    public double temperature(long key) {
        Cell cell = cells.get(key);
        return cell == null ? 0.0D : cell.heat;
    }

    public double pressure(long key) {
        Cell cell = cells.get(key);
        return cell == null ? 0.0D : cell.pressure;
    }

    public double moisture(long key) {
        Cell cell = cells.get(key);
        return cell == null ? 0.0D : cell.moisture;
    }

    public double charge(long key) {
        Cell cell = cells.get(key);
        return cell == null ? 0.0D : cell.charge;
    }

    /** The heat stored in the field, in UMU, counting what is held as latent heat. */
    public double energy() {
        double total = 0.0D;
        for (Cell cell : cells.values()) {
            total += cell.heat * cell.material.capacity + cell.latent;
        }
        return total;
    }

    /** Where water boils under a given pressure: lower in a vacuum, higher when pressed. */
    public static double boilingPoint(double pressure) {
        double ratio = Math.max(0.02D, (AirPressure.UMU_PER_AIR + pressure) / AirPressure.UMU_PER_AIR);
        return BOIL + BOIL_SLOPE * Math.log(ratio);
    }

    // ------------------------------------------------------------------ writing

    /** Wakes the block at {@code key} (with the heat it has by nature, for lava). */
    public void wake(Matter world, long key) {
        cellAt(world, key);
    }

    /**
     * Warms the block at {@code key} toward {@code target}, spending at most {@code budget} UMU, and says how much it
     * spent. Radiated heat warms matter, not the air it crosses.
     */
    public double warm(Matter world, long key, double target, double budget) {
        if (budget <= 0.0D) {
            return 0.0D;
        }
        Material material = world.at(key);
        if (material.gas || material == Material.VOID) {
            return 0.0D;
        }
        Cell cell = cellAt(world, key);
        if (cell == null || cell.heat >= target) {
            return 0.0D;
        }
        double given = Math.min(budget, (target - cell.heat) * cell.material.capacity);
        cell.heat += given / cell.material.capacity;
        return given;
    }

    /** Puts {@code energy} UMU of heat into the block at {@code key} (a gas expands with it). */
    public void heat(Matter world, long key, double energy) {
        Cell cell = cellAt(world, key);
        if (cell == null) {
            return;
        }
        cell.heat += energy / cell.material.capacity;
        if (cell.material.gas) {
            cell.pressure += EXPANSION * energy;
        }
    }

    /** Adds {@code pressure} to the block at {@code key}, if pressure can sit there. */
    public void press(Matter world, long key, double pressure) {
        Cell cell = cellAt(world, key);
        if (cell != null && cell.material.carriesPressure()) {
            cell.pressure += pressure;
        }
    }

    /** Holds the pressure of the block at {@code key} at {@code pressure} (a vacuum holding). */
    public void hold(Matter world, long key, double pressure) {
        Cell cell = cellAt(world, key);
        if (cell != null && cell.material.carriesPressure()) {
            cell.pressure = pressure;
        }
    }

    /**
     * Throws {@code water} UMU of water into the air as droplets around {@code center}, thicker near it, over the air
     * within {@code radius}. Where there is no air, the water joins what is there.
     */
    public void spray(Matter world, long center, double radius, double water) {
        spread(world, center, radius, water, (cell, share) -> cell.moisture += share);
    }

    /**
     * Puts {@code energy} UMU of heat into the air within {@code radius} of {@code center}, more near it; negative, it
     * takes heat out (air let out of a great pressure expands and cools).
     */
    public void heatAir(Matter world, long center, double radius, double energy) {
        spread(world, center, radius, Math.abs(energy), (cell, share) -> {
            double given = Math.signum(energy) * share;
            cell.heat += given / cell.material.capacity;
            cell.pressure += EXPANSION * given;
        });
    }

    /** Stirs the air within {@code radius} of {@code center} with {@code energy} UMU of motion, more near it. */
    public void stir(Matter world, long center, double radius, double energy) {
        spread(world, center, radius, energy, (cell, share) -> cell.turbulence += share);
    }

    private interface Give {
        void give(Cell cell, double share);
    }

    private void spread(Matter world, long center, double radius, double amount, Give give) {
        if (amount <= 0.0D) {
            return;
        }
        double r = Math.max(0.5D, Math.min(SPREAD_LIMIT, radius));
        int reach = (int) Math.ceil(r);
        List<Long> keys = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double total = 0.0D;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (distance > r) {
                        continue;
                    }
                    long key = offset(center, dx, dy, dz);
                    if (!world.at(key).gas) {
                        continue;
                    }
                    double weight = 1.0D - distance / (r + 1.0D);
                    keys.add(key);
                    weights.add(weight);
                    total += weight;
                }
            }
        }
        for (int i = 0; i < keys.size(); i++) {
            Cell cell = cellAt(world, keys.get(i));
            if (cell != null) {
                give.give(cell, amount * weights.get(i) / total);
            }
        }
    }

    private Cell cellAt(Matter world, long key) {
        Cell cell = cells.get(key);
        if (cell != null) {
            return cell;
        }
        Material material = world.at(key);
        if (material == Material.VOID || cells.size() >= maxCells) {
            return null;
        }
        cell = new Cell(material, material.inherent);
        cells.put(key, cell);
        return cell;
    }

    // ------------------------------------------------------------------ the laws

    /** One step of every law, over every block where energy is active. */
    public Step step(Matter world) {
        if (cells.isEmpty()) {
            return Step.EMPTY;
        }
        List<Change> changes = new ArrayList<>();
        List<Steam> steams = new ArrayList<>();
        List<Long> keys = new ArrayList<>(cells.keySet());
        for (long key : keys) {
            Cell cell = cells.get(key);
            if (cell.age++ % REFRESH == 0) {
                Material now = world.at(key);
                if (now != cell.material) {
                    adopt(cell, now);
                }
            }
            cell.before = cell.heat;
            cell.stepping = true;
        }

        // Laws 1 and 2: heat and pressure flow across every face, from a snapshot, so the order does not matter.
        for (long key : keys) {
            Cell a = cells.get(key);
            if (a.material == Material.VOID) {
                continue;
            }
            for (int face = 0; face < FACES.length; face++) {
                long next = offset(key, FACES[face][0], FACES[face][1], FACES[face][2]);
                Cell b = cells.get(next);
                if (b != null && b.stepping && next < key) {
                    continue; // this pair was done from the other side
                }
                if (b == null) {
                    Material rest = world.at(next);
                    if (rest == Material.VOID) {
                        continue;
                    }
                    if (rest.inherent != 0.0D) {
                        // Hot by nature and at rest (a lava lake): that heat is the world as it always is, not
                        // something that happened. Only what a spell wakes of it joins the field.
                        continue;
                    }
                    boolean heatWakes = Math.abs(a.heat) > WAKE_HEAT;
                    boolean pressureWakes = a.material.carriesPressure() && rest.carriesPressure()
                            && Math.abs(a.pressure) > WAKE_PRESSURE;
                    if (heatWakes || pressureWakes) {
                        b = cellAt(world, next);
                    }
                    if (b == null) {
                        if (a.material.carriesPressure() && rest.gas) {
                            double out = PRESSURE_FLOW * a.pressure;
                            a.pressureIn -= out; // out to the open air
                            if (a.material.gas) {
                                a.stirIn += FLOW_STIR * Math.abs(out);
                            }
                        }
                        continue;
                    }
                }
                if (b.material == Material.VOID) {
                    continue;
                }
                flowHeat(a, b, FACES[face][1]);
                if (a.material.carriesPressure() && b.material.carriesPressure()) {
                    double flow = PRESSURE_FLOW * (a.pressure - b.pressure);
                    a.pressureIn -= flow;
                    b.pressureIn += flow;
                    if (a.material.gas && b.material.gas) {
                        // Air rushing from one block to the next stirs both.
                        a.stirIn += FLOW_STIR * Math.abs(flow);
                        b.stirIn += FLOW_STIR * Math.abs(flow);
                    }
                }
            }
        }

        for (Cell cell : cells.values()) {
            cell.stepping = false;
            double capacity = cell.material.capacity;
            if (capacity > 0.0D) {
                // Heat passed along by conduction does not press: air at one pressure warming its neighbour only
                // spreads the warmth. A gas expands with heat that comes into it from outside (heat, boiling).
                cell.heat += cell.energyIn / capacity;
                cell.heat -= cell.heat * cell.material.loss;
            }
            cell.pressure += cell.pressureIn;
            if (cell.material.gas) {
                cell.pressure -= cell.pressure * AIR_RELAX;
            }
            cell.energyIn = 0.0D;
            cell.pressureIn = 0.0D;
        }

        rain(world, changes);
        charge();

        // Law 3: each block past its point takes latent heat, and changes once it has all of it.
        for (Map.Entry<Long, Cell> entry : cells.entrySet()) {
            changePhase(entry.getKey(), entry.getValue(), changes, steams);
        }

        // One burst and one strike at a time: what builds up meanwhile keeps flowing, and goes in the next.
        steps++;
        List<Burst> bursts = new ArrayList<>();
        if (steps - lastBurst >= BURST_PAUSE) {
            gather(GATHERS, AirPressure.BOMB, cell -> cell.pressure, cell -> cell.pressure = 0.0D,
                    (key, total) -> bursts.add(new Burst(key, total)));
            if (!bursts.isEmpty()) {
                lastBurst = steps;
            }
        }
        List<Discharge> discharges = new ArrayList<>();
        gather(CHARGE_GATHERS, DISCHARGE, cell -> cell.charge, cell -> cell.charge = 0.0D,
                (key, total) -> discharges.add(new Discharge(key, total)));

        Iterator<Cell> iterator = cells.values().iterator();
        while (iterator.hasNext()) {
            Cell cell = iterator.next();
            if (cell.material == Material.VOID || (Math.abs(cell.heat - cell.material.inherent) < REST_HEAT
                    && Math.abs(cell.pressure) < REST_PRESSURE && cell.latent <= 0.0D && cell.moisture < 1.0E-3D
                    && cell.puddle < 0.05D && cell.turbulence < 1.0E-3D && cell.charge < 1.0E-3D)) {
                iterator.remove();
            }
        }
        return new Step(changes, steams, bursts, discharges);
    }

    /**
     * Droplets fall a block at a time; resting on the ground they pool, and every {@link #WATER_UMU} of it that has
     * gathered becomes a block of water. Falling into water, they join it.
     */
    private void rain(Matter world, List<Change> changes) {
        List<Long> wet = new ArrayList<>();
        for (Map.Entry<Long, Cell> entry : cells.entrySet()) {
            if (entry.getValue().moisture > 0.0D || entry.getValue().puddle > 0.0D) {
                wet.add(entry.getKey());
            }
        }
        for (long key : wet) {
            Cell cell = cells.get(key);
            if (!cell.material.gas) {
                cell.moisture = 0.0D;
                cell.puddle = 0.0D;
                continue;
            }
            long below = offset(key, 0, -1, 0);
            Cell under = cells.get(below);
            Material ground = under != null ? under.material : world.at(below);
            if (ground.gas) {
                if (under == null) {
                    under = cellAt(world, below);
                }
                if (under != null) {
                    double falling = cell.moisture * FALL;
                    cell.moisture -= falling;
                    under.moistureIn += falling;
                }
            } else if (ground == Material.WATER || ground == Material.VOID) {
                cell.moisture = 0.0D; // into the water below
                cell.puddle = 0.0D;
            } else {
                cell.puddle += cell.moisture;
                cell.moisture = 0.0D;
                if (cell.puddle >= WATER_UMU) {
                    cell.puddle -= WATER_UMU;
                    // In freezing air the droplets came down frozen: they settle as snow.
                    become(key, cell, cell.heat < FREEZE ? Material.SNOW : Material.WATER, changes);
                    cell.puddle = 0.0D;
                    cell.turbulence = 0.0D;
                    cell.charge = 0.0D;
                } else {
                    cell.puddle -= cell.puddle * SOAK;
                }
            }
        }
        for (Cell cell : cells.values()) {
            cell.moisture += cell.moistureIn;
            cell.moistureIn = 0.0D;
        }
    }

    /**
     * In freezing air, droplets and the ice they freeze into, tossed about by stirred air, rub against each other and
     * separate charge, taking it from the motion of the air (the friction of a storm cloud). Warm droplets do not: it
     * takes ice and water together. The air calms, and the charge leaks away slowly.
     */
    private void charge() {
        for (Cell cell : cells.values()) {
            cell.turbulence += cell.stirIn;
            cell.stirIn = 0.0D;
            if (!cell.material.gas) {
                cell.turbulence = 0.0D;
                cell.charge = 0.0D;
                continue;
            }
            if (cell.moisture > 0.0D && cell.turbulence > 0.0D && cell.heat < FREEZE) {
                double separated = Math.min(cell.turbulence, CHARGING * cell.moisture * cell.turbulence);
                cell.charge += separated;
                cell.turbulence -= separated;
            }
            cell.turbulence -= cell.turbulence * CALM;
            cell.charge -= cell.charge * LEAK;
        }
    }

    private static void flowHeat(Cell a, Cell b, int dy) {
        double conduction = Math.min(a.material.conduction, b.material.conduction);
        if (a.material.gas && b.material.gas && dy != 0) {
            boolean hotBelow = dy > 0 ? a.heat > b.heat : b.heat > a.heat;
            if (hotBelow) {
                conduction *= CONVECTION; // hot air rises
            }
        }
        double ca = a.material.capacity;
        double cb = b.material.capacity;
        double flow = HEAT_FLOW * conduction * (a.heat - b.heat) * (ca * cb / (ca + cb));
        a.energyIn -= flow;
        b.energyIn += flow;
    }

    /** The block changed under the field (someone built or broke it): the heat stays, the half-done change is lost. */
    private static void adopt(Cell cell, Material now) {
        if (now != Material.VOID && cell.material.capacity > 0.0D) {
            cell.heat = cell.heat * cell.material.capacity / now.capacity;
        }
        cell.material = now;
        cell.latent = 0.0D;
        if (!now.carriesPressure()) {
            cell.pressure = 0.0D;
        }
    }

    private static void changePhase(long key, Cell cell, List<Change> changes, List<Steam> steams) {
        switch (cell.material) {
            case ICE -> warmThrough(key, cell, MELT, LATENT_ICE, Material.WATER, changes);
            case SNOW -> warmThrough(key, cell, MELT, LATENT_SNOW, Material.AIR, changes);
            case WATER -> {
                double boil = boilingPoint(cell.pressure);
                if (cell.heat > boil) {
                    double taken = (cell.heat - boil) * cell.material.capacity;
                    double vapor = Math.min(taken, Math.max(0.0D, LATENT_BOIL - cell.latent));
                    cell.pressure += VAPOR * vapor;
                    steams.add(new Steam(key, vapor));
                    cell.latent += taken;
                    cell.heat = boil;
                    if (cell.latent >= LATENT_BOIL) {
                        // All of it is vapor now: a block of hot gas, with whatever heat was left over.
                        double left = cell.latent - LATENT_BOIL;
                        become(key, cell, Material.AIR, changes);
                        cell.heat = boil + left / Material.AIR.capacity;
                        cell.pressure += EXPANSION * left;
                    }
                } else if (cell.heat < FREEZE) {
                    coolThrough(key, cell, FREEZE, LATENT_FREEZE, Material.ICE, changes);
                } else if (cell.latent > 0.0D) {
                    // Below the boil again: the vapor that formed gives its heat back as it condenses.
                    double back = Math.min(cell.latent, (boil - cell.heat) * cell.material.capacity);
                    cell.latent -= back;
                    cell.heat += back / cell.material.capacity;
                }
            }
            case LAVA -> {
                if (cell.heat < LAVA_SETS) {
                    boolean quenched = cell.before - cell.heat >= QUENCH;
                    become(key, cell, quenched ? Material.OBSIDIAN : Material.BASALT, changes);
                }
            }
            default -> {
            }
        }
    }

    /** Past {@code point} the heat goes into changing phase; with all of {@code latent}, it becomes {@code into}. */
    private static void warmThrough(long key, Cell cell, double point, double latent, Material into,
                                    List<Change> changes) {
        if (cell.heat > point) {
            cell.latent += (cell.heat - point) * cell.material.capacity;
            cell.heat = point;
            if (cell.latent >= latent) {
                double left = cell.latent - latent;
                become(key, cell, into, changes);
                cell.heat = point + left / into.capacity;
            }
        } else if (cell.latent > 0.0D) {
            double back = Math.min(cell.latent, (point - cell.heat) * cell.material.capacity);
            cell.latent -= back;
            cell.heat += back / cell.material.capacity;
        }
    }

    /** Below {@code point} it gives off its latent heat as it sets; with all of it given, it becomes {@code into}. */
    private static void coolThrough(long key, Cell cell, double point, double latent, Material into,
                                    List<Change> changes) {
        cell.latent += (point - cell.heat) * cell.material.capacity;
        cell.heat = point;
        if (cell.latent >= latent) {
            double left = cell.latent - latent;
            become(key, cell, into, changes);
            cell.heat = point - left / into.capacity;
        }
    }

    private static void become(long key, Cell cell, Material into, List<Change> changes) {
        changes.add(new Change(key, cell.material, into));
        cell.material = into;
        cell.latent = 0.0D;
        if (!into.gas) {
            cell.moisture = 0.0D;
        }
        if (!into.carriesPressure()) {
            cell.pressure = 0.0D;
        }
    }

    private interface Amount {
        double of(Cell cell);
    }

    private interface Release {
        void clear(Cell cell);
    }

    private interface Found {
        void at(long key, double total);
    }

    /**
     * What cannot get out fast enough (pressure, charge): neighbouring blocks holding at least {@code gathers} of it are
     * gathered, and if together they pass {@code releases} it all goes at once, around the one holding the most. One
     * group goes per call; the others wait for the next step.
     */
    private void gather(double gathers, double releases, Amount amount, Release release, Found found) {
        Set<Long> seen = new HashSet<>();
        for (Map.Entry<Long, Cell> entry : cells.entrySet()) {
            long start = entry.getKey();
            if (amount.of(entry.getValue()) < gathers || seen.contains(start)) {
                continue;
            }
            List<Long> group = new ArrayList<>();
            Deque<Long> queue = new ArrayDeque<>();
            queue.add(start);
            seen.add(start);
            double total = 0.0D;
            long most = start;
            while (!queue.isEmpty()) {
                long key = queue.poll();
                Cell cell = cells.get(key);
                group.add(key);
                total += amount.of(cell);
                if (amount.of(cell) > amount.of(cells.get(most))) {
                    most = key;
                }
                for (int[] face : FACES) {
                    long next = offset(key, face[0], face[1], face[2]);
                    Cell neighbour = cells.get(next);
                    if (neighbour != null && amount.of(neighbour) >= gathers && seen.add(next)) {
                        queue.add(next);
                    }
                }
            }
            if (total >= releases) {
                found.at(most, total);
                for (long key : group) {
                    release.clear(cells.get(key));
                }
                return;
            }
        }
    }

    // ------------------------------------------------------------------ keys (the layout of BlockPos.asLong)

    private static final int X_BITS = 26;
    private static final int Z_BITS = 26;
    private static final int Y_BITS = 12;
    private static final int Z_OFFSET = Y_BITS;
    private static final int X_OFFSET = Y_BITS + Z_BITS;

    public static long key(int x, int y, int z) {
        return (((long) x & ((1L << X_BITS) - 1)) << X_OFFSET) | ((long) y & ((1L << Y_BITS) - 1))
                | (((long) z & ((1L << Z_BITS) - 1)) << Z_OFFSET);
    }

    public static int x(long key) {
        return (int) (key >> X_OFFSET);
    }

    public static int y(long key) {
        return (int) (key << (64 - Y_BITS) >> (64 - Y_BITS));
    }

    public static int z(long key) {
        return (int) (key << (64 - Z_OFFSET - Z_BITS) >> (64 - Z_BITS));
    }

    public static long offset(long key, int dx, int dy, int dz) {
        return key(x(key) + dx, y(key) + dy, z(key) + dz);
    }
}
