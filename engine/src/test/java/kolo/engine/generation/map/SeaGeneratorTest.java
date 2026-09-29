package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.content.SeaDef;
import kolo.engine.content.TestMaps;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import org.junit.jupiter.api.Test;

class SeaGeneratorTest {

    private static final ContentPack PACK = TestNames.PACK;

    @Test
    void seaCoversWaterOnGeneratedWorlds() {
        for (long seed = 0; seed < 20; seed++) {
            ReliefGeneratorTest.World world = ReliefGeneratorTest.world(seed, 1 + (int) (seed % 5), 400);

            SeaMap sea = SeaGenerator.generate(Rng.of(seed), PACK, world.grid(), world.continents());

            SeaChecks.assertValid(world.grid(), world.continents(), sea, TestMaps.SEA);
            assertThat(sea.count(WaterKind.SEA)).isPositive();
            assertThat(sea.coasts()).isNotEmpty();
        }
    }

    @Test
    void smallEnclosedWaterIsLakeAndLargeIsSea() {
        MapGrid grid = grid(400);
        int lake = interiorCell(grid, Set.of());
        List<Integer> sea = interiorPatch(grid, lake, 6);
        ContinentMap continents = continents(grid, union(Set.of(lake), sea));

        // Озеро — менше 5 комірок, море — від 5: одинока комірка — озеро, пляма з 6 — море з однією зоною.
        SeaMap map = SeaGenerator.generate(Rng.of(1), PACK, grid, continents);

        SeaChecks.assertValid(grid, continents, map, TestMaps.SEA);
        assertThat(map.count(WaterKind.LAKE)).isEqualTo(1);
        assertThat(map.count(WaterKind.SEA)).isEqualTo(1);
        assertThat(map.isLake(lake)).isTrue();
        assertThat(map.zone(lake)).isEmpty();
        assertThat(map.zones()).hasSize(1);
        assertThat(map.zones().getFirst().cells()).containsExactlyElementsOf(new TreeSet<>(sea));
        for (int neighbor : grid.cells().get(lake).neighbors()) {
            // Берег озера — не вихід до моря, якщо поруч немає моря.
            if (!sea.contains(neighbor)
                    && grid.cells().get(neighbor).neighbors().stream().noneMatch(sea::contains)) {
                assertThat(map.coastal(neighbor)).isFalse();
            }
        }
        for (int cell : sea) {
            for (int neighbor : grid.cells().get(cell).neighbors()) {
                if (!sea.contains(neighbor) && neighbor != lake) {
                    assertThat(map.seaZones(neighbor)).containsExactly(0);
                }
            }
        }
    }

    @Test
    void thresholdTurnsEveryBodyIntoLakeOrSea() {
        ReliefGeneratorTest.World world = ReliefGeneratorTest.world(5, 3, 300);

        SeaMap lakes = SeaGenerator.generate(
                Rng.of(5), pack(new SeaDef(SeaDef.MAX_MIN_CELLS, 20)), world.grid(), world.continents());
        SeaMap seas = SeaGenerator.generate(Rng.of(5), pack(new SeaDef(1, 20)), world.grid(), world.continents());

        assertThat(lakes.count(WaterKind.SEA)).isZero();
        assertThat(lakes.zones()).isEmpty();
        assertThat(lakes.coasts()).isEmpty();
        assertThat(seas.count(WaterKind.LAKE)).isZero();
        assertThat(seas.bodies()).hasSameSizeAs(lakes.bodies());
    }

    @Test
    void zoneSizeSetsZoneCount() {
        ReliefGeneratorTest.World world = ReliefGeneratorTest.world(9, 2, 300);

        SeaMap single = SeaGenerator.generate(Rng.of(9), pack(new SeaDef(1, 1)), world.grid(), world.continents());
        SeaMap whole = SeaGenerator.generate(
                Rng.of(9), pack(new SeaDef(1, SeaDef.MAX_ZONE_CELLS)), world.grid(), world.continents());

        SeaChecks.assertValid(world.grid(), world.continents(), single, new SeaDef(1, 1));
        assertThat(single.zones()).allSatisfy(zone -> assertThat(zone.cells()).containsExactly(zone.seed()));
        assertThat(whole.zones()).hasSize(whole.bodies().size());
    }

