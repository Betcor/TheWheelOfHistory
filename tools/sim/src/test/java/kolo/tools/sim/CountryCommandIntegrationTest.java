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
import kolo.engine.generation.religion.WorldReligionsWheel;
import kolo.engine.rng.Rng;
import kolo.engine.state.NuclearStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Команда {@code country} на вбудованому контенті: відтворюваність, відповідність рушію, картка для будь-якого seed. */
class CountryCommandIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();
    private static final int SEEDS = 300;

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
    void generatesTheCountryOfTheEngineWorld() {
        Rng world = Rng.of(7);
        CountryGenerationInput input = CountryGenerationInput.of(WorldReligionsWheel.generate(
                        world.fork(CountryCommand.RELIGIONS_STREAM), PACK, CountryOptions.DEFAULT_COUNTRIES)
                .religions());
        StartCountry expected = CountryGenerator.generate(world.fork(CountryCommand.COUNTRY_STREAM), PACK, input);

        StartCountry actual = CountryCommand.generate(PACK, options(7)).country();

        assertThat(actual).isEqualTo(expected);
        assertThat(card(options(7)))
                .anySatisfy(line -> assertThat(line)
                        .contains(expected.name().name().fullName().nominative()));
    }

    @Test
    void everySeedRendersWithoutPlaceholders() {
        for (long seed = 0; seed < SEEDS; seed++) {
            CountryOptions options = options(seed, "--rolls", "--resources", "uranium");
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
        }
    }

    @Test
    void resourcesReachTheNuclearWheel() {
        boolean arsenal = false;
        for (long seed = 0; seed < SEEDS; seed++) {
            NuclearStatus without = CountryCommand.generate(PACK, options(seed))
                    .country()
                    .nuclear()
                    .status();
            assertThat(without).isNotEqualTo(NuclearStatus.ARSENAL);
            arsenal |= CountryCommand.generate(PACK, options(seed, "--resources", "uranium"))
                            .country()
                            .nuclear()
                            .status()
                    == NuclearStatus.ARSENAL;
        }

        assertThat(arsenal).isTrue();
    }

    @Test
    void rejectsUnknownResource() {
        assertThatThrownBy(() -> CountryCommand.generate(PACK, options(1, "--resources", "unobtainium")))
                .isInstanceOfSatisfying(
                        UsageException.class, e -> assertThat(e.key()).isEqualTo("error.usage.unknown_resource"));
        assertThatThrownBy(() -> CountryCommand.generate(PACK, options(1, "--resources", "Not An Id")))
                .isInstanceOfSatisfying(
                        UsageException.class, e -> assertThat(e.key()).isEqualTo("error.usage.unknown_resource"));
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
