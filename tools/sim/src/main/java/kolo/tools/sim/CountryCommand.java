package kolo.tools.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import kolo.content.loader.ContentLoader;
import kolo.content.loader.ContentSource;
import kolo.engine.content.ContentPack;
import kolo.engine.generation.country.CountryGenerationInput;
import kolo.engine.generation.country.CountryGenerator;
import kolo.engine.generation.country.StartCountry;
import kolo.engine.generation.country.StartPerson;
import kolo.engine.generation.map.MapGenerator;
import kolo.engine.generation.map.WorldMap;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.religion.StartReligions;
import kolo.engine.generation.religion.WorldReligionsWheel;
import kolo.engine.rng.Rng;

/**
 * Команда {@code country}: генерує світ — карту ({@link MapGenerator}), релігії й усі держави ланцюжком коліс
 * ({@link CountryGenerator}) — і друкує картку однієї держави.
 *
 * <p>Усе — від seed-а світу: карта з потоку {@value #MAP_STREAM}, релігії — з {@value #RELIGIONS_STREAM}, держава з
 * номером {@code n} — з {@code country:<n>}. Держави генеруються по черзі від нульової, кожна бачить зайняті
 * попередніми назви й імена; так картка може назвати сусідів і сусіда передісторії.
 */
final class CountryCommand {

    static final String MAP_STREAM = "map";
    static final String RELIGIONS_STREAM = "religions";
    static final String COUNTRY_STREAM = "country:";

    private CountryCommand() {}

    /**
     * Згенерований світ.
     *
     * @param countries усі держави світу за номером
     * @param number номер держави, чию картку друкувати
     */
    record Result(
            ContentPack content, WorldMap map, StartReligions religions, List<StartCountry> countries, int number) {

        Result {
            countries = List.copyOf(countries);
        }

        StartCountry country() {
            return countries.get(number);
        }
    }

    /**
     * @throws UsageException якщо держави з таким номером у світі немає
     * @throws kolo.engine.error.GameException якщо контент невалідний або генерація порушила інваріант
     */
    static Result run(CountryOptions options) {
        ContentPack content = options.content()
                .map(dir -> ContentLoader.load(ContentSource.directory(dir)))
                .orElseGet(ContentLoader::loadBundled);
        return generate(content, options);
    }

    static Result generate(ContentPack content, CountryOptions options) {
        Rng world = Rng.of(options.seed());
        WorldMap map = MapGenerator.generate(
                world.fork(MAP_STREAM), content, WorldSizeInput.of(options.players(), options.npcShare()));
        if (options.country() >= map.countries()) {
            throw new UsageException(
                    "error.usage.no_such_country", options.country(), map.countries(), map.countries() - 1);
        }
        StartReligions religions = WorldReligionsWheel.generate(world.fork(RELIGIONS_STREAM), content, map.countries());
        TreeSet<String> countryNames = new TreeSet<>();
        TreeSet<String> personNames = new TreeSet<>();
        List<StartCountry> countries = new ArrayList<>();
        for (int n = 0; n < map.countries(); n++) {
            CountryGenerationInput input =
                    new CountryGenerationInput(map, n, religions.religions(), countryNames, personNames);
            StartCountry country = CountryGenerator.generate(world.fork(COUNTRY_STREAM + n), content, input);
            countryNames.add(country.name().name().fullName().nominative());
            for (StartPerson person : country.people().people()) {
                personNames.add(person.name().fullName().nominative());
            }
            countries.add(country);
        }
        return new Result(content, map, religions, countries, options.country());
    }

    static List<String> render(Result result, CountryOptions options) {
        return new CountryReport(result).lines(options.seed(), options.rolls());
    }
}
