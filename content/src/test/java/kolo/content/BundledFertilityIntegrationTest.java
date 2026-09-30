package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
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
import kolo.engine.generation.map.FertilityGenerator;
import kolo.engine.generation.map.FertilityMap;
import kolo.engine.generation.map.MapGrid;
import kolo.engine.generation.map.ReliefGenerator;
import kolo.engine.generation.map.ReliefMap;
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
import kolo.engine.state.Terrain;
import kolo.engine.state.WorldLimits;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Родючість на вбудованому контенті: для кожного шаблону середня родючість помірна, є і житниці, і безплідні землі;
 * помірний пояс родючіший за полярний і посушливий, рівнина — за гори й пустелю, провінції з річкою — за решту;
 * найбільша карта вкладається в бюджет.
 */
class BundledFertilityIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();

    @Test
    void everyTemplateGetsSensibleFertility() {
        for (MapTemplateDef template : PACK.map().templates()) {
            Stats all = new Stats();
            Stats river = new Stats();
            Stats dry = new Stats();
            EnumMap<Climate, Stats> climates = new EnumMap<>(Climate.class);
            EnumMap<Terrain, Stats> terrains = new EnumMap<>(Terrain.class);
            long rich = 0;
            long barren = 0;
            for (long seed = 0; seed < 8; seed++) {
                Map map = map(seed, template);

                FertilityMap fertility = FertilityGenerator.generate(PACK, map.climate(), map.rivers());

                for (int cell : fertility.fertilities().keySet()) {
                    int value = fertility.fertility(cell).orElseThrow();
                    all.add(value);
                    (map.rivers().hasRiver(cell) ? river : dry).add(value);
                    climates.computeIfAbsent(map.climate().climates().get(cell), c -> new Stats())
                            .add(value);
                    terrains.computeIfAbsent(map.climate().terrains().get(cell), t -> new Stats())
                            .add(value);
                    rich += value >= 70 ? 1 : 0;
                    barren += value < 20 ? 1 : 0;
                }
            }
            String id = template.id().value();
            // Середня земля — ні пустка, ні рай.
            assertThat(all.mean()).as(id).isBetween(30L, 60L);
            // Є і житниці, і безплідні краї.
            assertThat(rich * 100 / all.count).as(id).isBetween(5L, 40L);
            assertThat(barren * 100 / all.count).as(id).isBetween(5L, 40L);
            assertThat(river.mean()).as(id).isGreaterThan(dry.mean() + 10);
            assertThat(climates.get(Climate.TEMPERATE).mean())
                    .as(id)
                    .isGreaterThan(climates.get(Climate.POLAR).mean())
                    .isGreaterThan(climates.get(Climate.ARID).mean());
            assertThat(terrains.get(Terrain.PLAIN).mean())
                    .as(id)
                    .isGreaterThan(terrains.get(Terrain.MOUNTAINS).mean())
                    .isGreaterThan(terrains.get(Terrain.DESERT).mean())
                    .isGreaterThan(terrains.get(Terrain.TUNDRA).mean());
        }
    }

    @Test
    @Tag("budget")
    void largestMapWithFertilityFitsBudget() {
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
        RiverMap rivers = RiverGenerator.generate(PACK, grid, relief, climate, sea);
        Budget.Timed<FertilityMap> timed = Budget.best(() -> FertilityGenerator.generate(PACK, climate, rivers));

        assertThat(timed.result().fertilities()).hasSize(provinces);
        // Бюджет усієї генерації карти — 2 с; родючість — таблиця, мала його частина.
        assertThat(timed.millis()).isLessThan(100);
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
        RiverMap rivers = RiverGenerator.generate(PACK, grid, relief, climate, sea);
        return new Map(climate, rivers);
    }

    private record Map(ClimateMap climate, RiverMap rivers) {}

    private static final class Stats {
        private long count;
        private long sum;

        void add(int value) {
            count++;
            sum += value;
        }

        long mean() {
            return count == 0 ? 0 : sum / count;
        }
    }
}
