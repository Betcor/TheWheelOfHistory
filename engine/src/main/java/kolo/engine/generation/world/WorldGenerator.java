package kolo.engine.generation.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.error.InvariantViolationException;
import kolo.engine.generation.country.CountryGenerationInput;
import kolo.engine.generation.country.CountryGenerator;
import kolo.engine.generation.country.StartCountry;
import kolo.engine.generation.country.StartPerson;
import kolo.engine.generation.map.MapGenerator;
import kolo.engine.generation.map.WorldMap;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.religion.HolyCenterWheel;
import kolo.engine.generation.religion.StartHolyCenters;
import kolo.engine.generation.religion.StartReligions;
import kolo.engine.generation.religion.WorldReligionsWheel;
import kolo.engine.rng.Rng;
import kolo.engine.state.PowerCorridor;

/**
 * Генерація цілого світу: карта ({@link MapGenerator}) → релігії світу ({@link WorldReligionsWheel}) → держави по
 * черзі від нульової ({@link CountryGenerator}) → святі центри ({@link HolyCenterWheel}).
 *
 * <p>Кожен крок — свій потік: {@value #MAP_STREAM}, {@value #RELIGIONS_STREAM}, {@code country:<номер>},
 * {@value #HOLY_CENTERS_STREAM}. Держава бачить повні назви держав і імена людей, зайняті попередніми, — вони
 * унікальні в світі. Святі центри — останніми, бо залежать від релігії кожної держави.
 */
public final class WorldGenerator {

    public static final String MAP_STREAM = "map";
    public static final String RELIGIONS_STREAM = "religions";
    public static final String COUNTRY_STREAM = "country:";
    public static final String HOLY_CENTERS_STREAM = "holy_centers";

    private WorldGenerator() {}

    /**
     * @param rng потік світу — зазвичай {@code Rng.of(seed світу)}
     * @param input розмір світу від хоста
     * @throws kolo.engine.error.ValidationException якщо вхід розміру світу невалідний (див. {@link MapGenerator})
     * @throws InvariantViolationException якщо генерація карти, релігій чи держави порушила інваріант
     */
    public static StartWorld generate(Rng rng, ContentPack content, WorldSizeInput input) {
        return generate(rng, content, input, PowerCorridor.DEFAULT);
    }

    /**
     * Те саме з коридором бюджету сили, який задав хост (GD §3.4, §4.11).
     *
     * @throws kolo.engine.error.ValidationException якщо вхід розміру світу невалідний (див. {@link MapGenerator})
     * @throws InvariantViolationException якщо генерація карти, релігій чи держави порушила інваріант
     */
    public static StartWorld generate(Rng rng, ContentPack content, WorldSizeInput input, PowerCorridor corridor) {
        Objects.requireNonNull(rng, "rng");
        return generate(rng, content, MapGenerator.generate(rng.fork(MAP_STREAM), content, input), corridor);
    }

    /**
     * Світ на готовій карті: усе, крім карти. З картою з потоку {@value #MAP_STREAM} того самого {@code rng} результат
     * той самий, що й у {@link #generate(Rng, ContentPack, WorldSizeInput)}; так розмір світу можна дізнатися до
     * генерації держав.
     *
     * @param map карта, згенерована з цим самим контентом
     * @throws InvariantViolationException якщо генерація релігій чи держави порушила інваріант
     */
    public static StartWorld generate(Rng rng, ContentPack content, WorldMap map) {
        return generate(rng, content, map, PowerCorridor.DEFAULT);
    }

    /**
     * Світ на готовій карті з коридором бюджету сили, який задав хост.
     *
     * @throws InvariantViolationException якщо генерація релігій чи держави порушила інваріант
     */
    public static StartWorld generate(Rng rng, ContentPack content, WorldMap map, PowerCorridor corridor) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(corridor, "corridor");
        StartReligions religions = WorldReligionsWheel.generate(rng.fork(RELIGIONS_STREAM), content, map.countries());
        TreeSet<String> countryNames = new TreeSet<>();
        TreeSet<String> personNames = new TreeSet<>();
        List<StartCountry> countries = new ArrayList<>();
        for (int n = 0; n < map.countries(); n++) {
            CountryGenerationInput country =
                    new CountryGenerationInput(map, n, religions.religions(), corridor, countryNames, personNames);
            StartCountry generated = CountryGenerator.generate(rng.fork(COUNTRY_STREAM + n), content, country);
            countryNames.add(generated.name().name().fullName().nominative());
            for (StartPerson person : generated.people().people()) {
                personNames.add(person.name().fullName().nominative());
            }
            countries.add(generated);
        }
        StartHolyCenters holyCenters = HolyCenterWheel.generate(
                rng.fork(HOLY_CENTERS_STREAM),
                content,
                map,
                religions.religions().size(),
                countries.stream().map(country -> country.religion().religion()).toList());
        return new StartWorld(map, religions, countries, holyCenters);
    }
}
