package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.AreaLevelId;
import kolo.engine.content.CoastLevelDef;
import kolo.engine.content.CoastLevelId;
import kolo.engine.content.ContentPack;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.content.PopulationLevelDef;
import kolo.engine.content.PopulationLevelId;
import kolo.engine.generation.country.Geography;
import kolo.engine.generation.country.PopulationWheel;
import kolo.engine.generation.country.StartGeography;
import kolo.engine.generation.country.StartPopulation;
import kolo.engine.generation.map.ClimateGenerator;
import kolo.engine.generation.map.ClimateMap;
import kolo.engine.generation.map.ContinentGenerator;
import kolo.engine.generation.map.ContinentMap;
import kolo.engine.generation.map.FertilityGenerator;
import kolo.engine.generation.map.FertilityMap;
import kolo.engine.generation.map.MapGrid;
import kolo.engine.generation.map.PlacedCountry;
import kolo.engine.generation.map.PlacementGenerator;
import kolo.engine.generation.map.PlacementMap;
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
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldLimits;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Географія й населення держав на вбудованому контенті: кожен рівень виходу до моря й населення трапляється, частки
 * в розумних межах, більша й родючіша держава в середньому багатолюдніша; розрахунок для найбільшого світу вкладається
 * в бюджет генерації.
 */
class BundledGeographyPopulationIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();
    private static final int SEEDS = 40;

    /** З якої різниці середньої родючості держави й світу держава вважається родючою чи пустою. */
    private static final int FERTILITY_GAP = 15;

    @Test
    void everyCountryGetsGeographyAndPopulation() {
        TreeMap<CoastLevelId, Integer> coasts = new TreeMap<>();
        TreeMap<String, Integer> tags = new TreeMap<>();
        TreeMap<PopulationLevelId, Integer> levels = new TreeMap<>();
        TreeMap<AreaLevelId, long[]> byArea = new TreeMap<>();
        long[] fertileVsBarren = new long[4];
        int countries = 0;
        int zeroProvinces = 0;
        for (MapTemplateDef template : PACK.map().templates()) {
            for (long seed = 0; seed < SEEDS; seed++) {
                World world = world(seed, template);
                for (int i = 0; i < world.placement().countries().size(); i++) {
                    PlacedCountry country = world.placement().countries().get(i);
                    StartGeography geography =
                            Geography.generate(PACK, country.cells(), world.sea(), world.climate(), world.fertility());
                    StartPopulation population = PopulationWheel.generate(
                            Rng.of(seed).fork("population:" + i),
                            PACK,
                            List.of(),
                            country.area(),
                            geography,
                            world.fertility());

                    countries++;
                    coasts.merge(geography.coast(), 1, Integer::sum);
                    geography.tags().forEach(tag -> tags.merge(tag, 1, Integer::sum));
                    levels.merge(population.level(), 1, Integer::sum);
                    // Середній номер рівня (×100), а не середнє населення: рідкісні найбільші рівні дають шум.
                    int index = PACK.map()
                            .population()
                            .levels()
                            .indexOf(PACK.map()
                                    .population()
                                    .level(population.level())
                                    .orElseThrow());
                    long[] sum = byArea.computeIfAbsent(country.area(), id -> new long[2]);
                    sum[0] += index * 100L;
                    sum[1]++;
                    int difference = geography.fertility() - world.meanFertility();
                    if (Math.abs(difference) >= FERTILITY_GAP) {
                        int half = difference > 0 ? 0 : 2;
                        fertileVsBarren[half] += index * 100L;
                        fertileVsBarren[half + 1]++;
                    }
                    zeroProvinces += population.provinces().containsValue(0) ? 1 : 0;
                    assertThat(population.provinces().keySet()).containsExactlyElementsOf(country.cells());
                }
            }
        }
        for (CoastLevelDef coast : PACK.map().geography().coast()) {
            assertThat(coasts).containsKey(coast.id());
        }
        for (PopulationLevelDef level : PACK.map().population().levels()) {
            assertThat(levels).containsKey(level.id());
        }
        // Частки держав — запропонований баланс (ADR 0034): берег у більшості, без моря ~15%, морських ~10%.
        assertThat(coasts.get(new CoastLevelId("landlocked")) * 100).isBetween(countries * 5, countries * 30);
        assertThat(coasts.get(new CoastLevelId("maritime")) * 100).isBetween(countries * 3, countries * 25);
        assertThat(tags.get("mountainous") * 100).isBetween(countries * 10, countries * 50);
        // Більша держава й родючіша за світ — у середньому на вищому рівні населення.
        assertThat(mean(byArea, "huge", "large")).isGreaterThan(mean(byArea, "tiny", "small") + 20);
        assertThat(fertileVsBarren[0] / fertileVsBarren[1]).isGreaterThan(fertileVsBarren[2] / fertileVsBarren[3]);
        // Порожні провінції — лише зрідка, у малолюдних держав з багатьма провінціями.
        assertThat(zeroProvinces * 100).isLessThan(countries * 5);
    }

    @Test
    @Tag("budget")
    void largestWorldGeographyAndPopulationFitBudget() {
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
        World world = world(Rng.of(1), size, template);

        Budget.Timed<Long> timed = Budget.best(() -> {
            long people = 0;
            for (int i = 0; i < world.placement().countries().size(); i++) {
                PlacedCountry country = world.placement().countries().get(i);
                StartGeography geography =
                        Geography.generate(PACK, country.cells(), world.sea(), world.climate(), world.fertility());
                people += PopulationWheel.generate(
                                Rng.of(i), PACK, List.of(), country.area(), geography, world.fertility())
                        .populationK();
            }
            return people;
        });

        assertThat(timed.result()).isPositive();
        // Бюджет усієї генерації карти — 2 с; географія й населення — лише її дрібна частина.
        assertThat(timed.millis()).isLessThan(100);
    }

    /** Середній номер рівня населення (×100) держав цих рівнів площі. */
    private static long mean(TreeMap<AreaLevelId, long[]> byArea, String... areas) {
        long sum = 0;
        long count = 0;
        for (String area : areas) {
            long[] values = byArea.get(new AreaLevelId(area));
            sum += values[0];
            count += values[1];
        }
        return sum / count;
    }

    private static World world(long seed, MapTemplateDef template) {
        Rng rng = Rng.of(seed);
        int players = 1 + (int) (seed % WorldLimits.MAX_PLAYERS);
        NpcShare share = NpcShare.values()[(int) (seed % NpcShare.values().length)];
        WorldSize size = WorldSizeWheel.generate(
                rng.fork("world_size"),
                PACK,
                new WorldSizeInput(players, share, Optional.of(template.id()), OptionalInt.empty()));
        return world(rng, size, template);
    }

    private static World world(Rng rng, WorldSize size, MapTemplateDef template) {
        MapGrid grid = VoronoiGrid.generate(rng.fork("grid"), PACK.map().grid(), template.gridCells(size.provinces()));
        ContinentMap continents = ContinentGenerator.generate(rng.fork("continents"), PACK, size, grid);
        ReliefMap relief = ReliefGenerator.generate(rng.fork("relief"), PACK, grid, continents);
        ClimateMap climate = ClimateGenerator.generate(rng.fork("climate"), PACK, grid, continents, relief);
        SeaMap sea = SeaGenerator.generate(rng.fork("sea"), PACK, grid, continents);
        RiverMap rivers = RiverGenerator.generate(PACK, grid, relief, climate, sea);
        FertilityMap fertility = FertilityGenerator.generate(PACK, climate, rivers);
        PlacementMap placement = PlacementGenerator.generate(rng.fork("placement"), PACK, size, grid, continents);
        return new World(climate, sea, fertility, placement);
    }

    record World(ClimateMap climate, SeaMap sea, FertilityMap fertility, PlacementMap placement) {

        int meanFertility() {
            long sum = 0;
            for (int value : fertility.fertilities().values()) {
                sum += value;
            }
            return (int) (sum / fertility.fertilities().size());
        }
    }
}
