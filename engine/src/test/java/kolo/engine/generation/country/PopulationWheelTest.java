package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestPopulation.FERTILITY;
import static kolo.engine.generation.country.TestPopulation.GEOGRAPHY;
import static kolo.engine.generation.country.TestPopulation.IDS;
import static kolo.engine.generation.country.TestPopulation.LARGE;
import static kolo.engine.generation.country.TestPopulation.MEDIUM;
import static kolo.engine.generation.country.TestPopulation.PACK;
import static kolo.engine.generation.country.TestPopulation.SMALL;
import static kolo.engine.generation.country.TestPopulation.WEIGHTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.engine.content.AreaLevelDef;
import kolo.engine.content.AreaLevelId;
import kolo.engine.content.ContentPack;
import kolo.engine.content.PopulationLevelDef;
import kolo.engine.content.PopulationLevelId;
import kolo.engine.content.TestMaps;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.map.FertilityMap;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.AppliedModifier;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PopulationWheelTest {

    private static final int SEEDS = 2_000;

    @Test
    void sectorsAreLevelsInContentOrderWithContentWeightsTiersQualitiesAndTags() {
        List<Sector<PopulationLevelDef>> sectors = PopulationWheel.sectors(PACK);

        assertThat(sectors).extracting(Sector::id).containsExactly(IDS);
        assertThat(sectors.stream().mapToInt(Sector::weightBp).toArray()).isEqualTo(WEIGHTS);
        assertThat(sectors.stream().mapToInt(Sector::quality)).containsExactly(10, 30, 50, 70, 90);
        assertThat(sectors).extracting(Sector::tier).containsExactly(OutcomeTier.values());
        assertThat(sectors.getFirst().tags()).containsExactly("small_population");
        assertThat(sectors.get(2).tags()).isEmpty();
        assertThat(sectors.getLast().tags()).containsExactly("large_population");
    }

    @Test
    void advantageIsModifiersThenAreaThenFertility() {
        Advantage advantage =
                PopulationWheel.advantage(PACK, List.of(TestPopulation.modifier("event", 7)), area(LARGE), 42, 40);

        // Велика держава 200%: (200 − 100) × 20 / 100 = +20; родючість +2 × 100%.
        assertThat(advantage.value()).isEqualTo(7 + 20 + 2);
        assertThat(advantage.modifiers())
                .extracting(AppliedModifier::sourceId, AppliedModifier::descriptionKey, AppliedModifier::value)
                .containsExactly(
                        tuple("event", "event.test", 7),
                        tuple("area:large", "area.large", 20),
                        tuple("geography:fertility", "geography.fertility", 2));
    }

    @Test
    void averageAreaAndFertilityAddNoContributionAndNegativeOnesRoundDown() {
        assertThat(PopulationWheel.advantage(PACK, List.of(), area(MEDIUM), 40, 40))
                .isEqualTo(Advantage.NONE);
        // Мала 50%: −50 × 20 / 100 = −10; родючість −3 × 50% = −1,5 → −2.
        ContentPack half = TestNames.pack(TestMaps.content(TestMaps.population(20, 50, 10, 20)), TestMaps.BALANCE);
        assertThat(PopulationWheel.advantage(half, List.of(), area(SMALL), 37, 40)
                        .modifiers())
                .extracting(AppliedModifier::value)
                .containsExactly(-10, -2);
    }

    @Test
    void advantageIsClampedButExplainsEveryContribution() {
        Advantage advantage =
                PopulationWheel.advantage(PACK, List.of(TestPopulation.modifier("event", 90)), area(LARGE), 100, 0);

        assertThat(advantage.value()).isEqualTo(Advantage.MAX);
        assertThat(advantage.modifiers()).extracting(AppliedModifier::value).containsExactly(90, 20, 100);
    }

    @Test
    void worldFertilityIsMeanOfAllLandRoundedDown() {
        assertThat(PopulationWheel.worldFertility(FERTILITY)).isEqualTo(40);
        assertThat(PopulationWheel.worldFertility(new FertilityMap(new TreeMap<>(Map.of(0, 1, 1, 2)))))
                .isEqualTo(1);
        assertThatThrownBy(() -> PopulationWheel.worldFertility(new FertilityMap(new TreeMap<>())))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.EMPTY_COLLECTION));
    }

    @Test
    void populationIsSplitByFertilityAndCoast() {
        // Ваги 10 / 80 / 110 / 30 із 230: 1000 → 43,5 / 347,8 / 478,3 / 130,4.
        assertThat(PopulationWheel.provinces(PACK.map().population(), 1_000, GEOGRAPHY, FERTILITY))
                .containsExactly(Map.entry(0, 44), Map.entry(1, 348), Map.entry(2, 478), Map.entry(3, 130));
        // Населення менше за кількість провінцій: пуста провінція може лишитися без людей.
        assertThat(PopulationWheel.provinces(PACK.map().population(), 2, GEOGRAPHY, FERTILITY))
                .containsExactly(Map.entry(0, 0), Map.entry(1, 1), Map.entry(2, 1), Map.entry(3, 0));
    }

    @Test
    void resultMatchesRolledLevel() {
        boolean[] seen = new boolean[IDS.length];
        for (long seed = 0; seed < SEEDS; seed++) {
            StartPopulation population =
                    PopulationWheel.generate(Rng.of(seed), PACK, List.of(), LARGE, GEOGRAPHY, FERTILITY);
            PopulationLevelDef level =
                    PACK.map().population().level(population.level()).orElseThrow();
            RollRecord roll = population.rolls().getFirst();

            assertThat(population.rolls()).hasSize(1);
            assertThat(roll.kind()).isEqualTo(PopulationWheel.KIND);
            assertThat(roll.resultSectorId()).isEqualTo(population.level().value());
            assertThat(roll.advantage()).isEqualTo(22);
            assertThat(roll.turn()).isZero();
            assertThat(roll.season()).isNull();
            assertThat(population.populationK()).isEqualTo(level.populationK());
            assertThat(population.tier()).isEqualTo(level.tier());
            assertThat(population.quality()).isEqualTo(level.quality());
            assertThat(population.tags()).containsExactlyElementsOf(new TreeSet<>(level.tags()));
            assertThat(population.provinces())
                    .isEqualTo(PopulationWheel.provinces(
                            PACK.map().population(), level.populationK(), GEOGRAPHY, FERTILITY));
            seen[Arrays.asList(IDS).indexOf(population.level().value())] = true;
        }
        assertThat(seen).containsOnly(true);
    }

    @Test
    void usesItsOwnStream() {
        int direct = Rng.of(42).fork("population").nextInt(Wheel.TOTAL_BP);
        StartPopulation population =
                PopulationWheel.generate(Rng.of(42), PACK, List.of(), MEDIUM, GEOGRAPHY, FERTILITY);

        assertThat(population.rolls().getFirst().roll()).isEqualTo(direct);
    }

    @ParameterizedTest
    @ValueSource(ints = {Advantage.MIN, 0, Advantage.MAX})
    void advantageShiftsTowardsLargerPopulationAndKeepsExtremesPossible(int advantage) {
        int[] weights = Wheel.applyAdvantage(
                        PopulationWheel.sectors(PACK),
                        advantage,
                        PACK.balance().wheel().strength(PopulationWheel.KIND))
                .stream()
                .mapToInt(Sector::weightBp)
                .toArray();

        assertThat(Arrays.stream(weights).sum()).isEqualTo(Wheel.TOTAL_BP);
        assertThat(weights[0]).isGreaterThanOrEqualTo(Wheel.MIN_CRITICAL_BP);
        assertThat(weights[4]).isGreaterThanOrEqualTo(Wheel.MIN_CRITICAL_BP);
        if (advantage > 0) {
            assertThat(weights[0]).isLessThan(WEIGHTS[0]);
            assertThat(weights[4]).isGreaterThan(WEIGHTS[4]);
        } else if (advantage < 0) {
            assertThat(weights[0]).isGreaterThan(WEIGHTS[0]);
            assertThat(weights[4]).isLessThan(WEIGHTS[4]);
        } else {
            assertThat(weights).isEqualTo(WEIGHTS);
        }
    }

    @Test
    void unknownAreaAndForeignProvincesAreRejected() {
        assertThatThrownBy(() -> PopulationWheel.generate(
                        Rng.of(1), PACK, List.of(), new AreaLevelId("colossal"), GEOGRAPHY, FERTILITY))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE);
                    assertThat(e.details()).containsEntry("value", "colossal");
                });
        FertilityMap other = new FertilityMap(new TreeMap<>(Map.of(0, 10, 1, 10, 2, 10)));
        assertThatThrownBy(() -> PopulationWheel.generate(Rng.of(1), PACK, List.of(), MEDIUM, GEOGRAPHY, other))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE);
                    assertThat(e.details()).containsEntry("value", 3);
                });
    }

    @Test
    void startPopulationValidatesFields() {
        PopulationLevelId level = new PopulationLevelId("medium");
        TreeMap<Integer, Integer> provinces = new TreeMap<>(Map.of(1, 40, 2, 60));
        assertThat(new StartPopulation(
                                level,
                                100,
                                OutcomeTier.PARTIAL,
                                new TreeSet<>(List.of("b", "a")),
                                50,
                                provinces,
                                List.of())
                        .tags())
                .containsExactly("a", "b");
        assertOutOfRange(() -> new StartPopulation(
                level, 0, OutcomeTier.PARTIAL, new TreeSet<>(), 50, new TreeMap<>(Map.of(1, 0)), List.of()));
        assertOutOfRange(
                () -> new StartPopulation(level, 100, OutcomeTier.PARTIAL, new TreeSet<>(), 101, provinces, List.of()));
        assertOutOfRange(
                () -> new StartPopulation(level, 101, OutcomeTier.PARTIAL, new TreeSet<>(), 50, provinces, List.of()));
        assertOutOfRange(() ->
                new StartPopulation(level, 100, OutcomeTier.PARTIAL, new TreeSet<>(), 50, new TreeMap<>(), List.of()));
        assertThatThrownBy(() ->
                        new StartPopulation(null, 100, OutcomeTier.PARTIAL, new TreeSet<>(), 50, provinces, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    private static AreaLevelDef area(AreaLevelId id) {
        return PACK.map().placement().area(id).orElseThrow();
    }

    private static void assertOutOfRange(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
    }
}
