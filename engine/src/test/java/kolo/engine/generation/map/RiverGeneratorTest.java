package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kolo.engine.content.ContentPack;
import kolo.engine.content.RiverDef;
import kolo.engine.content.TestMaps;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.Relief;
import org.junit.jupiter.api.Test;

class RiverGeneratorTest {

    private static final ContentPack PACK = TestNames.PACK;

    @Test
    void riversDrainGeneratedWorlds() {
        for (long seed = 0; seed < 20; seed++) {
            Full world = full(seed, 1 + (int) (seed % 5), 400);

            RiverMap rivers = RiverGenerator.generate(PACK, world.grid(), world.relief(), world.climate(), world.sea());

            RiverChecks.assertValid(
                    world.grid(), world.relief(), world.climate(), world.sea(), rivers, TestMaps.RIVERS);
            assertThat(rivers.rivers()).isNotEmpty();
            // Річка — не всюди: більшість провінцій лише віддає воду.
            assertThat(rivers.cellRivers().size()).isLessThan(rivers.flows().size() / 2);
        }
    }

    @Test
    void waterRunsDownhillAndRiversEndInSeaOrLake() {
        Built built = built(null);

        RiverMap rivers = RiverGenerator.generate(PACK, built.grid(), built.relief(), built.climate(), built.sea());

        RiverChecks.assertValid(built.grid(), built.relief(), built.climate(), built.sea(), rivers, TestMaps.RIVERS);
        for (Map.Entry<Integer, Integer> entry : rivers.downstream().entrySet()) {
            int next = entry.getValue();
            if (!built.sea().isWater(next)) {
                assertThat(built.relief().heights().get(next))
                        .isLessThan(built.relief().heights().get(entry.getKey()));
            }
        }
        // Довкола озера — власний басейн: річки впадають і в озеро, і в море, а з озера далі не течуть.
        assertThat(rivers.rivers())
                .anySatisfy(river -> assertThat(river.outlet()).isEqualTo(built.lake()));
        assertThat(rivers.rivers())
                .anySatisfy(
                        river -> assertThat(built.sea().isSea(river.outlet())).isTrue());
        assertThat(rivers.downstream(built.lake())).isEmpty();
        assertThat(rivers.hasRiver(built.lake())).isFalse();
    }

    @Test
    void pitSpillsOverItsLowestEdge() {
        Built plain = built(null);
        int pit = plain.grid().cells().stream()
                .filter(cell -> !cell.edge())
                .map(cell -> plain.grid().cells().indexOf(cell))
                .filter(cell -> !plain.sea().isWater(cell)
                        && plain.grid().cells().get(cell).neighbors().stream().noneMatch(plain.sea()::isWater))
                .findFirst()
                .orElseThrow();
        Built built = built(pit);

        RiverMap rivers = RiverGenerator.generate(PACK, built.grid(), built.relief(), built.climate(), built.sea());

        RiverChecks.assertValid(built.grid(), built.relief(), built.climate(), built.sea(), rivers, TestMaps.RIVERS);
        // Западина не стає озером: вода переливається через сусіда, хоч він і вищий.
        int next = rivers.downstream(pit).orElseThrow();
        assertThat(built.relief().heights().get(next))
                .isGreaterThan(built.relief().heights().get(pit));
    }

    @Test
    void thresholdSetsHowManyProvincesHaveRivers() {
        Full world = full(11, 2, 400);

        RiverMap none = RiverGenerator.generate(
                pack(new RiverDef(RiverDef.MAX_MIN_FLOW, 1)),
                world.grid(),
                world.relief(),
                world.climate(),
                world.sea());
        RiverMap few = RiverGenerator.generate(
                pack(new RiverDef(1_000, 1)), world.grid(), world.relief(), world.climate(), world.sea());
        RiverMap many = RiverGenerator.generate(
                pack(new RiverDef(101, 1)), world.grid(), world.relief(), world.climate(), world.sea());

        assertThat(none.rivers()).isEmpty();
        assertThat(none.cellRivers()).isEmpty();
        assertThat(few.cellRivers()).isNotEmpty();
        assertThat(many.cellRivers().keySet()).containsAll(few.cellRivers().keySet());
        assertThat(many.cellRivers().size()).isGreaterThan(few.cellRivers().size());
        // Поріг не змінює стоку — лише де він стає річкою.
        assertThat(many.flows()).isEqualTo(few.flows());
        assertThat(many.downstream()).isEqualTo(none.downstream());
    }

