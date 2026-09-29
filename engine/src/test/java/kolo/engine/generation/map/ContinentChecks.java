package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.List;

/** Перевірки материків, спільні для тестів рушія. */
public final class ContinentChecks {

    private ContinentChecks() {}

    /**
     * Материків і провінцій суходолу рівно стільки, скільки дав розмір світу; кожен материк зв'язний, має щонайменше
     * {@code minProvinces} комірок і відділений від інших морем.
     */
    public static void assertValid(MapGrid grid, ContinentMap map, WorldSize size, int minProvinces) {
        assertThat(map.cellContinents()).hasSize(grid.cells().size());
        assertThat(map.continents()).hasSize(size.continents());
        assertThat(map.landCells()).isEqualTo(size.provinces());
        assertThat(map.rolls()).hasSize(size.continents());
        for (int c = 0; c < map.continents().size(); c++) {
            Continent continent = map.continents().get(c);
            assertThat(continent.cells()).hasSizeGreaterThanOrEqualTo(minProvinces);
            assertThat(reachable(grid, map, continent.seed()))
                    .isEqualTo(continent.cells().size());
            for (int cell : continent.cells()) {
                for (int neighbor : grid.cells().get(cell).neighbors()) {
                    assertThat(map.cellContinents().get(neighbor)).isIn(c, ContinentMap.SEA);
                }
            }
        }
    }

    /** Скільки комірок того самого материка досяжно від {@code start} по суходолу. */
    private static int reachable(MapGrid grid, ContinentMap map, int start) {
        List<Integer> owners = map.cellContinents();
        int owner = owners.get(start);
        boolean[] seen = new boolean[owners.size()];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        seen[start] = true;
        int count = 0;
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            count++;
            for (int neighbor : grid.cells().get(cell).neighbors()) {
                if (!seen[neighbor] && owners.get(neighbor) == owner) {
                    seen[neighbor] = true;
                    queue.add(neighbor);
                }
            }
        }
        return count;
    }
}
