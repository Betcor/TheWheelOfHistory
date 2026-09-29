package kolo.tools.sim;

import java.util.List;
import java.util.TreeSet;
import kolo.content.loader.ContentLoader;
import kolo.content.loader.ContentSource;
import kolo.engine.content.ContentPack;
import kolo.engine.content.ResourceId;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.country.CountryGenerationInput;
import kolo.engine.generation.country.CountryGenerator;
import kolo.engine.generation.country.StartCountry;
import kolo.engine.generation.religion.StartReligions;
import kolo.engine.generation.religion.WorldReligionsWheel;
import kolo.engine.rng.Rng;

/**
 * Команда {@code country}: генерує одну державу ланцюжком коліс ({@link CountryGenerator}) і друкує її картку.
 *
 * <p>Світу ще немає, тож держава — перша в уявному світі з {@link CountryOptions#countries()} держав: релігії світу
 * генеруються з потоку {@value #RELIGIONS_STREAM}, держава — з потоку {@value #COUNTRY_STREAM}, обидва від seed-а
 * світу. Так само світ будують інтеграційні тести рушія, тож CLI відтворює їхні держави.
 */
final class CountryCommand {

    static final String RELIGIONS_STREAM = "religions";
    static final String COUNTRY_STREAM = "country:0";

    private CountryCommand() {}

    /** Згенерована держава разом зі світом, у якому її згенеровано. */
    record Result(ContentPack content, StartReligions religions, StartCountry country) {}

    /**
     * @throws UsageException якщо ресурсу немає в контенті
     * @throws kolo.engine.error.GameException якщо контент невалідний або генерація порушила інваріант
     */
    static Result run(CountryOptions options) {
        ContentPack content = options.content()
                .map(dir -> ContentLoader.load(ContentSource.directory(dir)))
                .orElseGet(ContentLoader::loadBundled);
        return generate(content, options);
    }

    static Result generate(ContentPack content, CountryOptions options) {
        TreeSet<ResourceId> resources = resources(content, options);
        Rng world = Rng.of(options.seed());
        StartReligions religions =
                WorldReligionsWheel.generate(world.fork(RELIGIONS_STREAM), content, options.countries());
        CountryGenerationInput input = new CountryGenerationInput(
                religions.religions(), resources, new TreeSet<>(), new TreeSet<>(), new TreeSet<>());
        StartCountry country = CountryGenerator.generate(world.fork(COUNTRY_STREAM), content, input);
        return new Result(content, religions, country);
    }

    static List<String> render(Result result, CountryOptions options) {
        return new CountryReport(result.content(), result.religions(), result.country())
                .lines(options.seed(), options.countries(), options.rolls());
    }

    private static TreeSet<ResourceId> resources(ContentPack content, CountryOptions options) {
        TreeSet<ResourceId> resources = new TreeSet<>();
        for (String value : options.resources()) {
            ResourceId id;
            try {
                id = new ResourceId(value);
            } catch (ValidationException e) {
                throw new UsageException("error.usage.unknown_resource", value);
            }
            if (content.resource(id).isEmpty()) {
                throw new UsageException("error.usage.unknown_resource", value);
            }
            resources.add(id);
        }
        return resources;
    }
}
