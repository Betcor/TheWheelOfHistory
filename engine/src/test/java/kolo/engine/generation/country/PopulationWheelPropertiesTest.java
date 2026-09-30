package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kolo.engine.content.AreaLevelDef;
import kolo.engine.content.ContentPack;
import kolo.engine.content.PopulationDef;
import kolo.engine.content.PopulationLevelDef;
import kolo.engine.generation.map.PlacedCountry;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.Wheel;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

/** Властивості географії й колеса населення на довільних світах, seed і перевагах. */
class PopulationWheelPropertiesTest {

    private static final ContentPack PACK = TestPopulation.PACK;

    @Property(tries = 30)
    void territoryGivesConsistentGeographyAndPopulation(@ForAll @LongRange(min = 0, max = 1_000_000) long seed) {
        TestTerritory.World world = TestTerritory.world(PACK, seed);
        int worldFertility = PopulationWheel.worldFertility(world.fertility());
        for (int i = 0; i < world.placement().countries().size(); i++) {
            PlacedCountry country = world.placement().countries().get(i);
            StartGeography geography = world.geography(PACK, i);
            GeographyChecks.assertValid(
                    PACK, country.cells(), world.sea(), world.climate(), world.fertility(), geography);

            StartPopulation population = PopulationWheel.generate(
                    Rng.of(seed).fork("population:" + i),
                    PACK,
                    List.of(),
                    country.area(),
                    geography,
                    world.fertility());

            assertThat(population.provinces().keySet()).containsExactlyElementsOf(country.cells());
            assertProportional(PACK.map().population(), population, geography, world);
            AreaLevelDef area = PACK.map().placement().area(country.area()).orElseThrow();
            assertThat(population.rolls().getFirst().advantage())
                    .isEqualTo(Math.clamp(
                            Math.floorDiv((area.sharePct() - 100) * 20, 100) + (geography.fertility() - worldFertility),
                            -100,
                            100));
            assertThat(population)
                    .isEqualTo(PopulationWheel.generate(
                            Rng.of(seed).fork("population:" + i),
                            PACK,
                            List.of(),
                            country.area(),
                            geography,
                            world.fertility()));
        }
    }

    @Property
    void resultIsConsistentWithRollAndAdvantage(@ForAll long seed, @ForAll @IntRange(min = -150, max = 150) int event) {
        StartPopulation population = PopulationWheel.generate(
                Rng.of(seed),
                PACK,
                List.of(TestPopulation.modifier("a", event / 2), TestPopulation.modifier("b", event - event / 2)),
                TestPopulation.MEDIUM,
                TestPopulation.GEOGRAPHY,
                TestPopulation.FERTILITY);

        RollRecord roll = population.rolls().getFirst();
        assertThat(roll.advantage()).isEqualTo(Math.clamp(event + 2, -100, 100));
        assertThat(roll.sectors().stream().mapToInt(RolledSector::weightBp).sum())
                .isEqualTo(Wheel.TOTAL_BP);
        assertThat(roll.sectors().getFirst().weightBp()).isGreaterThanOrEqualTo(Wheel.MIN_CRITICAL_BP);
        assertThat(roll.sectors().getLast().weightBp()).isGreaterThanOrEqualTo(Wheel.MIN_CRITICAL_BP);
        PopulationLevelDef level =
                PACK.map().population().level(population.level()).orElseThrow();
        assertThat(roll.resultSectorId()).isEqualTo(level.id().value());
        assertThat(population.populationK()).isEqualTo(level.populationK());
    }

    /** Кожна провінція отримує пропорційну частку населення, округлену вниз або вгору. */
    private static void assertProportional(
            PopulationDef def, StartPopulation population, StartGeography geography, TestTerritory.World world) {
        long sum = 0;
        for (int cell : geography.provinces()) {
            sum += weight(def, geography, world, cell);
        }
        long total = 0;
        for (int cell : geography.provinces()) {
            long exact = (long) population.populationK() * weight(def, geography, world, cell);
            assertThat((long) population.provinces().get(cell)).isBetween(exact / sum, exact / sum + 1);
            total += population.provinces().get(cell);
        }
        assertThat(total).isEqualTo(population.populationK());
    }

    private static long weight(PopulationDef def, StartGeography geography, TestTerritory.World world, int cell) {
        return def.provinceBase()
                + world.fertility().fertility(cell).orElseThrow()
                + (world.sea().coastal(cell) ? def.coastBonus() : 0);
    }
}
