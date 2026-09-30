package kolo.tools.sim;

import java.util.List;
import kolo.content.loader.ContentLoader;
import kolo.content.loader.ContentSource;
import kolo.engine.content.ContentPack;
import kolo.engine.generation.country.StartCountry;
import kolo.engine.generation.map.MapGenerator;
import kolo.engine.generation.map.WorldMap;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.religion.StartReligions;
import kolo.engine.generation.world.StartWorld;
import kolo.engine.generation.world.WorldGenerator;
import kolo.engine.rng.Rng;

/**
 * Команда {@code country}: генерує світ ({@link WorldGenerator}) — карту, релігії, усі держави ланцюжком коліс і
 * святі центри — і друкує картку однієї держави.
 *
 * <p>Усе — від seed-а світу, потоки — як у {@link WorldGenerator}. Номер держави перевіряється після карти, до
 * генерації держав: розмір світу відомий лише з карти.
 */
final class CountryCommand {

    private CountryCommand() {}

    /**
     * Згенерований світ.
     *
     * @param number номер держави, чию картку друкувати
     */
    record Result(ContentPack content, StartWorld world, int number) {

        WorldMap map() {
            return world.map();
        }

        StartReligions religions() {
            return world.religions();
        }

        List<StartCountry> countries() {
            return world.countries();
        }

        StartCountry country() {
            return world.country(number);
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
        Rng rng = Rng.of(options.seed());
        WorldMap map = MapGenerator.generate(
                rng.fork(WorldGenerator.MAP_STREAM), content, WorldSizeInput.of(options.players(), options.npcShare()));
        if (options.country() >= map.countries()) {
            throw new UsageException(
                    "error.usage.no_such_country", options.country(), map.countries(), map.countries() - 1);
        }
        StartWorld world = WorldGenerator.generate(rng, content, map);
        return new Result(content, world, options.country());
    }

    static List<String> render(Result result, CountryOptions options) {
        return new CountryReport(result).lines(options.seed(), options.rolls());
    }
}
