package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestChain.NEUTRAL;
import static kolo.engine.generation.country.TestChain.STREAKY;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.IntStream;
import kolo.engine.content.CoastLevelId;
import kolo.engine.content.ContentPack;
import kolo.engine.content.StreakKind;
import kolo.engine.generation.map.PlacedCountry;
import kolo.engine.generation.map.PlacementGenerator;
import kolo.engine.generation.map.WorldMap;
import kolo.engine.generation.religion.StartReligion;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.SourceKind;
import kolo.engine.rng.Rng;
import kolo.engine.state.FateTokens;
import kolo.engine.state.TechBranch;
import kolo.engine.wheel.AppliedModifier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.WheelKind;
import org.junit.jupiter.api.Test;

class CountryGeneratorTest {

    private static final int SEEDS = 200;

    @Test
    void wheelsSpinInChainOrder() {
        StartCountry country = generate(NEUTRAL, 1);

        List<WheelKind> kinds = country.rolls().stream().map(RollRecord::kind).toList();
        List<WheelKind> firsts = new ArrayList<>();
        for (WheelKind kind : kinds) {
            if (!firsts.contains(kind)) {
                firsts.add(kind);
            }
        }
        assertThat(firsts)
                .containsSubsequence(
                        PlacementGenerator.CONTINENT_KIND,
                        PlacementGenerator.AREA_KIND,
                        PopulationWheel.KIND,
                        RegimeWheel.IDEOLOGY_KIND,
                        StateReligionWheel.KIND,
                        DevelopmentWheel.kind(TechBranch.ECONOMY),
                        GdpWheel.KIND,
                        HdiWheel.KIND,
                        ArmySizeWheel.KIND,
                        ArmyTrainingWheel.KIND,
                        ResourceWheel.COUNT_KIND,
                        NuclearWheel.KIND,
                        BackstoryWheel.COUNT_KIND,
                        NameWheel.KIND,
                        PeopleWheel.COUNT_KIND);
    }

    @Test
    void rollsAreTheRollsOfEveryPart() {
        StartCountry country = generate(STREAKY, 2);

        List<RollRecord> expected = new ArrayList<>();
        expected.addAll(country.territory().rolls());
        expected.addAll(country.population().rolls());
        expected.addAll(country.regime().rolls());
        expected.addAll(country.religion().rolls());
        expected.addAll(country.development().rolls());
        expected.addAll(country.gdp().rolls());
        expected.addAll(country.hdi().rolls());
        expected.add(country.streaks().get(0).roll());
        expected.addAll(country.armySize().rolls());
        expected.addAll(country.armyTraining().rolls());
        expected.addAll(country.resources().rolls());
        expected.addAll(country.nuclear().rolls());
        expected.add(country.streaks().get(1).roll());
        expected.addAll(country.backstory().rolls());
        expected.add(country.name().roll());
        expected.addAll(country.people().rolls());
        assertThat(country.rolls()).containsExactlyElementsOf(expected);
    }

    @Test
    void neutralWheelsGiveNoStreaks() {
        for (long seed = 0; seed < SEEDS; seed++) {
            StartCountry country = generate(NEUTRAL, seed);

            assertThat(country.streaks()).isEmpty();
            assertThat(country.fateTokens()).isZero();
            assertThat(country.tags()).doesNotContain("golden_age", "underdog");
            assertThat(country.backstory().entries())
                    .noneMatch(entry -> entry.fragment().equals(TestChain.GLORY));
        }
    }

    @Test
    void goldenAgeFiresAfterHdiAndUnderdogAfterNuclear() {
        StartCountry country = generate(STREAKY, 3);

        assertThat(country.streaks())
                .extracting(StreakBonus::streak)
                .containsExactly(StreakKind.GOLDEN_AGE, StreakKind.UNDERDOG);
        List<WheelKind> kinds = country.rolls().stream().map(RollRecord::kind).toList();
        int golden = kinds.indexOf(StreakWheel.kind(StreakKind.GOLDEN_AGE));
        assertThat(kinds.get(golden - 1)).isEqualTo(HdiWheel.KIND);
        assertThat(kinds.get(golden + 1)).isEqualTo(ArmySizeWheel.KIND);
        int underdog = kinds.indexOf(StreakWheel.kind(StreakKind.UNDERDOG));
        assertThat(kinds.get(underdog - 1)).isEqualTo(NuclearWheel.KIND);
        assertThat(kinds.get(underdog + 1)).isEqualTo(BackstoryWheel.COUNT_KIND);
    }