    @Test
    void shortSystemsAreNotRivers() {
        Full world = full(12, 2, 400);

        RiverMap all = RiverGenerator.generate(
                pack(new RiverDef(300, 1)), world.grid(), world.relief(), world.climate(), world.sea());
        RiverMap longOnes = RiverGenerator.generate(
                pack(new RiverDef(300, 5)), world.grid(), world.relief(), world.climate(), world.sea());

        RiverChecks.assertValid(
                world.grid(), world.relief(), world.climate(), world.sea(), longOnes, new RiverDef(300, 5));
        assertThat(all.rivers()).anySatisfy(river -> assertThat(river.cells()).hasSizeLessThan(5));
        assertThat(longOnes.rivers()).isNotEmpty();
        assertThat(longOnes.rivers())
                .allSatisfy(river -> assertThat(river.cells()).hasSizeGreaterThanOrEqualTo(5));
        // Відсіюються цілі системи: довгі лишаються такими самими.
        assertThat(all.rivers()).containsAll(longOnes.rivers());
        assertThat(all.cellRivers().keySet()).containsAll(longOnes.cellRivers().keySet());
    }

    @Test
    void mismatchedMapsAreRejected() {
        Full world = full(1, 1, 100);
        Full other = full(2, 1, 200);

        assertThatThrownBy(
                        () -> RiverGenerator.generate(PACK, other.grid(), world.relief(), world.climate(), world.sea()))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
        assertThatThrownBy(
                        () -> RiverGenerator.generate(PACK, world.grid(), other.relief(), world.climate(), world.sea()))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
        assertThatThrownBy(
                        () -> RiverGenerator.generate(PACK, world.grid(), world.relief(), other.climate(), world.sea()))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
    }

    @Test
    void riverMapRejectsBrokenRivers() {
        // Комірки 0 → 1 → вода 2.
        Map<Integer, Integer> downstream = Map.of(0, 1, 1, 2);
        Map<Integer, Integer> flows = Map.of(0, 100, 1, 200);
        River river = new River(1, 2, List.of(0, 1));

        RiverMap valid = new RiverMap(
                new TreeMap<>(downstream), new TreeMap<>(flows), List.of(river), new TreeMap<>(Map.of(0, 0, 1, 0)));
        assertThat(valid.hasRiver(0)).isTrue();
        assertThat(valid.river(1)).hasValue(0);
        assertThat(valid.downstream(0)).hasValue(1);
        assertThat(valid.downstream(2)).isEmpty();

        assertInvalid(() ->
                new RiverMap(new TreeMap<>(downstream), new TreeMap<>(Map.of(0, 100)), List.of(), new TreeMap<>()));
        // Гирло не стікає у свою воду.
        assertInvalid(() -> new RiverMap(
                new TreeMap<>(downstream),
                new TreeMap<>(flows),
                List.of(new River(1, 3, List.of(0, 1))),
                new TreeMap<>(Map.of(0, 0, 1, 0))));
        // Річкова комірка без річки нижче за течією.
        assertInvalid(() -> new RiverMap(
                new TreeMap<>(downstream),
                new TreeMap<>(flows),
                List.of(new River(1, 2, List.of(1))),
                new TreeMap<>(Map.of(0, 0, 1, 0))));
        assertInvalid(() ->
                new RiverMap(new TreeMap<>(Map.of(0, 0)), new TreeMap<>(Map.of(0, 1)), List.of(), new TreeMap<>()));
        assertInvalid(() -> new River(1, 2, List.of(0)));
        assertInvalid(() -> new River(1, 0, List.of(0, 1)));
        assertInvalid(() -> new River(1, 2, List.of(1, 0)));
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(ValidationException.class);
    }