    @Test
    void zonesAreRoughlyEven() {
        ReliefGeneratorTest.World world = ReliefGeneratorTest.world(3, 1, 600);

        SeaMap sea = SeaGenerator.generate(Rng.of(3), PACK, world.grid(), world.continents());

        WaterBody ocean = sea.bodies().stream()
                .max((a, b) -> Integer.compare(a.cells().size(), b.cells().size()))
                .orElseThrow();
        int oceanIndex = sea.bodies().indexOf(ocean);
        List<Integer> sizes = sea.zones().stream()
                .filter(zone -> zone.body() == oceanIndex)
                .map(zone -> zone.cells().size())
                .toList();
        int average = ocean.cells().size() / sizes.size();
        assertThat(sizes).hasSizeGreaterThan(10);
        // Зона, затиснута в затоці, буває меншою, але жодна не розростається вдвічі понад середнє.
        assertThat(sizes).allSatisfy(size -> assertThat(size).isBetween(average / 4, average * 2));
    }

    @Test
    void farthestSeedsSpreadAcrossSea() {
        MapGrid grid = grid(200);
        Integer[] bodies = new Integer[grid.cells().size()];
        List<Integer> all = new ArrayList<>();
        for (int cell = 0; cell < bodies.length; cell++) {
            bodies[cell] = 0;
            all.add(cell);
        }

        int[] seeds = SeaGenerator.farthestSeeds(Rng.of(4), grid, all, 5, bodies, 0);

        assertThat(new TreeSet<>(List.of(seeds[0], seeds[1], seeds[2], seeds[3], seeds[4])))
                .hasSize(5);
        int[] owner = SeaGenerator.grow(grid, all, seeds, bodies, 0);
        int[] sizes = new int[5];
        for (int cell : all) {
            sizes[owner[cell]]++;
        }
        // Рівномірний ріст: зони різняться щонайбільше на кілька комірок, окрім затиснутих.
        assertThat(Arrays.stream(sizes).boxed().toList())
                .allSatisfy(size -> assertThat(size).isBetween(20, 60));
    }

    @Test
    void mismatchedGridIsRejected() {
        ReliefGeneratorTest.World world = ReliefGeneratorTest.world(1, 1, 100);

        assertThatThrownBy(() -> SeaGenerator.generate(Rng.of(1), PACK, grid(50), world.continents()))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
    }

    private static ContentPack pack(SeaDef sea) {
        return TestNames.pack(TestMaps.content(sea), TestMaps.BALANCE);
    }

    private static MapGrid grid(int cells) {
        return VoronoiGrid.generate(Rng.of(cells), TestMaps.GRID, cells);
    }

    /** Комірка не на краю, не з {@code taken} і не поруч з ними. */
    private static int interiorCell(MapGrid grid, Set<Integer> taken) {
        for (int cell = 0; cell < grid.cells().size(); cell++) {
            MapCell candidate = grid.cells().get(cell);
            if (!candidate.edge()
                    && !taken.contains(cell)
                    && candidate.neighbors().stream().noneMatch(taken::contains)) {
                return cell;
            }
        }
        throw new AssertionError("немає внутрішньої комірки");
    }

    /** Зв'язна пляма з {@code size} внутрішніх комірок, що не торкається {@code away} і далеко від краю. */
    private static List<Integer> interiorPatch(MapGrid grid, int away, int size) {
        TreeSet<Integer> blocked = new TreeSet<>(grid.cells().get(away).neighbors());
        blocked.add(away);
        for (int start = grid.cells().size() - 1; start >= 0; start--) {
            List<Integer> patch = new ArrayList<>();
            TreeSet<Integer> near = new TreeSet<>();
            patch.add(start);
            for (int i = 0; i < patch.size() && patch.size() < size; i++) {
                for (int neighbor : grid.cells().get(patch.get(i)).neighbors()) {
                    if (patch.size() < size && !patch.contains(neighbor)) {
                        patch.add(neighbor);
                    }
                }
            }
            for (int cell : patch) {
                near.add(cell);
                near.addAll(grid.cells().get(cell).neighbors());
            }
            boolean inside = patch.size() == size
                    && near.stream().noneMatch(blocked::contains)
                    && near.stream().noneMatch(cell -> grid.cells().get(cell).edge());
            if (inside) {
                return patch;
            }
        }
        throw new AssertionError("немає внутрішньої плями");
    }

    private static Set<Integer> union(Set<Integer> a, List<Integer> b) {
        TreeSet<Integer> all = new TreeSet<>(a);
        all.addAll(b);
        return all;
    }

    /** Один «материк» з усього, крім {@code water}. */
    private static ContinentMap continents(MapGrid grid, Set<Integer> water) {
        List<Integer> land = new ArrayList<>();
        List<Integer> owners = new ArrayList<>();
        for (int cell = 0; cell < grid.cells().size(); cell++) {
            boolean isWater = water.contains(cell);
            owners.add(isWater ? ContinentMap.SEA : 0);
            if (!isWater) {
                land.add(cell);
            }
        }
        return new ContinentMap(List.of(new Continent(land.getFirst(), 1, land)), owners, List.of());
    }
}
