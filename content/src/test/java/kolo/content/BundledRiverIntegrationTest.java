package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.generation.map.ClimateGenerator;
import kolo.engine.generation.map.ClimateMap;
import kolo.engine.generation.map.ContinentGenerator;
import kolo.engine.generation.map.ContinentMap;
import kolo.engine.generation.map.MapGrid;
import kolo.engine.generation.map.ReliefGenerator;
import kolo.engine.generation.map.ReliefMap;
import kolo.engine.generation.map.River;
import kolo.engine.generation.map.RiverGenerator;
import kolo.engine.generation.map.RiverMap;
import kolo.engine.generation.map.SeaGenerator;
import kolo.engine.generation.map.SeaMap;
import kolo.engine.generation.map.VoronoiGrid;
import kolo.engine.generation.map.WorldSize;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.map.WorldSizeWheel;
import kolo.engine.rng.Rng;
import kolo.engine.state.Climate;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldLimits;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Річки на вбудованому контенті: для кожного шаблону річка є в помітній, але не переважній частині провінцій, великі
 * річкові системи мають притоки, а посушливий пояс бідніший на річки за вологі; найбільша карта з річками вкладається
 * в бюджет.
 */
class BundledRiverIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();

    @Test
    void everyTemplateGetsRiversOfSensibleDensity() {
        for (MapTemplateDef template : PACK.map().templates()) {
            long land = 0;
            long riverCells = 0;
            long rivers = 0;
            long largest = 0;
            long arid = 0;
            long aridRivers = 0;
            long wet = 0;
            long wetRivers = 0;
            for (long seed = 0; seed < 8; seed++) {
                Map map = map(seed, template);

                RiverMap riverMap = RiverGenerator.generate(PACK, map.grid(), map.relief(), map.climate(), map.sea());

                assertThat(riverMap.rivers()).as(template.id().value()).isNotEmpty();
                land += riverMap.flows().size();
                riverCells += riverMap.cellRivers().size();
                rivers += riverMap.rivers().size();
                for (River river : riverMap.rivers()) {
                    largest = Math.max(largest, river.cells().size());
                }
                for (int cell : riverMap.flows().keySet()) {
                    Climate climate = map.climate().climates().get(cell);
                    boolean river = riverMap.hasRiver(cell);
                    if (climate == Climate.ARID) {
                        arid++;
                        aridRivers += river ? 1 : 0;
                    } else if (climate == Climate.TEMPERATE || climate == Climate.TROPICAL) {
                        wet++;
                        wetRivers += river ? 1 : 0;
                    }
                }
            }
            String id = template.id().value();
            // Річка — у помітній частині провінцій, але не всюди.
            assertThat(riverCells * 100 / land).as(id).isBetween(5L, 25L);
            // Системи в середньому довші за поріг, а найбільша — справжня ріка з притоками.
            assertThat(riverCells / rivers)
                    .as(id)
                    .isGreaterThanOrEqualTo(PACK.map().rivers().minCells() + 1L);
            assertThat(largest).as(id).isGreaterThanOrEqualTo(15);
            // Посушливий пояс дає мало води: річок там не більше, ніж у вологих поясах.
            assertThat(aridRivers * wet).as(id).isLessThanOrEqualTo(wetRivers * arid);
        }
    }

    @Test
    @Tag("budget")
    void largestMapWithRiversFitsBudget() {
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
        MapGrid grid = VoronoiGrid.generate(Rng.of(1), PACK.map().grid(), template.gridCells(provinces));
        ContinentMap continents = ContinentGenerator.generate(Rng.of(1), PACK, size, grid);
        ReliefMap relief = ReliefGenerator.generate(Rng.of(1), PACK, grid, continents);
        ClimateMap climate = ClimateGenerator.generate(Rng.of(1), PACK, grid, continents, relief);
        SeaMap sea = SeaGenerator.generate(Rng.of(1), PACK, grid, continents);
        Budget.Timed<RiverMap> timed = Budget.best(() -> RiverGenerator.generate(PACK, grid, relief, climate, sea));

        assertThat(timed.result().flows()).hasSize(provinces);
        // Бюджет усієї генерації карти — 2 с; річки — мала її частина, із запасом на CI.
        assertThat(timed.millis()).isLessThan(300);
    }

    private static Map map(long seed, MapTemplateDef template) {
        int players = 1 + (int) (seed % WorldLimits.MAX_PLAYERS);
        NpcShare share = NpcShare.values()[(int) (seed % NpcShare.values().length)];
        Rng world = Rng.of(seed);
        WorldSize size = WorldSizeWheel.generate(
                world.fork("world_size"),
                PACK,
                new WorldSizeInput(players, share, Optional.of(template.id()), OptionalInt.empty()));
        MapGrid grid =
                VoronoiGrid.generate(world.fork("grid"), PACK.map().grid(), template.gridCells(size.provinces()));
        ContinentMap continents = ContinentGenerator.generate(world.fork("continents"), PACK, size, grid);
        ReliefMap relief = ReliefGenerator.generate(world.fork("relief"), PACK, grid, continents);
        ClimateMap climate = ClimateGenerator.generate(world.fork("climate"), PACK, grid, continents, relief);
        SeaMap sea = SeaGenerator.generate(world.fork("sea"), PACK, grid, continents);
        return new Map(grid, relief, climate, sea);
    }

    private record Map(MapGrid grid, ReliefMap relief, ClimateMap climate, SeaMap sea) {}
}
