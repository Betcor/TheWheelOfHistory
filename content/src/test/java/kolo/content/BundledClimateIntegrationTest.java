package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ClimateDef;
import kolo.engine.content.ContentPack;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.generation.map.ClimateGenerator;
import kolo.engine.generation.map.ClimateMap;
import kolo.engine.generation.map.ContinentGenerator;
import kolo.engine.generation.map.ContinentMap;
import kolo.engine.generation.map.MapGrid;
import kolo.engine.generation.map.ReliefGenerator;
import kolo.engine.generation.map.ReliefMap;
import kolo.engine.generation.map.VoronoiGrid;
import kolo.engine.generation.map.WorldSize;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.map.WorldSizeWheel;
import kolo.engine.rng.Rng;
import kolo.engine.state.Climate;
import kolo.engine.state.Cover;
import kolo.engine.state.NpcShare;
import kolo.engine.state.Relief;
import kolo.engine.state.Terrain;
import kolo.engine.state.WorldLimits;
import org.junit.jupiter.api.Test;

/**
 * Клімат на вбудованому контенті: для кожного шаблону клімат має рівно суходіл і відповідає порогам, трапляються всі
 * пояси й покриви, частки — у розумних межах; найбільша карта з кліматом вкладається в бюджет.
 */
class BundledClimateIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();

    @Test
    void everyTemplateGivesClimateWithSensibleShares() {
        ClimateDef def = PACK.map().climate();
        for (MapTemplateDef template : PACK.map().templates()) {
            long[] climates = new long[Climate.values().length];
            long[] terrains = new long[Terrain.values().length];
            long total = 0;
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
                ContinentMap continents = ContinentGenerator.generate(world.fork("continents"), PACK, size, grid);
                ReliefMap relief = ReliefGenerator.generate(world.fork("relief"), PACK, grid, continents);

                ClimateMap climate = ClimateGenerator.generate(world.fork("climate"), PACK, grid, continents, relief);

                assertThat(climate.terrains()).hasSize(size.provinces());
                for (int cell : climate.climates().keySet()) {
                    assertThat(continents.isLand(cell)).isTrue();
                    Climate zone = def.climate(
                            climate.temperatures().get(cell),
                            climate.moistures().get(cell));
                    assertThat(climate.climates().get(cell)).isEqualTo(zone);
                    Relief level = relief.reliefs().get(cell);
                    Optional<Cover> cover = def.cover(
                            zone,
                            level,
                            climate.moistures().get(cell),
                            relief.heights().get(cell));
                    assertThat(climate.terrains().get(cell)).isEqualTo(Terrain.of(level, cover));
                }
                for (Climate zone : Climate.values()) {
                    climates[zone.ordinal()] += climate.count(zone);
                }
                for (Terrain terrain : Terrain.values()) {
                    terrains[terrain.ordinal()] += climate.count(terrain);
                }
                total += climate.climates().size();
            }
            // На вбудованому контенті: полярний ~11–13%, бореальний ~16–22%, помірний ~24–38%, посушливий ~14–27%,
            // тропічний ~11–15%; ліс ~23–30%, пустеля ~6–17%, тундра ~7–11%, болото ~3–5%.
            for (Climate zone : Climate.values()) {
                assertThat(climates[zone.ordinal()] * 100 / total)
                        .as(zone.key())
                        .isBetween(5L, 50L);
            }
            assertThat(terrains[Terrain.FOREST.ordinal()] * 100 / total).isBetween(15L, 45L);
            assertThat(terrains[Terrain.DESERT.ordinal()] * 100 / total).isBetween(2L, 25L);
            assertThat(terrains[Terrain.TUNDRA.ordinal()] * 100 / total).isBetween(3L, 20L);
            assertThat(terrains[Terrain.SWAMP.ordinal()] * 100 / total).isBetween(1L, 12L);
            assertThat(terrains[Terrain.PLAIN.ordinal()] * 100 / total).isBetween(10L, 40L);
        }
    }

    @Test
    void largestMapWithClimateFitsBudget() {
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
        int cells = template.gridCells(provinces);
        // Прогрів JIT.
        MapGrid warm = VoronoiGrid.generate(Rng.of(0), PACK.map().grid(), cells);
        ContinentMap warmContinents = ContinentGenerator.generate(Rng.of(0), PACK, size, warm);
        ClimateGenerator.generate(
                Rng.of(0), PACK, warm, warmContinents, ReliefGenerator.generate(Rng.of(0), PACK, warm, warmContinents));

        long start = System.nanoTime();
        MapGrid grid = VoronoiGrid.generate(Rng.of(1), PACK.map().grid(), cells);
        ContinentMap continents = ContinentGenerator.generate(Rng.of(1), PACK, size, grid);
        ReliefMap relief = ReliefGenerator.generate(Rng.of(1), PACK, grid, continents);
        ClimateMap climate = ClimateGenerator.generate(Rng.of(1), PACK, grid, continents, relief);
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(climate.terrains()).hasSize(provinces);
        // Бюджет усієї генерації карти — 2 с; сітка, материки, рельєф і клімат — лише її частина, із запасом на CI.
        assertThat(millis).isLessThan(2_000);
    }
}
