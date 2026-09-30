package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.content.StreakKind;
import kolo.engine.generation.country.CountryGenerationInput;
import kolo.engine.generation.country.CountryGenerator;
import kolo.engine.generation.country.StartCountry;
import kolo.engine.generation.country.StartDeposit;
import kolo.engine.generation.country.StartPerson;
import kolo.engine.generation.country.StreakBonus;
import kolo.engine.generation.map.MapGenerator;
import kolo.engine.generation.map.PlacedCountry;
import kolo.engine.generation.map.WorldMap;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.religion.StartReligion;
import kolo.engine.generation.religion.WorldReligionsWheel;
import kolo.engine.modifier.Modifier;
import kolo.engine.rng.Rng;
import kolo.engine.state.FateTokens;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldLimits;
import org.junit.jupiter.api.Test;

/**
 * Ланцюжок коліс генерації на вбудованому контенті з картою: понад 10 000 держав без жодного порушення обмежень —
 * держава живе на своїй території, назви й імена унікальні в світі, жетони в межах ліміту, кожен стрік щонайбільше
 * раз, модифікатори валідні, сусід передісторії межує з державою.
 *
 * <p>Карта — найдорожча частина, а держави на ній від неї не залежать інакше, ніж через територію, тож кожна карта
 * заселяється кілька разів з різних seed-ів; кожне заселення — окремий світ з власними зайнятими назвами.
 */
class BundledCountryGeneratorIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();
    private static final int MAPS = 30;
    private static final int RUNS = 15;
    private static final int COUNTRIES = 10_000;

    @Test
    void tenThousandCountriesKeepAllConstraints() {
        TreeMap<StreakKind, Integer> streaks = new TreeMap<>();
        int generated = 0;
        for (long seed = 0; seed < MAPS; seed++) {
            Rng worldRng = Rng.of(seed);
            WorldMap map = MapGenerator.generate(
                    worldRng.fork("map"), PACK, WorldSizeInput.of(WorldLimits.MAX_PLAYERS, NpcShare.MANY));
            List<StartReligion> religions = WorldReligionsWheel.generate(
                            worldRng.fork("religions"), PACK, map.countries())
                    .religions();
            for (int run = 0; run < RUNS; run++) {
                TreeSet<String> countryNames = new TreeSet<>();
                TreeSet<String> personNames = new TreeSet<>();
                for (int n = 0; n < map.countries(); n++) {
                    CountryGenerationInput input =
                            new CountryGenerationInput(map, n, religions, countryNames, personNames);
                    StartCountry country =
                            CountryGenerator.generate(worldRng.fork("run:" + run + ":country:" + n), PACK, input);

                    assertThat(countryNames.add(country.name().name().fullName().nominative()))
                            .isTrue();
                    for (StartPerson person : country.people().people()) {
                        assertThat(personNames.add(person.name().fullName().nominative()))
                                .isTrue();
                    }
                    check(country, input);
                    country.streaks().forEach(bonus -> streaks.merge(bonus.streak(), 1, Integer::sum));
                    generated++;
                }
            }
        }

        assertThat(generated).isGreaterThanOrEqualTo(COUNTRIES);
        // Стрік — подія, а не норма: не частіше, ніж у кожної четвертої держави.
        int total = generated;
        streaks.values().forEach(count -> assertThat(count * 4).isLessThan(total));
    }

    private static void check(StartCountry country, CountryGenerationInput input) {
        PlacedCountry territory = input.territory();
        assertThat(country.territory()).isEqualTo(territory);
        assertThat(country.geography().provinces()).isEqualTo(territory.cells());
        assertThat(country.population().provinces().keySet()).containsExactlyElementsOf(territory.cells());
        assertThat(country.resources().deposits())
                .extracting(StartDeposit::cell)
                .allSatisfy(cell -> assertThat(territory.cells()).contains(cell));
        assertThat(country.totalGdp()).isPositive();
        assertThat(country.armyStrength()).isNotNegative();

        assertThat(country.fateTokens()).isBetween(0, FateTokens.MAX);
        assertThat(country.streaks()).extracting(StreakBonus::streak).doesNotHaveDuplicates();
        country.streaks().forEach(bonus -> assertThat(country.tags()).containsAll(bonus.tags()));
        assertThat(country.tags())
                .containsAll(territory.tags())
                .containsAll(country.geography().tags())
                .containsAll(country.population().tags())
                .containsAll(country.backstory().tags());
        assertThat(country.modifiers()).extracting(Modifier::id).doesNotHaveDuplicates();
        assertThat(country.modifiers()).allMatch(modifier -> modifier.isActiveAt(0));
        country.backstory()
                .neighbor()
                .ifPresent(neighbor -> assertThat(input.neighbors()).contains(neighbor));
        int extra =
                country.streaks().stream().mapToInt(StreakBonus::extraPeople).sum();
        assertThat(country.people().people().size())
                .isBetween(
                        PACK.balance().generation().notablePeople().min(),
                        PACK.balance().generation().notablePeople().max() + extra);
        assertThat(country.backstory().entries().size())
                .isLessThanOrEqualTo(
                        PACK.balance().generation().backstoryFragments().max());
    }
}