    @Test
    void streakModifiersActOnLaterWheels() {
        StartCountry country = generate(STREAKY, 4);

        RollRecord army = country.armySize().rolls().get(0);
        assertThat(army.modifiers())
                .contains(new AppliedModifier(
                        "streak:golden_age:great_figure:0",
                        "streak.golden_age.great_figure",
                        TestChain.ARMY_ADVANTAGE));
        // Колеса до стріку його не бачать.
        assertThat(country.hdi().rolls().get(0).modifiers())
                .extracting(AppliedModifier::sourceId)
                .noneMatch(id -> id.startsWith("streak:"));
    }

    @Test
    void streakTagsReachBackstoryAndCountry() {
        boolean gloryRolled = false;
        for (long seed = 0; seed < SEEDS; seed++) {
            StartCountry country = generate(STREAKY, seed);

            assertThat(country.backstory().tags()).contains("golden_age", "world_attention", "underdog", "sympathy");
            assertThat(country.tags()).contains("golden_age", "world_attention", "underdog", "sympathy");
            gloryRolled |= country.backstory().entries().stream()
                    .anyMatch(entry -> entry.fragment().equals(TestChain.GLORY));
        }
        assertThat(gloryRolled).isTrue();
    }

    @Test
    void goldenAgeAddsAPerson() {
        for (long seed = 0; seed < SEEDS; seed++) {
            StartCountry neutral = generate(NEUTRAL, seed);
            StartCountry streaky = generate(STREAKY, seed);

            assertThat(neutral.people().people()).hasSize(counted(neutral));
            assertThat(streaky.people().people()).hasSize(counted(streaky) + 1);
        }
    }

    @Test
    void fateTokensAreClippedToTheLimit() {
        StartCountry country = generate(STREAKY, 5);

        int total = country.streaks().stream().mapToInt(StreakBonus::fateTokens).sum();
        assertThat(total).isGreaterThan(FateTokens.MAX);
        assertThat(country.fateTokens()).isEqualTo(FateTokens.MAX);
    }

    @Test
    void modifiersComeInOrderOfAcquisition() {
        for (long seed = 0; seed < SEEDS; seed++) {
            StartCountry country = generate(STREAKY, seed);

            List<Modifier> expected = new ArrayList<>();
            expected.addAll(country.regime().modifiers());
            expected.addAll(country.religion().modifiers());
            country.streaks().forEach(bonus -> expected.addAll(bonus.modifiers()));
            expected.addAll(country.backstory().modifiers());
            assertThat(country.modifiers()).containsExactlyElementsOf(expected);
            assertThat(country.modifiers()).extracting(Modifier::id).doesNotHaveDuplicates();
        }
    }

    @Test
    void backstoryModifiersBecomeCountryModifiers() {
        for (long seed = 0; seed < SEEDS; seed++) {
            StartCountry country = generate(STREAKY, seed);
            if (country.backstory().entries().stream()
                    .noneMatch(entry -> entry.fragment().equals(TestChain.GLORY))) {
                continue;
            }

            assertThat(country.modifiers())
                    .filteredOn(modifier -> modifier.source().kind() == SourceKind.BACKSTORY)
                    .extracting(Modifier::id)
                    .containsExactly("backstory:glory:0", "backstory:glory:1");
            return;
        }
        throw new AssertionError("фрагмент glory не випав");
    }

    @Test
    void mapWheelsCountInStreaks() {
        StartCountry country = generate(TestChain.MAP_STREAKY, 6);

        // Площа, населення й розвиненість — три дуже добрі поспіль; ВВП та ІЛР уже не дають другої «Золотої доби».
        assertThat(country.streaks()).extracting(StreakBonus::streak).containsExactly(StreakKind.GOLDEN_AGE);
        List<RollRecord> rolls = country.rolls();
        int golden = rolls.indexOf(country.streaks().get(0).roll());
        assertThat(rolls.get(golden - 1))
                .isEqualTo(country.development().rolls().getLast());
        assertThat(rolls.get(golden + 1)).isEqualTo(country.gdp().rolls().getFirst());
    }

    @Test
    void countryLivesOnItsTerritory() {
        WorldMap world = TestChain.world(NEUTRAL);
        for (int number = 0; number < world.countries(); number++) {
            CountryGenerationInput input = TestChain.input(NEUTRAL, number);
            StartCountry country = CountryGenerator.generate(Rng.of(number), NEUTRAL, input);

            PlacedCountry territory = world.country(number);
            assertThat(country.territory()).isEqualTo(territory);
            assertThat(country.geography())
                    .isEqualTo(Geography.generate(
                            NEUTRAL, territory.cells(), world.sea(), world.climate(), world.fertility()));
            assertThat(country.population().provinces().keySet()).containsExactlyElementsOf(territory.cells());
            assertThat(country.resources().deposits())
                    .allSatisfy(deposit -> assertThat(territory.cells()).contains(deposit.cell()));
            assertThat(country.tags())
                    .containsAll(territory.tags())
                    .containsAll(country.geography().tags())
                    .containsAll(country.population().tags());
        }
    }