    private static ContentPack pack(RiverDef rivers) {
        return TestNames.pack(TestMaps.content(rivers), TestMaps.BALANCE);
    }

    static Full full(long seed, int continents, int provinces) {
        ReliefGeneratorTest.World world = ReliefGeneratorTest.world(seed, continents, provinces);
        Rng rng = Rng.of(seed);
        ReliefMap relief = ReliefGenerator.generate(rng.fork("relief"), PACK, world.grid(), world.continents());
        ClimateMap climate =
                ClimateGenerator.generate(rng.fork("climate"), PACK, world.grid(), world.continents(), relief);
        SeaMap sea = SeaGenerator.generate(rng.fork("sea"), PACK, world.grid(), world.continents());
        return new Full(world.grid(), world.continents(), relief, climate, sea);
    }

    record Full(MapGrid grid, ContinentMap continents, ReliefMap relief, ClimateMap climate, SeaMap sea) {}

    /**
     * Карта з 400 комірок: край — море, комірка найближча до центру — озеро, висота — 5 за кожен крок від води, волога
     * всюди 100. Якщо {@code pit} задано, ця комірка — западина висотою 0 серед сусідів висотою 100.
     */
    private static Built built(Integer pit) {
        MapGrid grid = VoronoiGrid.generate(Rng.of(400), TestMaps.GRID, 400);
        int lake = 0;
        long best = Long.MAX_VALUE;
        for (int cell = 0; cell < grid.cells().size(); cell++) {
            GridPoint site = grid.cells().get(cell).site();
            long dx = 2L * site.x() - grid.width();
            long dy = 2L * site.y() - grid.height();
            if (dx * dx + dy * dy < best) {
                best = dx * dx + dy * dy;
                lake = cell;
            }
        }
        List<Integer> owners = new ArrayList<>();
        List<Integer> land = new ArrayList<>();
        for (int cell = 0; cell < grid.cells().size(); cell++) {
            boolean water = cell == lake || grid.cells().get(cell).edge();
            owners.add(water ? ContinentMap.SEA : 0);
            if (!water) {
                land.add(cell);
            }
        }
        ContinentMap continents = new ContinentMap(List.of(new Continent(land.getFirst(), 1, land)), owners, List.of());
        SeaMap sea = SeaGenerator.generate(Rng.of(1), PACK, grid, continents);

        int[] steps = new int[grid.cells().size()];
        Arrays.fill(steps, -1);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int cell = 0; cell < steps.length; cell++) {
            if (sea.isWater(cell)) {
                steps[cell] = 0;
                queue.add(cell);
            }
        }
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            for (int neighbor : grid.cells().get(cell).neighbors()) {
                if (steps[neighbor] < 0) {
                    steps[neighbor] = steps[cell] + 1;
                    queue.add(neighbor);
                }
            }
        }
        TreeMap<Integer, Integer> heights = new TreeMap<>();
        TreeMap<Integer, Relief> reliefs = new TreeMap<>();
        TreeMap<Integer, Integer> moistures = new TreeMap<>();
        for (int cell : land) {
            heights.put(cell, Math.min(100, steps[cell] * 5));
            reliefs.put(cell, Relief.PLAIN);
            moistures.put(cell, 100);
        }
        if (pit != null) {
            heights.put(pit, 0);
            for (int neighbor : grid.cells().get(pit).neighbors()) {
                heights.put(neighbor, 100);
            }
        }
        ReliefMap relief = new ReliefMap(heights, reliefs, List.of(), List.of());
        ClimateMap generated = ClimateGenerator.generate(Rng.of(1), PACK, grid, continents, relief);
        ClimateMap climate = new ClimateMap(
                generated.world(),
                generated.temperatures(),
                moistures,
                generated.climates(),
                generated.covers(),
                generated.terrains(),
                generated.roll());
        assertThat(sea.isLake(lake)).isTrue();
        return new Built(grid, relief, climate, sea, lake);
    }

    private record Built(MapGrid grid, ReliefMap relief, ClimateMap climate, SeaMap sea, int lake) {}
}
