package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.content.ResourceDef;
import kolo.engine.content.ResourceId;
import kolo.engine.generation.country.ResourceWheel;
import kolo.engine.generation.country.StartDeposit;
import kolo.engine.generation.country.StartResources;
import kolo.engine.generation.map.ClimateGenerator;
import kolo.engine.generation.map.ClimateMap;
import kolo.engine.generation.map.ContinentGenerator;
import kolo.engine.generation.map.ContinentMap;
import kolo.engine.generation.map.FertilityGenerator;
import kolo.engine.generation.map.FertilityMap;
import kolo.engine.generation.map.MapGrid;
import kolo.engine.generation.map.ReliefGenerator;
import kolo.engine.generation.map.ReliefMap;
import kolo.engine.generation.map.ResourceSuitabilityGenerator;
import kolo.engine.generation.map.ResourceSuitabilityMap;
import kolo.engine.generation.map.RiverGenerator;
import kolo.engine.generation.map.RiverMap;
import kolo.engine.generation.map.SeaGenerator;
import kolo.engine.generation.map.SeaMap;
import kolo.engine.generation.map.VoronoiGrid;
import kolo.engine.generation.map.WorldSize;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.map.WorldSizeWheel;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldLimits;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Родовища на вбудованому контенті. Держав на карті ще немає, тож «держава» тут — зв'язна пляма суходолу розміру з
 * балансу світу. Кожен ресурс досяжний і трапляється в помітної частки держав, жоден не домінує; родовища лежать
 * у придатних провінціях; найбільша карта вкладається в бюджет.
 */
class BundledResourcesIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();

    @Test
    void everyResourceAppearsInReasonableShareOfCountries() {
        TreeMap<ResourceId, Integer> countries = new TreeMap<>();
        int total = 0;
        for (MapTemplateDef template : PACK.map().templates()) {
            for (long seed = 0; seed < 6; seed++) {
                Map map = map(seed, template);
                ResourceSuitabilityMap suitability =
                        ResourceSuitabilityGenerator.generate(PACK, map.climate(), map.fertility());
                Rng rng = Rng.of(seed).fork("countries");
                for (int country = 0; country < 10; country++) {
                    Rng countryRng = rng.fork("country:" + country);
                    TreeSet<Integer> provinces = blob(countryRng.fork("blob"), map, suitability);

                    StartResources resources =
                            ResourceWheel.generate(countryRng.fork("resources"), PACK, suitability, provinces);

                    for (StartDeposit deposit : resources.deposits()) {
                        assertThat(provinces).contains(deposit.cell());
                        assertThat(suitability.suitability(deposit.cell(), deposit.resource()))
                                .isPositive();
                        countries.merge(deposit.resource(), 1, Integer::sum);
                    }
                    assertThat(resources.deposits()).isNotEmpty();
                    total++;
                }
            }
        }
        for (ResourceDef resource : PACK.resources().values()) {
            int share = countries.getOrDefault(resource.id(), 0) * 100 / total;
            // Кожен ресурс — не екзотика й не в кожної держави.
            assertThat(share).as(resource.id().value()).isBetween(8, 75);
        }
    }

    @Test
    @Tag("budget")
    void largestMapSuitabilityFitsBudget() {
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
        FertilityMap fertility = FertilityGenerator.generate(PACK, climate, rivers);
        Budget.Timed<ResourceSuitabilityMap> timed =
                Budget.best(() -> ResourceSuitabilityGenerator.generate(PACK, climate, fertility));

        assertThat(timed.result().suitabilities()).hasSize(provinces);
        // Бюджет усієї генерації карти — 2 с; придатність — таблиця, мала його частина.
        assertThat(timed.millis()).isLessThan(100);
    }

    /** Зв'язна пляма суходолу розміру «провінцій на державу» від випадкової комірки. */
    private static TreeSet<Integer> blob(Rng rng, Map map, ResourceSuitabilityMap suitability) {
        List<Integer> land = List.copyOf(suitability.suitabilities().keySet());
        int target = map.provincesPerCountry();
        int start = land.get(rng.nextInt(land.size()));
        TreeSet<Integer> blob = new TreeSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>(List.of(start));
        blob.add(start);
        while (!queue.isEmpty() && blob.size() < target) {
            int cell = queue.poll();
            for (int neighbor : map.grid().cells().get(cell).neighbors()) {
                if (blob.size() < target && suitability.isLand(neighbor) && blob.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return blob;
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
        FertilityMap fertility = FertilityGenerator.generate(PACK, climate, rivers);
        return new Map(grid, climate, fertility, size.provincesPerCountry());
    }

    private record Map(MapGrid grid, ClimateMap climate, FertilityMap fertility, int provincesPerCountry) {}
}
