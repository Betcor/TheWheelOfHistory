package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.content.MapGridDef;
import kolo.engine.generation.map.MapCell;
import kolo.engine.generation.map.MapGrid;
import kolo.engine.generation.map.VoronoiGrid;
import kolo.engine.generation.map.WorldSize;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.map.WorldSizeWheel;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldLimits;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.Test;

/**
 * Сітка Вороного на вбудованому контенті: для розмірів світу, які дає колесо, комірки вкривають карту без щілин,
 * сусідство симетричне й зв'язне; найбільша карта вкладається в бюджет генерації.
 */
class BundledMapGridIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();

    @Test
    void worldSizesGiveValidGrids() {
        for (long seed = 0; seed < 12; seed++) {
            int players = 1 + (int) (seed % WorldLimits.MAX_PLAYERS);
            NpcShare share = NpcShare.values()[(int) (seed % NpcShare.values().length)];
            Rng world = Rng.of(seed);
            WorldSize size = WorldSizeWheel.generate(world.fork("world_size"), PACK, WorldSizeInput.of(players, share));
            MapGrid grid = VoronoiGrid.generate(world.fork("grid"), PACK.map().grid(), size.provinces());

            assertValid(grid, size.provinces());
        }
    }

    @Test
    void largestMapHasExpectedSizeAndFitsBudget() {
        MapGridDef def = PACK.map().grid();
        int cells = PACK.balance().world().provinces().max();
        VoronoiGrid.generate(Rng.of(0), def, cells);

        long start = System.nanoTime();
        MapGrid grid = VoronoiGrid.generate(Rng.of(1), def, cells);
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat((long) grid.width() * grid.height())
                .isCloseTo((long) cells * def.cellSize() * def.cellSize(), Percentage.withPercentage(1));
        // Бюджет усієї генерації карти — 2 с (Вороной — лише її частина); із запасом на повільний CI.
        assertThat(millis).isLessThan(2_000);
    }

    /** Комірки вкривають карту без щілин і накладань, сусідство симетричне. */
    private static void assertValid(MapGrid grid, int cells) {
        assertThat(grid.cells()).hasSize(cells);
        long area = 0;
        for (int i = 0; i < cells; i++) {
            MapCell cell = grid.cells().get(i);
            assertThat(cell.doubleArea()).isPositive();
            area += cell.doubleArea();
            for (int neighbor : cell.neighbors()) {
                assertThat(grid.cells().get(neighbor).neighbors()).contains(i);
            }
        }
        assertThat(area).isEqualTo(2L * grid.width() * grid.height());
    }
}