    @Test
    void neighborComesFromTheMap() {
        WorldMap world = TestChain.world(NEUTRAL);
        int number = IntStream.range(0, world.countries())
                .filter(candidate -> !world.neighbors(candidate).isEmpty())
                .findFirst()
                .orElseThrow();
        CountryGenerationInput input = TestChain.input(NEUTRAL, number);
        boolean neighbor = false;
        for (long seed = 0; seed < SEEDS; seed++) {
            StartCountry country = CountryGenerator.generate(Rng.of(seed), NEUTRAL, input);
            neighbor |= country.backstory().neighbor().isPresent();
            country.backstory()
                    .neighbor()
                    .ifPresent(id -> assertThat(input.neighbors()).contains(id));
        }
        assertThat(neighbor).isTrue();
    }

    @Test
    void populationShiftsDevelopmentAndGdp() {
        boolean shifted = false;
        for (long seed = 0; seed < SEEDS; seed++) {
            StartCountry country = generate(NEUTRAL, seed);

            int step = country.population().tier().step();
            String id = "population:" + country.population().level();
            for (RollRecord roll : country.development().rolls()) {
                assertThat(contribution(roll, id)).isEqualTo(step * TestChain.DEVELOPMENT_PER_STEP);
            }
            assertThat(contribution(country.gdp().rolls().getFirst(), id)).isEqualTo(step * TestChain.GDP_PER_STEP);
            shifted |= step != 0;
        }
        assertThat(shifted).isTrue();
    }

    @Test
    void coastShiftsGdp() {
        WorldMap world = TestChain.world(NEUTRAL);
        TreeSet<Integer> seen = new TreeSet<>();
        for (int number = 0; number < world.countries(); number++) {
            StartCountry country = CountryGenerator.generate(Rng.of(number), NEUTRAL, TestChain.input(NEUTRAL, number));

            CoastLevelId coast = country.geography().coast();
            int expected = NEUTRAL.map().geography().coast().stream()
                    .filter(level -> level.id().equals(coast))
                    .findFirst()
                    .orElseThrow()
                    .gdpAdvantage();
            assertThat(contribution(country.gdp().rolls().getFirst(), "coast:" + coast))
                    .isEqualTo(expected);
            seen.add(expected);
        }
        assertThat(seen).isNotEmpty();
    }

    @Test
    void totalGdpAndArmyFollowPopulation() {
        StartCountry country = generate(NEUTRAL, 7);

        long people = country.population().populationK() * 1_000L;
        assertThat(country.totalGdp()).isEqualTo(people * country.gdp().perCapita());
        assertThat(country.armyStrength()).isEqualTo(people * country.armySize().shareBp() / 10_000);
    }

    @Test
    void takenNamesAreNotRepeated() {
        List<StartReligion> religions = TestChain.religions(NEUTRAL);
        WorldMap world = TestChain.world(NEUTRAL);
        TreeSet<String> countries = new TreeSet<>();
        TreeSet<String> people = new TreeSet<>();
        // У північному стилі лише 8 чоловічих імен: трьох держав досить, щоб повтори стали ймовірними.
        for (int seed = 0; seed < 3; seed++) {
            CountryGenerationInput input = new CountryGenerationInput(world, 0, religions, countries, people);
            StartCountry country = CountryGenerator.generate(Rng.of(seed), NEUTRAL, input);

            assertThat(countries.add(country.name().name().fullName().nominative()))
                    .isTrue();
            for (StartPerson person : country.people().people()) {
                assertThat(people.add(person.name().fullName().nominative())).isTrue();
            }
        }
    }

    /** Внесок з цим id у перевагу обертання; 0, якщо рядка немає. */
    private static int contribution(RollRecord roll, String id) {
        return roll.modifiers().stream()
                .filter(modifier -> modifier.sourceId().equals(id))
                .mapToInt(AppliedModifier::value)
                .sum();
    }

    /** Скільки постатей обрало колесо кількості: сектор {@code people_<n>}. */
    private static int counted(StartCountry country) {
        return Integer.parseInt(country.people().countRoll().resultSectorId().substring("people_".length()));
    }

    private static StartCountry generate(ContentPack pack, long seed) {
        return CountryGenerator.generate(Rng.of(seed), pack, TestChain.input(pack));
    }
}
