package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.generation.map.PlacedCountry;
import kolo.engine.rng.Rng;
import org.junit.jupiter.api.Test;

/** Основний сценарій: карта → розміщення → географія й населення кожної держави → мітки для передісторії. */
class PopulationWheelSmokeTest {

    @Test
    void placedCountriesGetGeographyAndPopulationWhichFeedBackstory() {
        ContentPack pack = TestPopulation.PACK;
        Rng rng = Rng.of(1970);
        TestTerritory.World world = TestTerritory.world(pack, 1970);

        long people = 0;
        for (int i = 0; i < world.placement().countries().size(); i++) {
            PlacedCountry country = world.placement().countries().get(i);
            StartGeography geography = world.geography(pack, i);
            StartPopulation population = PopulationWheel.generate(
                    rng.fork("population:" + i), pack, List.of(), country.area(), geography, world.fertility());
            TreeSet<String> tags = new TreeSet<>(country.tags());
            tags.addAll(geography.tags());
            tags.addAll(population.tags());
            Backstory backstory = BackstoryWheel.generate(rng.fork("backstory:" + i), pack, tags, List.of());

            assertThat(geography.provinces()).hasSize(country.provinces());
            assertThat(population.provinces()).hasSize(country.provinces());
            assertThat(backstory.tags()).containsAll(geography.tags()).containsAll(population.tags());
            people += population.populationK();
        }
        assertThat(people).isPositive();
    }
}
