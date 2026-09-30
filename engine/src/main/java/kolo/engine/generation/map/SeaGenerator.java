package kolo.engine.generation.map;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.content.SeaDef;
import kolo.engine.error.Checks;
import kolo.engine.rng.Rng;
import kolo.engine.state.GridPoint;

/**
 * Море й озера (GD §3.5): ділить воду карти на водойми, а моря — на морські зони.
 *
 * <ol>
 *   <li>Водойми — зв'язні області комірок води; водойма з щонайменше {@code min_cells} комірок — море, менша — озеро.
 *   <li>Кожне море ділиться на {@link SeaDef#zones(int)} зон. Перше зерно — рівноймовірно серед комірок моря, кожне
 *       наступне — найдальша в кроках від уже обраних (при рівності — менший номер).
 *   <li>Ріст: щоразу росте найменша зона (при рівності — менший номер), беручи найстарішу вільну комірку зі своєї черги
 *       сусідів; зона без вільних сусідів зупиняється. Так зони зв'язні й приблизно рівні.
 *   <li>Вирівнювання ({@value #RELAXATION} рази): зерно кожної зони переходить у її комірку, найближчу до центру мас
 *       центрів її комірок, і ріст повторюється — зони стають округлішими.
 * </ol>
 *
 * <p>Коліс немає: розкладка зон — геометрія, а не ігровий результат.
 */
public final class SeaGenerator {

    /** Ітерацій вирівнювання зон; як релаксація Ллойда в сітці, дві вже прибирають витягнуті зони. */
    static final int RELAXATION = 2;

    private SeaGenerator() {}

    /**
     * @param rng окремий потік моря; всередині — {@code zones:<номер водойми>} на зерна кожного моря
     * @param grid сітка, з якої зроблено {@code continents}
     */
    public static SeaMap generate(Rng rng, ContentPack content, MapGrid grid, ContinentMap continents) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(continents, "continents");
        Checks.inRange(
                "cells",
                continents.cellContinents().size(),
                grid.cells().size(),
                grid.cells().size());
        SeaDef def = content.map().sea();
        int cellCount = grid.cells().size();

        Integer[] cellBodies = new Integer[cellCount];
        Arrays.fill(cellBodies, SeaMap.NONE);
        List<WaterBody> bodies = bodies(grid, continents, def, cellBodies);

        Integer[] cellZones = new Integer[cellCount];
        Arrays.fill(cellZones, SeaMap.NONE);
        List<Integer> zoneBodies = new ArrayList<>();
        List<Integer> zoneSeeds = new ArrayList<>();
        for (int b = 0; b < bodies.size(); b++) {
            WaterBody body = bodies.get(b);
            if (body.kind() != WaterKind.SEA) {
                continue;
            }
            Layout layout = zones(
                    rng.fork("zones:" + b), grid, body, def.zones(body.cells().size()), cellBodies, b);
            int[] seeds = layout.seeds();
            int first = zoneSeeds.size();
            for (int z = 0; z < seeds.length; z++) {
                zoneBodies.add(b);
                zoneSeeds.add(seeds[z]);
            }
            for (int cell : body.cells()) {
                cellZones[cell] = first + layout.owner()[cell];
            }
        }

