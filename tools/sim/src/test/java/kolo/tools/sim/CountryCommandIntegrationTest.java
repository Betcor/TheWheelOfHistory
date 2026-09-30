package kolo.tools.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import kolo.content.loader.ContentLoader;
import kolo.content.loader.ContentSource;
import kolo.engine.content.ContentPack;
import kolo.engine.generation.country.CountryGenerationInput;
import kolo.engine.generation.country.CountryGenerator;
import kolo.engine.generation.country.StartCountry;
import kolo.engine.generation.map.MapGenerator;
import kolo.engine.generation.map.WorldMap;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.religion.StartReligion;
import kolo.engine.generation.religion.WorldReligionsWheel;
import kolo.engine.rng.Rng;
import kolo.engine.state.CountryId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Команда {@code country} на вбудованому контенті: відтворюваність, відповідність рушію, картка для будь-якого seed. */
class CountryCommandIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();
    private static final int SEEDS = 100;

    @Test
    void sameSeedGivesSameCard() {
        CountryOptions options = options(42, "--rolls");

        assertThat(card(options)).isEqualTo(card(options));
    }

    @Test
    void differentSeedsGiveDifferentCountries() {
        TreeSet<String> names = new TreeSet<>();
        for (long seed = 0; seed < 20; seed++) {
            names.add(CountryCommand.generate(PACK, options(seed))
                    .country()
                    .name()
                    .name()
                    .fullName()
                    .nominative());
        }

        assertThat(names).hasSizeGreaterThan(15);
    }

    @Test
    void generatesTheCountriesOfTheEngineWorld() {
        Rng world = Rng.of(7);
        WorldMap map = MapGenerator.generate(
                world.fork(CountryCommand.MAP_STREAM),
                PACK,
                WorldSizeInput.of(CountryOptions.DEFAULT_PLAYERS, CountryOptions.DEFAULT_NPC_SHARE));
        List<StartReligion> religions = WorldReligionsWheel.generate(
                        world.fork(CountryCommand.RELIGIONS_STREAM), PACK, map.countries())
                .religions();
        CountryGenerationInput input = CountryGenerationInput.of(map, 0, religions);
        StartCountry expected = CountryGenerator.generate(world.fork(CountryCommand.COUNTRY_STREAM + 0), PACK, input);

        CountryCommand.Result result = CountryCommand.generate(PACK, options(7));

        assertThat(result.map()).isEqualTo(map);
        assertThat(result.countries()).hasSize(map.countries());
        assertThat(result.country()).isEqualTo(expected);
        assertThat(card(options(7)))
                .anySatisfy(line -> assertThat(line)
                        .contains(expected.name().name().fullName().nominative()));
    }

    @Test
    void countriesOfOneWorldHaveUniqueNames() {
        CountryCommand.Result result = CountryCommand.generate(PACK, options(11, "--players", "8", "--npc", "many"));

        assertThat(result.countries())
                .extracting(country -> country.name().name().fullName().nominative())
                .doesNotHaveDuplicates();
        assertThat(result.countries().stream()
                        .flatMap(country -> country.people().people().stream())
                        .map(person -> person.name().fullName().nominative()))
                .doesNotHaveDuplicates();
    }

    @Test
    void everySeedRendersWithoutPlaceholders() {
        for (long seed = 0; seed < SEEDS; seed++) {
            CountryOptions options = options(seed, "--rolls", "--country", String.valueOf(seed % 3));
            CountryCommand.Result result = CountryCommand.generate(PACK, options);

            List<String> card = CountryCommand.render(result, options);

            assertThat(card).noneMatch(line -> line.contains("{") || line.contains("}"));
            long rollLines = card.stream().filter(line -> line.contains(" → ")).count();
            assertThat(rollLines).isEqualTo(result.country().rolls().size());
            result.country()
                    .backstory()
                    .entries()
                    .forEach(entry -> assertThat(card)
                            .anySatisfy(line -> assertThat(line).contains(String.valueOf(entry.year()))));
            result.country()
                    .people()
                    .people()
                    .forEach(person -> assertThat(card)
                            .anySatisfy(line -> assertThat(line)
                                    .contains(person.name().fullName().nominative())));
            result.map()
                    .neighbors(result.number())
                    .forEach(neighbor -> assertThat(card)
                            .anySatisfy(line -> assertThat(line)
                                    .contains(result.countries()
                                            .get(neighbor)
                                            .name()
                                            .name()
                                            .shortName()
                                            .nominative())));
        }
    }

    @Test
    void backstoryNamesTheNeighbor() {
        for (long seed = 0; seed < SEEDS; seed++) {
            CountryCommand.Result result = CountryCommand.generate(PACK, options(seed));
            Optional<CountryId> neighbor = result.country().backstory().neighbor();
            if (neighbor.isEmpty()) {
                continue;
            }
            StartCountry other = result.countries().get(CountryReport.numberOf(neighbor.orElseThrow()));
            assertThat(result.map().neighbors(result.number()))
                    .contains(CountryReport.numberOf(neighbor.orElseThrow()));
            // Шаблони згадують сусіда в різних відмінках, тож досить кореня короткої назви.
            String root = other.name().name().shortName().nominative();
            assertThat(String.join("\n", CountryCommand.render(result, options(seed))))
                    .contains(root.substring(0, Math.min(4, root.length())));
            return;
        }
        throw new AssertionError("жодна передісторія не згадала сусіда");
    }

    @Test
    void rejectsCountryMissingFromTheWorld() {
        assertThatThrownBy(() -> CountryCommand.generate(PACK, options(1, "--npc", "few", "--country", "39")))
                .isInstanceOfSatisfying(
                        UsageException.class, e -> assertThat(e.key()).isEqualTo("error.usage.no_such_country"));
    }

    @Test
    void loadsContentFromDirectory(@TempDir Path dir) throws IOException {
        ContentSource bundled = ContentSource.bundled();
        for (String file : ContentLoader.FILES) {
            Optional<byte[]> bytes = bundled.read(file);
            assertThat(bytes).as(file).isPresent();
            Files.write(dir.resolve(file), bytes.orElseThrow());
        }
        CountryOptions fromDir = options(42, "--content", dir.toString());

        CountryCommand.Result result = CountryCommand.run(fromDir);

        assertThat(result.content().hash()).isEqualTo(PACK.hash());
        assertThat(CountryCommand.render(result, fromDir)).isEqualTo(card(options(42)));
    }

    private static List<String> card(CountryOptions options) {
        return CountryCommand.render(CountryCommand.generate(PACK, options), options);
    }

    private static CountryOptions options(long seed, String... more) {
        List<String> args = new ArrayList<>(List.of("--seed", String.valueOf(seed)));
        args.addAll(List.of(more));
        return CountryOptions.parse(args);
    }
}
