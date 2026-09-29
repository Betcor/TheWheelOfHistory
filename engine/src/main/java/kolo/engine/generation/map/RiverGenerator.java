package kolo.engine.generation.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.content.RiverDef;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.InvariantViolationException;

/**
 * Річки (GD §3.5) — стік води суходолом до моря чи озера.
 *
 * <ol>
 *   <li>Напрям стоку — пріоритетне заповнення від берегів: спершу комірки суходолу біля води (стікають у сусіднє море,
 *       а якщо його немає — в озеро), далі щоразу найнижча з рівнем {@code max(висота, рівень сусіда, з якого до неї
 *       дійшли)} стікає в цього сусіда. Так кожна комірка має шлях до води, а западини переливаються через найнижчий
 *       край, не утворюючи нових озер. При рівності рівнів — хто раніше потрапив у чергу: рівниною вода йде
 *       найкоротшим шляхом.
 *   <li>Стік — волога комірки плюс стік усіх комірок, що стікають у неї.
 *   <li>Річка — комірка зі стоком від {@link RiverDef#minFlow()}. Стік донизу лише росте, тож річка тече до води
 *       нерозривно, а притоки зливаються; гирло впадає в море або в озеро — далі вода з озера не тече.
 *   <li>Річкова система — усі річкові комірки одного гирла; система з менш ніж {@link RiverDef#minCells()} комірок —
 *       струмок біля берега, не річка.
 * </ol>
 *
 * <p>Ні коліс, ні кидків: річки — наслідок рельєфу й клімату.
 */
public final class RiverGenerator {

    private RiverGenerator() {}

    /**
     * @param grid сітка карти
     * @param relief висота суходолу тієї самої карти
     * @param climate волога суходолу тієї самої карти
     * @param sea водойми тієї самої карти
     */
    public static RiverMap generate(
            ContentPack content, MapGrid grid, ReliefMap relief, ClimateMap climate, SeaMap sea) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(relief, "relief");
        Objects.requireNonNull(climate, "climate");
        Objects.requireNonNull(sea, "sea");
        int cellCount = grid.cells().size();
        Checks.inRange("cells", sea.cellBodies().size(), cellCount, cellCount);
        int land = (int)
                sea.cellBodies().stream().filter(body -> body == SeaMap.NONE).count();
        Checks.inRange("relief", relief.heights().size(), land, land);
        Checks.inRange("climate", climate.moistures().size(), land, land);
        RiverDef def = content.map().rivers();

        int[] next = new int[cellCount];
        Arrays.fill(next, SeaMap.NONE);
        int[] order = drainage(grid, relief, sea, next);
        if (order.length != land) {
            throw new InvariantViolationException(ErrorDetails.of("field", "downstream", "value", land - order.length));
        }

        // Черга — від води вгору, тож проходом навспак кожна комірка віддає стік униз уже повним.
        int[] flow = new int[cellCount];
        for (int i = order.length - 1; i >= 0; i--) {
            int cell = order[i];
            flow[cell] += climate.moistures().get(cell);
            if (!sea.isWater(next[cell])) {
                flow[next[cell]] += flow[cell];
            }
        }

        TreeMap<Integer, Integer> downstream = new TreeMap<>();
        TreeMap<Integer, Integer> flows = new TreeMap<>();
        // Гирло → усі комірки зі стоком від порогу, що стікають до нього. Від води вгору: комірка нижче за течією вже
        // знає своє гирло.
        int[] mouthOf = new int[cellCount];
        TreeMap<Integer, TreeSet<Integer>> systems = new TreeMap<>();
        for (int cell : order) {
            downstream.put(cell, next[cell]);
            flows.put(cell, flow[cell]);
            if (!def.enoughFlow(flow[cell])) {
                continue;
            }
            mouthOf[cell] = sea.isWater(next[cell]) ? cell : mouthOf[next[cell]];
            systems.computeIfAbsent(mouthOf[cell], mouth -> new TreeSet<>()).add(cell);
        }
        List<River> rivers = new ArrayList<>();
        TreeMap<Integer, Integer> cellRivers = new TreeMap<>();
        for (Map.Entry<Integer, TreeSet<Integer>> system : systems.entrySet()) {
            if (system.getValue().size() < def.minCells()) {
                continue;
            }
            int mouth = system.getKey();
            for (int cell : system.getValue()) {
                cellRivers.put(cell, rivers.size());
            }
            rivers.add(new River(mouth, next[mouth], List.copyOf(system.getValue())));
        }
        return new RiverMap(downstream, flows, rivers, cellRivers);
    }

    /**
     * Пріоритетне заповнення від берегів: заповнює {@code next} — куди стікає кожна досягнута комірка суходолу.
     *
     * @return комірки суходолу в порядку заповнення: кожна — після тієї, куди стікає
     */
    static int[] drainage(MapGrid grid, ReliefMap relief, SeaMap sea, int[] next) {
        int cellCount = grid.cells().size();
        int[] level = new int[cellCount];
        List<Integer> queued = new ArrayList<>();
        // Ключ — рівень у старших бітах, порядок потрапляння в чергу в молодших.
        TreeSet<Long> queue = new TreeSet<>();
        for (int cell = 0; cell < cellCount; cell++) {
            if (sea.isWater(cell)) {
                continue;
            }
            int outlet = outlet(grid.cells().get(cell), sea);
            if (outlet != SeaMap.NONE) {
                next[cell] = outlet;
                level[cell] = relief.heights().get(cell);
                queue.add(key(level[cell], queued.size()));
                queued.add(cell);
            }
        }
        int[] order = new int[relief.heights().size()];
        int filled = 0;
        while (!queue.isEmpty()) {
            int cell = queued.get((int) (queue.pollFirst() & 0xFFFF_FFFFL));
            order[filled++] = cell;
            for (int neighbor : grid.cells().get(cell).neighbors()) {
                if (sea.isWater(neighbor) || next[neighbor] != SeaMap.NONE) {
                    continue;
                }
                next[neighbor] = cell;
                level[neighbor] = Math.max(relief.heights().get(neighbor), level[cell]);
                queue.add(key(level[neighbor], queued.size()));
                queued.add(neighbor);
            }
        }
        return Arrays.copyOf(order, filled);
    }

    /** Вода, у яку стікає берегова комірка: найменша сусідня комірка моря, інакше озера; {@link SeaMap#NONE} — не берег. */
    private static int outlet(MapCell cell, SeaMap sea) {
        int lake = SeaMap.NONE;
        for (int neighbor : cell.neighbors()) {
            if (sea.isSea(neighbor)) {
                return neighbor;
            }
            if (lake == SeaMap.NONE && sea.isLake(neighbor)) {
                lake = neighbor;
            }
        }
        return lake;
    }

    private static long key(int level, int sequence) {
        return ((long) level << 32) | sequence;
    }
}
