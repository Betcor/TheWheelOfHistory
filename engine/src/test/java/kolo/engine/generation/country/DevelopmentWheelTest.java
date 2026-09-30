package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestDevelopment.PACK;
import static kolo.engine.generation.country.TestDevelopment.QUALITIES;
import static kolo.engine.generation.country.TestDevelopment.REGIME;
import static kolo.engine.generation.country.TestDevelopment.WEIGHTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.content.DevelopmentLevelDef;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.SourceKind;
import kolo.engine.rng.Rng;
import kolo.engine.state.TechBranch;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.AppliedModifier;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import kolo.engine.wheel.WheelKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DevelopmentWheelTest {

    private static final int SEEDS = 500;
    private static final int STRENGTH = 50;

    @Test
    void kindIsPerBranch() {
        assertThat(Arrays.stream(TechBranch.values()).map(DevelopmentWheel::kind))
                .extracting(WheelKind::id)
                .containsExactly(
                        "generation_development_economy",
                        "generation_development_military",
                        "generation_development_society",
                        "generation_development_energy_science");
    }

    @Test
    void sectorsAreLevelsInAscendingOrderWithContentWeightsAndQualities() {
        List<Sector<DevelopmentLevelDef>> sectors = DevelopmentWheel.sectors(PACK);

        assertThat(sectors)
                .extracting(Sector::id)
                .containsExactly(
                        "level_minus_3", "level_minus_2", "level_minus_1", "level_0", "level_plus_1", "level_plus_2");
        assertThat(sectors).extracting(sector -> sector.value().level()).containsExactly(-3, -2, -1, 0, 1, 2);
        assertThat(sectors.stream().mapToInt(Sector::weightBp).toArray()).isEqualTo(WEIGHTS);
        assertThat(sectors.stream().mapToInt(Sector::quality).toArray()).isEqualTo(QUALITIES);
        assertThat(sectors)
                .extracting(Sector::tier)
                .containsExactly(
                        OutcomeTier.CRIT_FAIL,
                        OutcomeTier.FAIL,
                        OutcomeTier.FAIL,
                        OutcomeTier.PARTIAL,
                        OutcomeTier.SUCCESS,
                        OutcomeTier.CRIT_SUCCESS);
        assertThat(sectors.getLast().tags()).containsExactly("advanced", "leader");
    }

    @Test
    void tierRejectsLevelOutsideDesignRange() {
        assertThatThrownBy(() -> DevelopmentWheel.tier(3))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
    }

    @Test
    void rollsOneWheelPerBranchInBranchOrder() {
        StartDevelopment development = DevelopmentWheel.generate(Rng.of(7), PACK, List.of());

        assertThat(development.rolls())
                .extracting(RollRecord::kind)
                .containsExactlyElementsOf(Arrays.stream(TechBranch.values())
                        .map(DevelopmentWheel::kind)
                        .toList());
        for (int i = 0; i < TechBranch.values().length; i++) {
            RollRecord roll = development.rolls().get(i);
            assertThat(roll.resultSectorId())
                    .isEqualTo(DevelopmentWheel.sectorId(development.level(TechBranch.values()[i])));
            assertThat(roll.turn()).isZero();
            assertThat(roll.season()).isNull();
            assertThat(roll.sectors().stream().mapToInt(RolledSector::weightBp).sum())
                    .isEqualTo(Wheel.TOTAL_BP);
        }
    }

    @Test
    void withoutModifiersWeightsAreContentWeights() {
        StartDevelopment development = DevelopmentWheel.generate(Rng.of(3), PACK, List.of());

        assertThat(development.rolls()).allSatisfy(roll -> {
            assertThat(roll.advantage()).isZero();
            assertThat(roll.modifiers()).isEmpty();
            assertThat(roll.sectors().stream().mapToInt(RolledSector::weightBp).toArray())
                    .isEqualTo(WEIGHTS);
        });
    }

    @Test
    void regimeModifiersGiveAdvantageOnlyToTheirBranch() {
        StartDevelopment development = DevelopmentWheel.generate(Rng.of(3), PACK, REGIME.modifiers());

        Map<TechBranch, RollRecord> rolls = byBranch(development);
        // Енергетика: 100 + 20 = 120, обрізано до 100; пояснення — обидва внески.
        assertThat(rolls.get(TechBranch.ENERGY_SCIENCE).advantage()).isEqualTo(Advantage.MAX);
        assertThat(rolls.get(TechBranch.ENERGY_SCIENCE).modifiers())
                .extracting(AppliedModifier::sourceId, AppliedModifier::value)
                .containsExactly(tuple("ideology:authoritarianism:0", 100), tuple("sub_ideology:technocracy:0", 20));
        assertThat(rolls.get(TechBranch.MILITARY).advantage()).isEqualTo(Advantage.MIN);
        assertThat(rolls.get(TechBranch.ECONOMY).advantage()).isEqualTo(30);
        assertThat(rolls.get(TechBranch.ECONOMY).modifiers()).hasSize(2);
        assertThat(rolls.get(TechBranch.SOCIETY).advantage()).isZero();
        assertThat(rolls.get(TechBranch.SOCIETY).modifiers()).isEmpty();
    }

    @Test
    void levelsTagsAndQualityFollowTheRolls() {
        for (long seed = 0; seed < SEEDS; seed++) {
            StartDevelopment development = DevelopmentWheel.generate(Rng.of(seed), PACK, REGIME.modifiers());

            TreeSet<String> tags = new TreeSet<>();
            int qualitySum = 0;
            for (TechBranch branch : TechBranch.values()) {
                DevelopmentLevelDef level = PACK.developmentLevel(development.level(branch));
                tags.addAll(level.tags());
                qualitySum += level.quality();
            }
            assertThat(development.tags()).containsExactlyElementsOf(tags);
            assertThat(development.quality()).isEqualTo(Math.floorDiv(qualitySum, 4));
        }
    }

    @Test
    void qualityIsAverageRoundedDown() {
        // Шукаємо seed з рівнями, середня якість яких не ціла, щоб перевірити округлення.
        for (long seed = 0; seed < SEEDS; seed++) {
            StartDevelopment development = DevelopmentWheel.generate(Rng.of(seed), PACK, List.of());
            int sum = development.levels().values().stream()
                    .mapToInt(level -> QUALITIES[level + 3])
                    .sum();
            if (sum % 4 != 0) {
                assertThat(development.quality()).isEqualTo(sum / 4);
                return;
            }
        }
        throw new AssertionError("жодного seed з неціллю середньою якістю");
    }

    @Test
    void branchStreamsAreIndependent() {
        // Кожна галузь крутиться у своєму потоці: кидок військової галузі не залежить від кидків економіки.
        int direct = Rng.of(42).fork("military").nextInt(Wheel.TOTAL_BP);
        StartDevelopment development = DevelopmentWheel.generate(Rng.of(42), PACK, List.of());

        assertThat(byBranch(development).get(TechBranch.MILITARY).roll()).isEqualTo(direct);
    }

    @ParameterizedTest
    @ValueSource(ints = {Advantage.MIN, 0, Advantage.MAX})
    void advantageShiftsLevelsAndKeepsExtremes(int advantage) {
        int[] weights = Wheel.applyAdvantage(DevelopmentWheel.sectors(PACK), advantage, STRENGTH).stream()
                .mapToInt(Sector::weightBp)
                .toArray();

        assertThat(Arrays.stream(weights).sum()).isEqualTo(Wheel.TOTAL_BP);
        assertThat(weights[0]).isGreaterThanOrEqualTo(Wheel.MIN_CRITICAL_BP);
        assertThat(weights[5]).isGreaterThanOrEqualTo(Wheel.MIN_CRITICAL_BP);
        int below = weights[0] + weights[1] + weights[2];
        int above = weights[4] + weights[5];
        if (advantage > 0) {
            assertThat(below).isLessThan(3000);
            assertThat(above).isGreaterThan(3000);
        } else if (advantage < 0) {
            assertThat(below).isGreaterThan(3000);
            assertThat(above).isLessThan(3000);
        } else {
            assertThat(weights).isEqualTo(WEIGHTS);
        }
    }

    @Test
    void regimeModifiersComeFromIdeologyThenSubIdeology() {
        List<Modifier> modifiers = REGIME.modifiers();

        assertThat(modifiers)
                .extracting(Modifier::id)
                .containsExactly(
                        "ideology:authoritarianism:0",
                        "ideology:authoritarianism:1",
                        "ideology:authoritarianism:2",
                        "sub_ideology:technocracy:0",
                        "sub_ideology:technocracy:1");
        assertThat(modifiers).allSatisfy(modifier -> {
            assertThat(modifier.source().kind()).isEqualTo(SourceKind.IDEOLOGY);
            assertThat(modifier.expiresAtTurn()).isNull();
        });
        assertThat(modifiers.getFirst().descriptionKey()).isEqualTo("ideology.authoritarianism");
        assertThat(modifiers.getLast().descriptionKey()).isEqualTo("sub_ideology.technocracy");
        assertThat(modifiers.getLast().source().refId()).isEqualTo("technocracy");
    }

    @Test
    void startDevelopmentNeedsEveryBranchWithinRange() {
        EnumMap<TechBranch, Integer> levels = new EnumMap<>(TechBranch.class);
        levels.put(TechBranch.ECONOMY, 0);
        levels.put(TechBranch.MILITARY, 0);
        levels.put(TechBranch.SOCIETY, 0);

        assertThatThrownBy(() -> new StartDevelopment(levels, new TreeSet<>(), 50, List.of()))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.MISSING_DEFINITION));
        levels.put(TechBranch.ENERGY_SCIENCE, 3);
        assertThatThrownBy(() -> new StartDevelopment(levels, new TreeSet<>(), 50, List.of()))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
        levels.put(TechBranch.ENERGY_SCIENCE, 2);
        assertThatThrownBy(() -> new StartDevelopment(levels, new TreeSet<>(), 101, List.of()))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
        assertThat(new StartDevelopment(levels, new TreeSet<>(), 50, List.of()).level(TechBranch.ENERGY_SCIENCE))
                .isEqualTo(2);
    }

    private static Map<TechBranch, RollRecord> byBranch(StartDevelopment development) {
        EnumMap<TechBranch, RollRecord> result = new EnumMap<>(TechBranch.class);
        for (int i = 0; i < TechBranch.values().length; i++) {
            result.put(TechBranch.values()[i], development.rolls().get(i));
        }
        return result;
    }

    @Test
    void populationShiftsEveryBranchWithoutChangingTheDraws() {
        ContentPack pack = TestChain.NEUTRAL;
        StartPopulation population = TestPopulation.population("huge", OutcomeTier.CRIT_SUCCESS);

        StartDevelopment plain = DevelopmentWheel.generate(Rng.of(11), pack, List.of());
        StartDevelopment shifted = DevelopmentWheel.generate(Rng.of(11), pack, List.of(), population);

        for (int i = 0; i < TechBranch.values().length; i++) {
            RollRecord roll = shifted.rolls().get(i);
            assertThat(roll.advantage()).isEqualTo(2 * TestChain.DEVELOPMENT_PER_STEP);
            assertThat(roll.modifiers())
                    .containsExactly(new AppliedModifier(
                            "population:huge", "population.huge", 2 * TestChain.DEVELOPMENT_PER_STEP));
            assertThat(roll.roll()).isEqualTo(plain.rolls().get(i).roll());
        }
        StartDevelopment partial = DevelopmentWheel.generate(
                Rng.of(11), pack, List.of(), TestPopulation.population("medium", OutcomeTier.PARTIAL));
        assertThat(partial).isEqualTo(plain);
    }
}
