package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.List;
import java.util.TreeSet;
import kolo.engine.content.SeaDef;

/** Перевірки моря, спільні для тестів рушія. */
public final class SeaChecks {

    private SeaChecks() {}

    /**
     * Водойми — рівно вода карти, кожна зв'язна й максимальна, вид — за розміром; кожне море поділене на належну
     * кількість зв'язних зон, озера — без зон; сусідство зон і виходи до моря відповідають сітці.
     */
    public static void assertValid(MapGrid grid, ContinentMap continents, SeaMap sea, SeaDef def) {
        int cells = grid.cells().size();
        assertThat(sea.cellBodies()).hasSize(cells);
        for (int cell = 0; cell < cells; cell++) {
            assertThat(sea.isWater(cell)).isEqualTo(!continents.isLand(cell));
            if (sea.isWater(cell)) {
                // Максимальна: сусідня вода — у тій самій водоймі.
                for (int neighbor : grid.cells().get(cell).neighbors()) {
                    if (sea.isWater(neighbor)) {
                        assertThat(sea.cellBodies().get(neighbor))
                                .isEqualTo(sea.cellBodies().get(cell));
                    }
                }
            }
        }
        int zonesSeen = 0;
        for (int b = 0; b < sea.bodies().size(); b++) {
            WaterBody body = sea.bodies().get(b);
            assertConnected(grid, body.cells());
            if (b > 0) {
                assertThat(body.cells().getFirst())
                        .isGreaterThan(sea.bodies().get(b - 1).cells().getFirst());
            }
            WaterKind expected = body.cells().size() >= def.minCells() ? WaterKind.SEA : WaterKind.LAKE;
            assertThat(body.kind()).isEqualTo(expected);
            final int index = b;
            List<SeaZone> zones =
                    sea.zones().stream().filter(zone -> zone.body() == index).toList();
            if (body.kind() == WaterKind.LAKE) {
                assertThat(zones).isEmpty();
                continue;
            }
            assertThat(zones).hasSize(def.zones(body.cells().size()));
            TreeSet<Integer> covered = new TreeSet<>();
            for (SeaZone zone : zones) {
                assertThat(sea.zones().indexOf(zone)).isEqualTo(zonesSeen++);
                assertConnected(grid, zone.cells());
                covered.addAll(zone.cells());
            }
            assertThat(covered).containsExactlyElementsOf(body.cells());
        }
        assertThat(zonesSeen).isEqualTo(sea.zones().size());

        for (int z = 0; z < sea.zones().size(); z++) {
            TreeSet<Integer> neighbors = new TreeSet<>();
            for (int cell : sea.zones().get(z).cells()) {
                for (int neighbor : grid.cells().get(cell).neighbors()) {
                    if (sea.isSea(neighbor) && sea.cellZones().get(neighbor) != z) {
                        neighbors.add(sea.cellZones().get(neighbor));
                    }
                }
            }
            assertThat(sea.zones().get(z).neighbors()).containsExactlyElementsOf(neighbors);
            for (int neighbor : neighbors) {
                assertThat(sea.zones().get(neighbor).neighbors()).contains(z);
            }
        }

        for (int cell = 0; cell < cells; cell++) {
            TreeSet<Integer> near = new TreeSet<>();
            if (continents.isLand(cell)) {
                for (int neighbor : grid.cells().get(cell).neighbors()) {
                    if (sea.isSea(neighbor)) {
                        near.add(sea.cellZones().get(neighbor));
                    }
                }
            }
            assertThat(sea.coastal(cell)).isEqualTo(!near.isEmpty());
            assertThat(sea.seaZones(cell)).containsExactlyElementsOf(near);
        }
    }

    private static void assertConnected(MapGrid grid, List<Integer> cells) {
        TreeSet<Integer> all = new TreeSet<>(cells);
        TreeSet<Integer> seen = new TreeSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(cells.getFirst());
        seen.add(cells.getFirst());
        while (!queue.isEmpty()) {
            for (int neighbor : grid.cells().get(queue.poll()).neighbors()) {
                if (all.contains(neighbor) && seen.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        assertThat(seen).isEqualTo(all);
    }
}