        List<TreeSet<Integer>> zoneCells = new ArrayList<>();
        List<TreeSet<Integer>> zoneNeighbors = new ArrayList<>();
        for (int z = 0; z < zoneSeeds.size(); z++) {
            zoneCells.add(new TreeSet<>());
            zoneNeighbors.add(new TreeSet<>());
        }
        TreeMap<Integer, List<Integer>> coasts = new TreeMap<>();
        for (int cell = 0; cell < cellCount; cell++) {
            int zone = cellZones[cell];
            if (zone != SeaMap.NONE) {
                zoneCells.get(zone).add(cell);
                for (int neighbor : grid.cells().get(cell).neighbors()) {
                    if (cellZones[neighbor] != SeaMap.NONE && cellZones[neighbor] != zone) {
                        zoneNeighbors.get(zone).add(cellZones[neighbor]);
                    }
                }
            } else if (cellBodies[cell] == SeaMap.NONE) {
                TreeSet<Integer> near = new TreeSet<>();
                for (int neighbor : grid.cells().get(cell).neighbors()) {
                    if (cellZones[neighbor] != SeaMap.NONE) {
                        near.add(cellZones[neighbor]);
                    }
                }
                if (!near.isEmpty()) {
                    coasts.put(cell, List.copyOf(near));
                }
            }
        }
        List<SeaZone> zones = new ArrayList<>();
        for (int z = 0; z < zoneSeeds.size(); z++) {
            zones.add(new SeaZone(
                    zoneBodies.get(z),
                    zoneSeeds.get(z),
                    List.copyOf(zoneCells.get(z)),
                    List.copyOf(zoneNeighbors.get(z))));
        }
        return new SeaMap(bodies, Arrays.asList(cellBodies), zones, Arrays.asList(cellZones), coasts);
    }

    /** Зв'язні області води за зростанням найменшої комірки; заповнює {@code cellBodies}. */
    static List<WaterBody> bodies(MapGrid grid, ContinentMap continents, SeaDef def, Integer[] cellBodies) {
        List<WaterBody> bodies = new ArrayList<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int start = 0; start < cellBodies.length; start++) {
            if (continents.isLand(start) || cellBodies[start] != SeaMap.NONE) {
                continue;
            }
            int body = bodies.size();
            TreeSet<Integer> cells = new TreeSet<>();
            cellBodies[start] = body;
            queue.add(start);
            while (!queue.isEmpty()) {
                int cell = queue.poll();
                cells.add(cell);
                for (int neighbor : grid.cells().get(cell).neighbors()) {
                    if (!continents.isLand(neighbor) && cellBodies[neighbor] == SeaMap.NONE) {
                        cellBodies[neighbor] = body;
                        queue.add(neighbor);
                    }
                }
            }
            WaterKind kind = cells.size() >= def.minCells() ? WaterKind.SEA : WaterKind.LAKE;
            bodies.add(new WaterBody(kind, List.copyOf(cells)));
        }
        return bodies;
    }

    /**
     * Розкладка зон одного моря.
     *
     * @param seeds зерно кожної зони
     * @param owner для кожної комірки сітки — номер зони в межах моря; поза морем — {@link SeaMap#NONE}
     */
    record Layout(int[] seeds, int[] owner) {}

    /** Ділить море {@code bodyIndex} на {@code count} зон. */
    static Layout zones(Rng rng, MapGrid grid, WaterBody body, int count, Integer[] cellBodies, int bodyIndex) {
        List<Integer> cells = body.cells();
        int[] seeds = farthestSeeds(rng, grid, cells, count, cellBodies, bodyIndex);
        int[] owner = grow(grid, cells, seeds, cellBodies, bodyIndex);
        for (int i = 0; i < RELAXATION; i++) {
            seeds = centered(grid, cells, owner, seeds.length);
            owner = grow(grid, cells, seeds, cellBodies, bodyIndex);
        }
        return new Layout(seeds, owner);
    }

    /** Перше зерно — рівноймовірно, кожне наступне — найдальша в кроках від обраних комірка (при рівності — менша). */
    static int[] farthestSeeds(
            Rng rng, MapGrid grid, List<Integer> cells, int count, Integer[] cellBodies, int bodyIndex) {
        int[] distance = new int[cellBodies.length];
        Arrays.fill(distance, Integer.MAX_VALUE);
        int[] seeds = new int[count];
        seeds[0] = cells.get(rng.fork("seed").nextInt(cells.size()));
        for (int s = 0; ; s++) {
            relax(grid, seeds[s], distance, cellBodies, bodyIndex);
            if (s + 1 == count) {
                return seeds;
            }
            int best = -1;
            for (int cell : cells) {
                if (best < 0 || distance[cell] > distance[best]) {
                    best = cell;
                }
            }
            seeds[s + 1] = best;
        }
    }

    /** Пошук ушир від нового зерна: зменшує відстані, що стали ближчими. */
    private static void relax(MapGrid grid, int seed, int[] distance, Integer[] cellBodies, int bodyIndex) {
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        distance[seed] = 0;
        queue.add(seed);
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            for (int neighbor : grid.cells().get(cell).neighbors()) {
                if (cellBodies[neighbor] == bodyIndex && distance[neighbor] > distance[cell] + 1) {
                    distance[neighbor] = distance[cell] + 1;
                    queue.add(neighbor);
                }
            }
        }
    }

    /**
     * Рівномірний ріст від зерен: росте найменша зона, беручи найстарішу вільну комірку зі своєї черги.
     *
     * @return для кожної комірки сітки — номер зони або {@link SeaMap#NONE} поза морем
     */
    static int[] grow(MapGrid grid, List<Integer> cells, int[] seeds, Integer[] cellBodies, int bodyIndex) {
        int[] owner = new int[cellBodies.length];
        Arrays.fill(owner, SeaMap.NONE);
        int[] sizes = new int[seeds.length];
        List<ArrayDeque<Integer>> frontiers = new ArrayList<>();
        // Ключ — розмір у старших бітах, номер зони в молодших: найменша зона з меншим номером — перша.
        TreeSet<Long> active = new TreeSet<>();
        for (int z = 0; z < seeds.length; z++) {
            owner[seeds[z]] = z;
            sizes[z] = 1;
            frontiers.add(new ArrayDeque<>());
        }
        for (int z = 0; z < seeds.length; z++) {
            addFrontier(grid, seeds[z], frontiers.get(z), owner, cellBodies, bodyIndex);
            active.add(key(sizes[z], z));
        }
        int assigned = seeds.length;
        while (!active.isEmpty() && assigned < cells.size()) {
            int z = (int) (active.pollFirst() & 0xFFFF_FFFFL);
            ArrayDeque<Integer> frontier = frontiers.get(z);
            while (!frontier.isEmpty() && owner[frontier.peek()] != SeaMap.NONE) {
                frontier.poll();
            }
            if (frontier.isEmpty()) {
                continue;
            }
            int cell = frontier.poll();
            owner[cell] = z;
            sizes[z]++;
            assigned++;
            addFrontier(grid, cell, frontier, owner, cellBodies, bodyIndex);
            active.add(key(sizes[z], z));
        }
        return owner;
    }

    private static void addFrontier(
            MapGrid grid, int cell, ArrayDeque<Integer> frontier, int[] owner, Integer[] cellBodies, int bodyIndex) {
        for (int neighbor : grid.cells().get(cell).neighbors()) {
            if (cellBodies[neighbor] == bodyIndex && owner[neighbor] == SeaMap.NONE) {
                frontier.add(neighbor);
            }
        }
    }

    private static long key(int size, int zone) {
        return ((long) size << 32) | zone;
    }

    /** Нові зерна: для кожної зони — її комірка, найближча до центру мас центрів її комірок (при рівності — менша). */
    static int[] centered(MapGrid grid, List<Integer> cells, int[] owner, int count) {
        long[] sumX = new long[count];
        long[] sumY = new long[count];
        long[] sizes = new long[count];
        for (int cell : cells) {
            GridPoint site = grid.cells().get(cell).site();
            sumX[owner[cell]] += site.x();
            sumY[owner[cell]] += site.y();
            sizes[owner[cell]]++;
        }
        int[] seeds = new int[count];
        long[] best = new long[count];
        Arrays.fill(seeds, -1);
        for (int cell : cells) {
            int z = owner[cell];
            GridPoint site = grid.cells().get(cell).site();
            long dx = site.x() - Math.floorDiv(sumX[z], sizes[z]);
            long dy = site.y() - Math.floorDiv(sumY[z], sizes[z]);
            long distance = dx * dx + dy * dy;
            if (seeds[z] < 0 || distance < best[z]) {
                seeds[z] = cell;
                best[z] = distance;
            }
        }
        return seeds;
    }
}
