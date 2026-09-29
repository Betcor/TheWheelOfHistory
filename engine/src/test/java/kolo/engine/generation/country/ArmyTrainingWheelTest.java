package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestHdi.gdp;
import static kolo.engine.generation.country.TestTraining.COMBAT_MODIFIERS;
import static kolo.engine.generation.country.TestTraining.IDS;
import static kolo.engine.generation.country.TestTraining.PACK;
import static kolo.engine.generation.country.TestTraining.QUALITIES;
import static kolo.engine.generation.country.TestTraining.REGIME;
import static kolo.engine.generation.country.TestTraining.TIERS;
import static kolo.engine.generation.country.TestTraining.WEIGHTS;
import static kolo.engine.generation.country.TestTraining.development;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import kolo.engine.content.TrainingLevelDef;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.rng.Rng;
import kolo.engine.state.Development;
import kolo.engine.state.Training;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.AppliedModifier;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ArmyTrainingWheelTest {

    private static final int SEEDS = 2_000;
    private static final int STRENGTH = 50;

    @Test
    void sectorsAreLevelsFromMilitiaToEliteWithContentWeightsTiersQualitiesAndTags() {
        List<Sector<TrainingLevelDef>> sectors = ArmyTrainingWheel.sectors(PACK);

        assertThat(sectors).extracting(Sector::id).containsExactly(IDS);
        assertThat(sectors.stream().mapToInt(Sector::weightBp).toArray()).isEqualTo(WEIGHTS);
        assertThat(sectors.stream().mapToInt(Sector::quality).toArray()).isEqualTo(QUALITIES);
        assertThat(sectors).extracting(Sector::tier).containsExactly(TIERS);
        assertThat(sectors.stream()
                        .mapToInt(sector -> sector.value().combatModifier())
                        .toArray())
                .isEqualTo(COMBAT_MODIFIERS);
        assertThat(sectors.getFirst().tags()).isEmpty();
        assertThat(sectors.getLast().tags()).containsExactly("elite_army");
    }

    @Test
    void sectorIdIsLevelNumber() {
        assertThat(ArmyTrainingWheel.sectorId(Training.MIN)).isEqualTo("level_1");
        assertThat(ArmyTrainingWheel.sectorId(Training.MAX)).isEqualTo("level_5");
    }

    @Test
    void advantageIsRegimeThenGdpThenMilitaryDevelopment() {
        Advantage advantage =
                ArmyTrainingWheel.advantage(PACK, REGIME.modifiers(), gdp(OutcomeTier.SUCCESS), development(2));

        assertThat(advantage.value()).isEqualTo(5 + 10 + 10 + 20);
        assertThat(advantage.modifiers())
                .extracting(AppliedModifier::sourceId, AppliedModifier::descriptionKey, AppliedModifier::value)
                .containsExactly(
                        tuple("ideology:authoritarianism:0", "ideology.authoritarianism", 5),
                        tuple("sub_ideology:military_junta:0", "sub_ideology.military_junta", 10),
                        tuple("gdp:rich", "gdp.rich", 10),
                        tuple("development:military", "development.military", 20));
    }

    @ParameterizedTest
    @EnumSource(OutcomeTier.class)
    void gdpContributionIsTierStepTimesCoefficient(OutcomeTier tier) {
        Advantage advantage = ArmyTrainingWheel.advantage(PACK, List.of(), gdp(tier), development(0));

        assertThat(advantage.value()).isEqualTo(tier.step() * TestTraining.GDP_ADVANTAGE);
        // Частковий ВВП і світовий рівень війська нічого не додають, тож і рядків пояснення немає.
        assertThat(advantage.modifiers()).hasSize(tier == OutcomeTier.PARTIAL ? 0 : 1);
    }

    @ParameterizedTest
    @ValueSource(ints = {Development.MIN, -1, Development.WORLD, 1, Development.MAX})
    void militaryDevelopmentContributionIsLevelTimesCoefficient(int military) {
        Advantage advantage =
                ArmyTrainingWheel.advantage(PACK, List.of(), gdp(OutcomeTier.PARTIAL), development(military));

        assertThat(advantage.value()).isEqualTo(military * TestTraining.DEVELOPMENT_ADVANTAGE);
        assertThat(advantage.modifiers()).hasSize(military == Development.WORLD ? 0 : 1);
    }

    @Test
    void otherBranchesDoNotAffectTraining() {
        StartDevelopment advancedEconomy = TestGdp.development(Development.MAX, Development.MAX);

        assertThat(ArmyTrainingWheel.advantage(PACK, List.of(), gdp(OutcomeTier.PARTIAL), advancedEconomy)
                        .modifiers())
                .isEmpty();
    }

    @Test
    void advantageIsClampedButExplainsEveryContribution() {
        Advantage advantage = ArmyTrainingWheel.advantage(
                PACK,
                List.of(TestTraining.modifier("event", -60)),
                gdp(OutcomeTier.CRIT_FAIL),
                development(Development.MIN));

        assertThat(advantage.value()).isEqualTo(Advantage.MIN);
        assertThat(advantage.modifiers()).extracting(AppliedModifier::value).containsExactly(-60, -20, -30);
    }

    @Test
    void resultMatchesRolledLevel() {
        boolean[] seen = new boolean[IDS.length];
        for (long seed = 0; seed < SEEDS; seed++) {
            StartArmyTraining training = ArmyTrainingWheel.generate(
                    Rng.of(seed), PACK, REGIME.modifiers(), gdp(OutcomeTier.FAIL), development(-1));
            TrainingLevelDef level = PACK.trainingLevel(training.level());
            RollRecord roll = training.rolls().getFirst();

            assertThat(training.rolls()).hasSize(1);
            assertThat(roll.kind()).isEqualTo(ArmyTrainingWheel.KIND);
            assertThat(roll.resultSectorId()).isEqualTo(ArmyTrainingWheel.sectorId(training.level()));
            assertThat(roll.advantage()).isEqualTo(15 - 10 - 10);
            assertThat(roll.turn()).isZero();
            assertThat(roll.season()).isNull();
            assertThat(training.combatModifier()).isEqualTo(level.combatModifier());
            assertThat(training.quality()).isEqualTo(level.quality());
            assertThat(training.tags()).containsExactlyElementsOf(new TreeSet<>(level.tags()));
            seen[training.level() - Training.MIN] = true;
        }
        assertThat(seen).containsOnly(true);
    }

    @Test
    void usesItsOwnStream() {
        int direct = Rng.of(42).fork("army_training").nextInt(Wheel.TOTAL_BP);
        StartArmyTraining training =
                ArmyTrainingWheel.generate(Rng.of(42), PACK, List.of(), gdp(OutcomeTier.PARTIAL), development(0));

        assertThat(training.rolls().getFirst().roll()).isEqualTo(direct);
    }

    @ParameterizedTest
    @ValueSource(ints = {Advantage.MIN, 0, Advantage.MAX})
    void advantageShiftsTowardsBetterTrainingAndKeepsExtremesPossible(int advantage) {
        int[] weights = Wheel.applyAdvantage(ArmyTrainingWheel.sectors(PACK), advantage, STRENGTH).stream()
                .mapToInt(Sector::weightBp)
                .toArray();

        assertThat(Arrays.stream(weights).sum()).isEqualTo(Wheel.TOTAL_BP);
        assertThat(weights[0]).isGreaterThanOrEqualTo(Wheel.MIN_CRITICAL_BP);
        assertThat(weights[4]).isGreaterThanOrEqualTo(Wheel.MIN_CRITICAL_BP);
        if (advantage > 0) {
            assertThat(weights[0]).isLessThan(WEIGHTS[0]);
            assertThat(weights[1]).isLessThan(WEIGHTS[1]);
            assertThat(weights[3]).isGreaterThan(WEIGHTS[3]);
            assertThat(weights[4]).isGreaterThan(WEIGHTS[4]);
        } else if (advantage < 0) {
            assertThat(weights[0]).isGreaterThan(WEIGHTS[0]);
            assertThat(weights[4]).isLessThan(WEIGHTS[4]);
        } else {
            assertThat(weights).isEqualTo(WEIGHTS);
        }
    }

    @Test
    void startArmyTrainingValidatesFields() {
        assertThat(new StartArmyTraining(3, 0, new TreeSet<>(List.of("b", "a")), 50, List.of()).tags())
                .containsExactly("a", "b");
        assertThat(new StartArmyTraining(
                                Training.MAX, TrainingLevelDef.MAX_COMBAT_MODIFIER, new TreeSet<>(), 100, List.of())
                        .level())
                .isEqualTo(Training.MAX);
        assertThat(new StartArmyTraining(
                                Training.MIN, -TrainingLevelDef.MAX_COMBAT_MODIFIER, new TreeSet<>(), 0, List.of())
                        .combatModifier())
                .isEqualTo(-TrainingLevelDef.MAX_COMBAT_MODIFIER);
        assertOutOfRange(() -> new StartArmyTraining(Training.MIN - 1, 0, new TreeSet<>(), 50, List.of()));
        assertOutOfRange(() -> new StartArmyTraining(Training.MAX + 1, 0, new TreeSet<>(), 50, List.of()));
        assertOutOfRange(() ->
                new StartArmyTraining(3, TrainingLevelDef.MAX_COMBAT_MODIFIER + 1, new TreeSet<>(), 50, List.of()));
        assertOutOfRange(() -> new StartArmyTraining(3, 0, new TreeSet<>(), 101, List.of()));
        assertThatThrownBy(() -> new StartArmyTraining(3, 0, null, 50, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    private static void assertOutOfRange(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
    }
}
