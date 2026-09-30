package kolo.engine.generation.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.country.CountryGenerationInput;
import kolo.engine.generation.country.CountryGenerator;
import kolo.engine.generation.country.StartCountry;
import kolo.engine.generation.map.MapGenerator;
import kolo.engine.generation.map.WorldMap;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.name.TestNames;
import kolo.engine.generation.religion.HolyCenterWheel;
import kolo.engine.generation.religion.StartHolyCenters;
import kolo.engine.generation.religion.StartReligions;
import kolo.engine.generation.religion.WorldReligionsWheel;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import org.junit.jupiter.api.Test;

class WorldGeneratorTest {

    private static final ContentPack PACK = TestNames.PACK;
    private static final WorldSizeInput INPUT = WorldSizeInput.of(1, NpcShare.FEW);
    private static final long SEED = 1970;
    private static final StartWorld WORLD = WorldGenerator.generate(Rng.of(SEED), PACK, INPUT);

    @Test
    void worldIsMapThenReligionsThenCountriesThenHolyCenters() {
        Rng rng = Rng.of(SEED);
        WorldMap map = MapGenerator.generate(rng.fork(WorldGenerator.MAP_STREAM), PACK, INPUT);
        StartReligions religions =
                WorldReligionsWheel.generate(rng.fork(WorldGenerator.RELIGIONS_STREAM), PACK, map.countries());
        TreeSet<String> countryNames = new TreeSet<>();
        TreeSet<String> personNames = new TreeSet<>();
        List<StartCountry> countries = new ArrayList<>();
        for (int n = 0; n < map.countries(); n++) {
            StartCountry country = CountryGenerator.generate(
                    rng.fork(WorldGenerator.COUNTRY_STREAM + n),
                    PACK,
                    new CountryGenerationInput(map, n, religions.religions(), countryNames, personNames));
            countryNames.add(country.name().name().fullName().nominative());
            country.people()
                    .people()
                    .forEach(person -> personNames.add(person.name().fullName().nominative()));
            countries.add(country);
        }
        StartHolyCenters centers = HolyCenterWheel.generate(
                rng.fork(WorldGenerator.HOLY_CENTERS_STREAM),
                PACK,
                map,
                religions.religions().size(),
                countries.stream().map(country -> country.religion().religion()).toList());

        assertThat(WORLD).isEqualTo(new StartWorld(map, religions, countries, centers));
    }

    @Test
    void readyMapGivesSameWorld() {
        Rng rng = Rng.of(SEED);
        WorldMap map = MapGenerator.generate(rng.fork(WorldGenerator.MAP_STREAM), PACK, INPUT);

        assertThat(WorldGenerator.generate(rng, PACK, map)).isEqualTo(WORLD);
    }

    @Test
    void namesAreUniqueInWorld() {
        assertThat(WORLD.countries())
                .extracting(country -> country.name().name().fullName().nominative())
                .doesNotHaveDuplicates();
        assertThat(WORLD.countries().stream()
                        .flatMap(country -> country.people().people().stream())
                        .map(person -> person.name().fullName().nominative()))
                .doesNotHaveDuplicates();
    }

    @Test
    void followersAreCountriesOfThatReligion() {
        List<OptionalInt> chosen = WORLD.countryReligions();
        assertThat(chosen).hasSize(WORLD.countries().size());
        int religious = 0;
        for (int r = 0; r < WORLD.religions().religions().size(); r++) {
            for (int n : WORLD.followers(r)) {
                assertThat(chosen.get(n)).isEqualTo(OptionalInt.of(r));
            }
            religious += WORLD.followers(r).size();
        }
        assertThat(religious)
                .isEqualTo(chosen.stream().filter(OptionalInt::isPresent).count());
    }

    @Test
    void holyCenterBelongsToFollowerOrNobody() {
        for (int r = 0; r < WORLD.religions().religions().size(); r++) {
            int owner = WORLD.map().placement().country(WORLD.holyCenters().cell(r));
            if (WORLD.map().placement().isClaimed(WORLD.holyCenters().cell(r))) {
                assertThat(WORLD.followers(r)).contains(owner);
            }
        }
    }

    @Test
    void worldValidatesSizes() {
        List<StartCountry> fewer =
                WORLD.countries().subList(1, WORLD.countries().size());
        assertThatThrownBy(() -> new StartWorld(WORLD.map(), WORLD.religions(), fewer, WORLD.holyCenters()))
                .isInstanceOf(ValidationException.class);
        StartHolyCenters none = new StartHolyCenters(List.of());
        assertThatThrownBy(() -> new StartWorld(WORLD.map(), WORLD.religions(), WORLD.countries(), none))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> WORLD.country(WORLD.countries().size())).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> WORLD.followers(-1)).isInstanceOf(ValidationException.class);
    }
}
