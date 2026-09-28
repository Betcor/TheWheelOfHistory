package kolo.engine.wheel;

import static kolo.engine.wheel.Wheels.CONSTRUCTION;
import static kolo.engine.wheel.Wheels.CONSTRUCTION_KIND;
import static kolo.engine.wheel.Wheels.sector;
import static kolo.engine.wheel.Wheels.weights;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.util.ArrayList;
import java.util.List;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.rng.Rng;
import kolo.engine.state.Season;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class WheelTest {

    @Test
    void zeroAdvantageKeepsNormalizedWheel() {
        assertThat(Wheel.applyAdvantage(CONSTRUCTION, 0, 100)).isEqualTo(CONSTRUCTION);
    }

    @Test
    void maxAdvantageDoublesSuccessAndRemovesFailButKeepsCriticalFail() {
        List<Sector<String>> result = Wheel.applyAdvantage(CONSTRUCTION, 100, 100);

        // Сирі ваги 0/0/2500/10000/2000; КП фіксується на 100, решта 9900 — найбільшими залишками.
        assertThat(weights(result)).containsExactly(100, 0, 1707, 6828, 1365);
    }

    @Test
    void minAdvantageMirrorsAndKeepsCriticalSuccess() {
        List<Sector<String>> result = Wheel.applyAdvantage(CONSTRUCTION, -100, 100);

        assertThat(weights(result)).containsExactly(1080, 4320, 4500, 0, 100);
    }

    @Test
    void partialAdvantageAndStrength() {
        // Множники 0,75 і 1,25; сума 11125 → частки з залишками .247/.988/.191/.977/.595, три пункти — П, У, КУ.
        List<Sector<String>> result = Wheel.applyAdvantage(CONSTRUCTION, 50, 50);

        assertThat(weights(result)).containsExactly(202, 809, 2247, 5618, 1124);
    }

    @Test
    void zeroStrengthIgnoresAdvantage() {
        assertThat(Wheel.applyAdvantage(CONSTRUCTION, 100, 0)).isEqualTo(CONSTRUCTION);
        assertThat(Wheel.applyAdvantage(CONSTRUCTION, -100, 0)).isEqualTo(CONSTRUCTION);
    }

    @Test
    void remainderTiesAreBrokenById() {
        List<Sector<String>> sectors = List.of(
                sector("bravo", 1, OutcomeTier.PARTIAL),
                sector("alpha", 1, OutcomeTier.PARTIAL),
                sector("charlie", 1, OutcomeTier.PARTIAL));

        assertThat(weights(Wheel.applyAdvantage(sectors, 0, 100))).containsExactly(3333, 3334, 3333);
    }

    @Test
    void smallWeightsAreScaledUp() {
        List<Sector<String>> sectors =
                List.of(sector("low", 1, OutcomeTier.FAIL), sector("high", 3, OutcomeTier.SUCCESS));

        assertThat(weights(Wheel.applyAdvantage(sectors, 0, 100))).containsExactly(2500, 7500);
    }

    @Test
    void criticalSectorsNeverDropBelowMinimum() {
        List<Sector<String>> sectors = List.of(
                sector("crit_fail", 0, OutcomeTier.CRIT_FAIL),
                sector("rest", 10_000, OutcomeTier.PARTIAL),
                sector("crit_success", 1, OutcomeTier.CRIT_SUCCESS));

        assertThat(weights(Wheel.applyAdvantage(sectors, 0, 100))).containsExactly(100, 9800, 100);
    }

    @Test
    void pinningCascadesWhenBudgetShrinks() {
        // Після фіксації першого КП бюджет решти зменшується й другий КП теж падає нижче мінімуму.
        List<Sector<String>> sectors = new ArrayList<>();
        sectors.add(sector("crit_a", 0, OutcomeTier.CRIT_FAIL));
        sectors.add(sector("crit_b", 100, OutcomeTier.CRIT_FAIL));
        sectors.add(sector("rest", 9900, OutcomeTier.PARTIAL));

        assertThat(weights(Wheel.applyAdvantage(sectors, 0, 100))).containsExactly(100, 100, 9800);
    }

    @Test
    void wheelOfOnlyCriticalSectorsIsValid() {
        List<Sector<String>> sectors =
                List.of(sector("doom", 1, OutcomeTier.CRIT_FAIL), sector("glory", 9999, OutcomeTier.CRIT_SUCCESS));

        assertThat(weights(Wheel.applyAdvantage(sectors, 0, 100))).containsExactly(100, 9900);
    }

    @Test
    void wipedOutWheelFallsBackToBaseProportions() {
        List<Sector<String>> sectors =
                List.of(sector("disaster", 500, OutcomeTier.CRIT_FAIL), sector("setback", 9500, OutcomeTier.FAIL));

        assertThat(weights(Wheel.applyAdvantage(sectors, 100, 100))).containsExactly(500, 9500);
    }

    @Test
    void preservesContentOrderAndSectorData() {
        List<Sector<String>> result = Wheel.applyAdvantage(CONSTRUCTION, 37, 80);

        for (int i = 0; i < CONSTRUCTION.size(); i++) {
            Sector<String> before = CONSTRUCTION.get(i);
            assertThat(result.get(i)).isEqualTo(before.withWeight(result.get(i).weightBp()));
        }
    }

    @Test
    void rejectsInvalidWheels() {
        assertRejected(() -> Wheel.applyAdvantage(List.of(), 0, 100), ErrorCode.EMPTY_COLLECTION);
        assertRejected(
                () -> Wheel.applyAdvantage(List.of(sector("zero", 0, OutcomeTier.PARTIAL)), 0, 100),
                ErrorCode.WHEEL_ZERO_WEIGHT);
        assertRejected(
                () -> Wheel.applyAdvantage(
                        List.of(sector("same", 1, OutcomeTier.FAIL), sector("same", 1, OutcomeTier.SUCCESS)), 0, 100),
                ErrorCode.DUPLICATE_ID);

        List<Sector<String>> tooManyCriticals = new ArrayList<>();
        for (int i = 0; i <= 100; i++) {
            tooManyCriticals.add(sector("crit_" + i, 1, OutcomeTier.CRIT_SUCCESS));
        }
        assertRejected(() -> Wheel.applyAdvantage(tooManyCriticals, 0, 100), ErrorCode.WHEEL_TOO_MANY_CRITICAL);
    }

    @Test
    void rejectsOutOfRangeAdvantageAndStrength() {
        assertRejected(() -> Wheel.applyAdvantage(CONSTRUCTION, 101, 100), ErrorCode.VALUE_OUT_OF_RANGE);
        assertRejected(() -> Wheel.applyAdvantage(CONSTRUCTION, -101, 100), ErrorCode.VALUE_OUT_OF_RANGE);
        assertRejected(() -> Wheel.applyAdvantage(CONSTRUCTION, 0, 101), ErrorCode.VALUE_OUT_OF_RANGE);
        assertRejected(() -> Wheel.applyAdvantage(CONSTRUCTION, 0, -1), ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void outOfRangeDetailsNameTheField() {
        assertThatThrownBy(() -> Wheel.applyAdvantage(CONSTRUCTION, 0, 101))
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        e -> assertThat(e.details())
                                .containsExactly(
                                        entry("field", "strength"),
                                        entry("max", 100L),
                                        entry("min", 0L),
                                        entry("value", 101L)));
    }

    private static void assertRejected(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void spinPicksSectorByCumulativeWeight() {
        for (long seed = 0; seed < 200; seed++) {
            WheelSpin<String> spin =
                    Wheel.spin(Rng.of(seed), CONSTRUCTION_KIND, CONSTRUCTION, Advantage.NONE, 100, 3, null);
            int roll = Rng.of(seed).nextInt(Wheel.TOTAL_BP);

            assertThat(spin.record().roll()).isEqualTo(roll);
            assertThat(spin.value()).isEqualTo(expectedByRoll(roll));
            assertThat(spin.record().resultSectorId()).isEqualTo(spin.outcome().id());
        }
    }

    @Test
    void spinNeverPicksZeroWeightSector() {
        List<Sector<String>> sectors = List.of(
                sector("never", 0, OutcomeTier.FAIL),
                sector("always", 10_000, OutcomeTier.SUCCESS),
                sector("never_either", 0, OutcomeTier.PARTIAL));
        Rng rng = Rng.of(7);

        for (int i = 0; i < 1000; i++) {
            assertThat(Wheel.spin(rng, CONSTRUCTION_KIND, sectors, Advantage.NONE, 100, 0, null)
                            .value())
                    .isEqualTo("always");
        }
    }

    @Test
    void recordDescribesTheSpin() {
        Advantage advantage = Advantage.of(List.of(
                new AppliedModifier("training", "modifier.training", 15),
                new AppliedModifier("mountains", "modifier.terrain.mountains", -10)));

        WheelSpin<String> spin =
                Wheel.spin(Rng.of(42), CONSTRUCTION_KIND, CONSTRUCTION, advantage, 60, 12, Season.WINTER);
        RollRecord record = spin.record();

        assertThat(record.kind()).isEqualTo(CONSTRUCTION_KIND);
        assertThat(record.advantage()).isEqualTo(5);
        assertThat(record.modifiers()).isEqualTo(advantage.modifiers());
        assertThat(record.turn()).isEqualTo(12);
        assertThat(record.season()).isEqualTo(Season.WINTER);
        assertThat(record.sectors())
                .extracting(RolledSector::weightBp)
                .containsExactly(boxed(weights(Wheel.applyAdvantage(CONSTRUCTION, 5, 60))));
        assertThat(record.result().id()).isEqualTo(spin.outcome().id());
        assertThat(record.result().tier()).isEqualTo(spin.tier());
    }

    private static Integer[] boxed(int[] values) {
        Integer[] boxed = new Integer[values.length];
        for (int i = 0; i < values.length; i++) {
            boxed[i] = values[i];
        }
        return boxed;
    }

    private static String expectedByRoll(int roll) {
        if (roll < 300) {
            return "crit_fail";
        } else if (roll < 1500) {
            return "fail";
        } else if (roll < 4000) {
            return "partial";
        } else if (roll < 9000) {
            return "success";
        }
        return "crit_success";
    }
}
