package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.generation.map.Continent;
import kolo.engine.generation.map.ContinentGenerator;
import kolo.engine.generation.map.ContinentMap;
import kolo.engine.generation.map.MapGrid;
import kolo.engine.generation.map.VoronoiGrid;
import kolo.engine.generation.map.WorldSize;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.map.WorldSizeWheel;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldLimits;
import org.junit.jupiter.api.Test;

/**
 * Материки на вбудованому контенті: для кожного шаблону й розмірів світу, які дає колесо, материків і провінцій
 * суходолу рівно стільки, скільки треба, материки зв'язні й розділені морем; найбільша карта з материками вкладається
 * в бюджет генерації.
 */
class BundledContinentsIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();

    @Test
    void everyTemplateGivesValidContinents() {
        int minProvinces = PACK.map().continents().minProvinces();
        for (MapTemplateDef template : PACK.map().templates()) {
            for (long seed = 0; seed < 8; seed++) {
                int players = 1 + (int) (seed % WorldLimits.MAX_PLAYERS);
                NpcShare share = NpcShare.values()[(int) (seed % NpcShare.values().length)];
                Rng world = Rng.of(seed);
                WorldSize size = WorldSizeWheel.generate(
                        world.fork("world_size"),
                        PACK,
                        new WorldSizeInput(players, share, Optional.of(template.id()), OptionalInt.empty()));
                MapGrid grid = VoronoiGrid.generate(
                        world.fork("grid"), PACK.map().grid(), template.gridCells(size.provinces()));

                ContinentMap map = ContinentGenerator.generate(world.fork("continents"), PACK, size, grid);

                assertValid(grid, map, size, minProvinces);
            }
        }
    }

    @Test
    void mostContinentsOnSmallestWorldFit() {
        // Найтісніший випадок: найбільше материків шаблону на найменшому світі.
        for (MapTemplateDef template : PACK.map().templates()) {
            int provinces = PACK.balance().world().provinces().min();
            int continents = template.continents().max();
            WorldSize size = new WorldSize(1, 1, template.id(), continents, 60, provinces, 500, List.of());
            for (long seed = 0; seed < 10; seed++) {
                MapGrid grid = VoronoiGrid.generate(Rng.of(seed), PACK.map().grid(), template.gridCells(provinces));

                ContinentMap map = ContinentGenerator.generate(Rng.of(seed), PACK, size, grid);

                assertValid(grid, map, size, PACK.map().continents().minProvinces());
            }
        }
    }

    @Test
    void largestMapWithContinentsFitsBudget() {
        MapTemplateDef template = PACK.map().templates().stream()
                .min((a, b) -> Integer.compare(a.landPct(), b.landPct()))
                .orElseThrow();
        int provinces = PACK.balance().world().provinces().max();
        WorldSize size = new WorldSize(
                WorldLimits.MAX_PLAYERS,
                WorldLimits.MAX_COUNTRIES - WorldLimits.MAX_PLAYERS,
                template.id(),
                template.continents().max(),
                100,
                provinces,
                500,
                List.of());
        // Прогрів JIT.
        ContinentGenerator.generate(
                Rng.of(0),
                PACK,
                size,
                VoronoiGrid.generate(Rng.of(0), PACK.map().grid(), largestGrid()));

        long start = System.nanoTime();
        MapGrid grid = VoronoiGrid.generate(Rng.of(1), PACK.map().grid(), largestGrid());
        ContinentMap map = ContinentGenerator.generate(Rng.of(1), PACK, size, grid);
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(map.landCells()).isEqualTo(provinces);
        // Бюджет усієї генерації карти — 2 с; сітка й материки — лише її частина, із запасом на повільний CI.
        assertThat(millis).isLessThan(2_000);
    }

    /**
     * Материків і провінцій суходолу рівно стільки, скільки дав розмір світу; кожен материк зв'язний, має щонайменше
     * {@code minProvinces} комірок і відділений від інших морем.
     */
    private static void assertValid(MapGrid grid, ContinentMap map, WorldSize size, int minProvinces) {
        assertThat(map.cellContinents()).hasSize(grid.cells().size());
        assertThat(map.continents()).hasSize(size.continents());
        assertThat(map.landCells()).isEqualTo(size.provinces());
        for (int c = 0; c < map.continents().size(); c++) {
            Continent continent = map.continents().get(c);
            assertThat(continent.cells()).hasSizeGreaterThanOrEqualTo(minProvinces);
            boolean[] seen = new boolean[grid.cells().size()];
            ArrayDeque<Integer> queue = new ArrayDeque<>(List.of(continent.seed()));
            seen[continent.seed()] = true;
            int reached = 0;
            while (!queue.isEmpty()) {
                int cell = queue.poll();
                reached++;
                for (int neighbor : grid.cells().get(cell).neighbors()) {
                    int owner = map.cellContinents().get(neighbor);
                    assertThat(owner).isIn(c, ContinentMap.SEA);
                    if (owner == c && !seen[neighbor]) {
                        seen[neighbor] = true;
                        queue.add(neighbor);
                    }
                }
            }
            assertThat(reached).isEqualTo(continent.cells().size());
        }
    }

    /** Комірок сітки найбільшого світу: найбільше провінцій при найменшій частці суходолу серед шаблонів. */
    static int largestGrid() {
        int provinces = PACK.balance().world().provinces().max();
        return PACK.map().templates().stream()
                .mapToInt(template -> template.gridCells(provinces))
                .max()
                .orElseThrow();
    }
}
