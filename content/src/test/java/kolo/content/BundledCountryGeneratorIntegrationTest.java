package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.content.ResourceId;
import kolo.engine.content.StreakKind;
import kolo.engine.generation.country.CountryGenerationInput;
import kolo.engine.generation.country.CountryGenerator;
import kolo.engine.generation.country.StartCountry;
import kolo.engine.generation.country.StartPerson;
import kolo.engine.generation.country.StreakBonus;
import kolo.engine.generation.religion.StartReligion;
import kolo.engine.generation.religion.WorldReligionsWheel;
import kolo.engine.modifier.Modifier;
import kolo.engine.rng.Rng;
import kolo.engine.state.CountryId;
import kolo.engine.state.FateTokens;
import org.junit.jupiter.api.Test;

/**
 * Ланцюжок коліс генерації на вбудованому контенті: 10 000 держав у світах по 40 без жодного порушення обмежень —
 * назви й імена унікальні в світі, жетони в межах ліміту, кожен стрік щонайбільше раз, модифікатори валідні.
 */
class BundledCountryGeneratorIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();
    private static final int WORLDS = 250;
    private static final int COUNTRIES = 40;

    /** Сусідами передісторії вважаються кілька попередніх держав світу — доки немає карти. */
    private static final int NEIGHBORS = 3;

    private static final ResourceId URANIUM = new ResourceId("uranium");

    @Test
    void tenThousandCountriesKeepAllConstraints() {
        TreeMap<StreakKind, Integer> streaks = new TreeMap<>();
        for (long world = 0; world < WORLDS; world++) {
            Rng worldRng = Rng.of(world);
            List<StartReligion> religions = WorldReligionsWheel.generate(worldRng.fork("religions"), PACK, COUNTRIES)
                    .religions();
            TreeSet<String> countryNames = new TreeSet<>();
            TreeSet<String> personNames = new TreeSet<>();
            List<CountryId> ids = new ArrayList<>();
            for (int n = 0; n < COUNTRIES; n++) {
                TreeSet<ResourceId> resources = new TreeSet<>();
                if (n % 3 == 0) {
                    resources.add(URANIUM);
                }
                TreeSet<CountryId> neighbors = new TreeSet<>(ids.subList(Math.max(0, n - NEIGHBORS), n));
                CountryGenerationInput input =
                        new CountryGenerationInput(religions, resources, neighbors, countryNames, personNames);
                StartCountry country = CountryGenerator.generate(worldRng.fork("country:" + n), PACK, input);

                assertThat(countryNames.add(country.name().name().fullName().nominative()))
                        .isTrue();
                for (StartPerson person : country.people().people()) {
                    assertThat(personNames.add(person.name().fullName().nominative()))
                            .isTrue();
                }
                check(country, neighbors);
                country.streaks().forEach(bonus -> streaks.merge(bonus.streak(), 1, Integer::sum));
                ids.add(CountryId.of(n + 1));
            }
        }

        // Стрік — подія, а не норма: не частіше, ніж у кожної четвертої держави.
        streaks.values().forEach(count -> assertThat(count * 4).isLessThan(WORLDS * COUNTRIES));
    }

    private static void check(StartCountry country, TreeSet<CountryId> neighbors) {
        assertThat(country.fateTokens()).isBetween(0, FateTokens.MAX);
        assertThat(country.streaks()).extracting(StreakBonus::streak).doesNotHaveDuplicates();
        country.streaks().forEach(bonus -> assertThat(country.tags()).containsAll(bonus.tags()));
        assertThat(country.tags()).containsAll(country.backstory().tags());
        assertThat(country.modifiers()).extracting(Modifier::id).doesNotHaveDuplicates();
        assertThat(country.modifiers()).allMatch(modifier -> modifier.isActiveAt(0));
        country.backstory()
                .neighbor()
                .ifPresent(neighbor -> assertThat(neighbors).contains(neighbor));
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
