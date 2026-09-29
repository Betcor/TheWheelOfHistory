package kolo.engine.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.state.PowerCorridor;
import kolo.engine.wheel.WheelKind;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class BalanceDefinitionsTest {

    private static final WheelKind ECONOMIC_CYCLE = new WheelKind("economic_cycle");

    @Test
    void wheelStrengthFallsBackToDefault() {
        WheelBalanceDef wheel = new WheelBalanceDef(50, new TreeMap<>(Map.of(ECONOMIC_CYCLE, 80)), List.of(20, 10, 5));

        assertThat(wheel.strength(ECONOMIC_CYCLE)).isEqualTo(80);
        assertThat(wheel.strength(new WheelKind("construction"))).isEqualTo(50);
    }

    @Test
    void wheelStrengthIsWithinWheelLimits() {
        assertFails(() -> new WheelBalanceDef(101, new TreeMap<>(), List.of(10)), ErrorCode.VALUE_OUT_OF_RANGE);
        assertThatThrownBy(() -> new WheelBalanceDef(50, new TreeMap<>(Map.of(ECONOMIC_CYCLE, -1)), List.of(10)))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE);
                    assertThat(e.details()).contains(entry("field", "wheel.strength.economic_cycle"));
                });
    }

    @Test
    void investmentsHaveDiminishingReturns() {
        WheelBalanceDef wheel = new WheelBalanceDef(50, new TreeMap<>(), List.of(20, 12, 7, 4));

        assertThat(wheel.maxInvestments()).isEqualTo(4);
        assertThat(wheel.investmentAdvantage(0)).isZero();
        assertThat(wheel.investmentAdvantage(1)).isEqualTo(20);
        assertThat(wheel.investmentAdvantage(4)).isEqualTo(43);
        assertFails(() -> wheel.investmentAdvantage(5), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> wheel.investmentAdvantage(-1), ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void investmentAdvantageIsCappedAtWheelMaximum() {
        WheelBalanceDef wheel = new WheelBalanceDef(50, new TreeMap<>(), List.of(90, 60));

        assertThat(wheel.investmentAdvantage(2)).isEqualTo(100);
    }

    @Test
    void investmentCurveMustStrictlyDecrease() {
        assertFails(() -> new WheelBalanceDef(50, new TreeMap<>(), List.of()), ErrorCode.EMPTY_COLLECTION);
        assertThatThrownBy(() -> new WheelBalanceDef(50, new TreeMap<>(), List.of(20, 20)))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE);
                    assertThat(e.details()).contains(entry("field", "wheel.investment_curve[1]"), entry("max", 19L));
                });
        assertFails(() -> new WheelBalanceDef(50, new TreeMap<>(), List.of(10, 0)), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new WheelBalanceDef(50, new TreeMap<>(), List.of(101)), ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void wheelCollectionsAreDefensivelyCopied() {
        TreeMap<WheelKind, Integer> strengths = new TreeMap<>(Map.of(ECONOMIC_CYCLE, 80));
        List<Integer> curve = new ArrayList<>(List.of(20, 10));
        WheelBalanceDef wheel = new WheelBalanceDef(50, strengths, curve);
        strengths.clear();
        curve.add(5);

        assertThat(wheel.strength(ECONOMIC_CYCLE)).isEqualTo(80);
        assertThat(wheel.investmentCurve()).containsExactly(20, 10);
        assertThatThrownBy(() -> wheel.strengths().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void streakThresholdsClassifyQuality() {
        StreakRulesDef streaks = new StreakRulesDef(85, 15, 3);

        assertThat(streaks.isVeryGood(85)).isTrue();
        assertThat(streaks.isVeryGood(84)).isFalse();
        assertThat(streaks.isVeryBad(15)).isTrue();
        assertThat(streaks.isVeryBad(16)).isFalse();
    }

    @Test
    void streakThresholdsDoNotOverlap() {
        assertFails(() -> new StreakRulesDef(50, 50, 3), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new StreakRulesDef(101, 15, 3), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new StreakRulesDef(85, -1, 3), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new StreakRulesDef(85, 15, 1), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new StreakRulesDef(85, 15, StreakRulesDef.MAX_LENGTH + 1), ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void medianRangeContainsTheMedian() {
        assertThat(new MedianRange(50, 200).contains(new MedianRange(75, 133))).isTrue();
        assertThat(new MedianRange(50, 200).contains(new MedianRange(40, 133))).isFalse();
        assertThat(new MedianRange(100, 100).contains(new MedianRange(100, 100)))
                .isTrue();
        assertFails(() -> new MedianRange(0, 200), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new MedianRange(101, 200), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new MedianRange(50, 99), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new MedianRange(50, MedianRange.MAX_PCT + 1), ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void npcCorridorIsNeverNarrowerThanPlayers() {
        PowerCorridorDef classic =
                new PowerCorridorDef(PowerCorridor.CLASSIC, new MedianRange(50, 200), new MedianRange(33, 300));

        assertThat(classic.range(false)).isEqualTo(new MedianRange(50, 200));
        assertThat(classic.range(true)).isEqualTo(new MedianRange(33, 300));
        assertThatThrownBy(() ->
                        new PowerCorridorDef(PowerCorridor.CLASSIC, new MedianRange(50, 200), new MedianRange(60, 300)))
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        e -> assertThat(e.details()).contains(entry("field", "power_corridor.classic.npc.min_pct")));
        assertFails(
                () -> new PowerCorridorDef(PowerCorridor.CLASSIC, new MedianRange(50, 200), new MedianRange(33, 150)),
                ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void countRangeIsOrdered() {
        assertThat(new CountRange(2, 4).contains(3)).isTrue();
        assertThat(new CountRange(2, 4).contains(5)).isFalse();
        assertFails(() -> new CountRange(-1, 4), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new CountRange(4, 2), ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void generationCountsAreAtLeastOneAndBounded() {
        CountRange fragments = new CountRange(2, 4);
        CountRange people = new CountRange(1, 3);
        CountRange warheads = new CountRange(2, 10);
        assertFails(
                () -> new GenerationBalanceDef(
                        new CountRange(0, 4),
                        people,
                        warheads,
                        10,
                        10,
                        15,
                        10,
                        10,
                        10,
                        new CountRange(1, 3),
                        new CountRange(25, 70)),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new GenerationBalanceDef(
                        fragments,
                        new CountRange(1, GenerationBalanceDef.MAX_COUNT + 1),
                        warheads,
                        10,
                        10,
                        15,
                        10,
                        10,
                        10,
                        new CountRange(1, 3),
                        new CountRange(25, 70)),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new GenerationBalanceDef(
                        fragments,
                        people,
                        new CountRange(0, 10),
                        10,
                        10,
                        15,
                        10,
                        10,
                        10,
                        new CountRange(1, 3),
                        new CountRange(25, 70)),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new GenerationBalanceDef(
                        fragments,
                        people,
                        new CountRange(2, GenerationBalanceDef.MAX_WARHEADS + 1),
                        10,
                        10,
                        15,
                        10,
                        10,
                        10,
                        new CountRange(1, 3),
                        new CountRange(25, 70)),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertThat(new GenerationBalanceDef(
                                fragments,
                                people,
                                new CountRange(1, GenerationBalanceDef.MAX_WARHEADS),
                                0,
                                0,
                                15,
                                10,
                                10,
                                10,
                                new CountRange(1, 3),
                                new CountRange(25, 70))
                        .warheads()
                        .max())
                .isEqualTo(GenerationBalanceDef.MAX_WARHEADS);
    }

    @Test
    void nuclearEnergyAdvantageIsWithinAdvantageRange() {
        CountRange count = new CountRange(1, 3);
        assertFails(
                () -> new GenerationBalanceDef(
                        count, count, count, -1, 10, 15, 10, 10, 10, new CountRange(1, 3), new CountRange(25, 70)),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new GenerationBalanceDef(
                        count, count, count, 101, 10, 15, 10, 10, 10, new CountRange(1, 3), new CountRange(25, 70)),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertThat(new GenerationBalanceDef(
                                count,
                                count,
                                count,
                                100,
                                10,
                                15,
                                10,
                                10,
                                10,
                                new CountRange(1, 3),
                                new CountRange(25, 70))
                        .nuclearEnergyAdvantage())
                .isEqualTo(100);
    }

    @Test
    void hdiGdpAdvantageIsWithinAdvantageRange() {
        CountRange count = new CountRange(1, 3);
        assertFails(
                () -> new GenerationBalanceDef(
                        count, count, count, 10, 10, -1, 10, 10, 10, new CountRange(1, 3), new CountRange(25, 70)),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new GenerationBalanceDef(
                        count, count, count, 10, 10, 101, 10, 10, 10, new CountRange(1, 3), new CountRange(25, 70)),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertThat(new GenerationBalanceDef(
                                count,
                                count,
                                count,
                                10,
                                10,
                                100,
                                10,
                                10,
                                10,
                                new CountRange(1, 3),
                                new CountRange(25, 70))
                        .hdiGdpAdvantage())
                .isEqualTo(100);
    }

    @Test
    void armySizeGdpAdvantageIsWithinAdvantageRange() {
        CountRange count = new CountRange(1, 3);
        assertFails(
                () -> new GenerationBalanceDef(
                        count, count, count, 10, 10, 15, -1, 10, 10, new CountRange(1, 3), new CountRange(25, 70)),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new GenerationBalanceDef(
                        count, count, count, 10, 10, 15, 101, 10, 10, new CountRange(1, 3), new CountRange(25, 70)),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertThat(new GenerationBalanceDef(
                                count,
                                count,
                                count,
                                10,
                                10,
                                15,
                                100,
                                10,
                                10,
                                new CountRange(1, 3),
                                new CountRange(25, 70))
                        .armySizeGdpAdvantage())
                .isEqualTo(100);
    }

    @Test
    void armyTrainingAdvantagesAreWithinAdvantageRange() {
        CountRange count = new CountRange(1, 3);
        assertFails(
                () -> new GenerationBalanceDef(
                        count, count, count, 10, 10, 15, 10, -1, 10, new CountRange(1, 3), new CountRange(25, 70)),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new GenerationBalanceDef(
                        count, count, count, 10, 10, 15, 10, 101, 10, new CountRange(1, 3), new CountRange(25, 70)),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new GenerationBalanceDef(
                        count, count, count, 10, 10, 15, 10, 10, -1, new CountRange(1, 3), new CountRange(25, 70)),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new GenerationBalanceDef(
                        count, count, count, 10, 10, 15, 10, 10, 101, new CountRange(1, 3), new CountRange(25, 70)),
                ErrorCode.VALUE_OUT_OF_RANGE);
        GenerationBalanceDef max = new GenerationBalanceDef(
                count, count, count, 10, 10, 15, 10, 100, 100, new CountRange(1, 3), new CountRange(25, 70));
        assertThat(max.armyTrainingGdpAdvantage()).isEqualTo(100);
        assertThat(max.armyTrainingDevelopmentAdvantage()).isEqualTo(100);
    }

    @Test
    void gdpDevelopmentAdvantageIsWithinAdvantageRange() {
        CountRange count = new CountRange(1, 3);
        assertFails(
                () -> new GenerationBalanceDef(
                        count, count, count, 10, -1, 15, 10, 10, 10, new CountRange(1, 3), new CountRange(25, 70)),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new GenerationBalanceDef(
                        count, count, count, 10, 101, 15, 10, 10, 10, new CountRange(1, 3), new CountRange(25, 70)),
                ErrorCode.VALUE_OUT_OF_RANGE);
        assertThat(new GenerationBalanceDef(
                                count,
                                count,
                                count,
                                10,
                                100,
                                15,
                                10,
                                10,
                                10,
                                new CountRange(1, 3),
                                new CountRange(25, 70))
                        .gdpDevelopmentAdvantage())
                .isEqualTo(100);
    }

    @Test
    void everyCorridorMustBeDefinedOnce() {
        BalanceDef balance = TestContent.balance();
        assertThat(balance.corridors().keySet()).containsExactly(PowerCorridor.values());
        assertThat(balance.corridor(PowerCorridor.FULL_CHAOS).corridor()).isEqualTo(PowerCorridor.FULL_CHAOS);

        assertThatThrownBy(() -> BalanceDef.of(
                        balance.wheel(),
                        balance.streaks(),
                        List.of(
                                TestContent.corridor(PowerCorridor.CLASSIC),
                                TestContent.corridor(PowerCorridor.EQUAL_CHANCES)),
                        balance.generation()))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.MISSING_DEFINITION);
                    assertThat(e.details())
                            .containsExactly(entry("field", "power_corridors"), entry("value", "full_chaos"));
                });
        List<PowerCorridorDef> twice = new ArrayList<>(
                Arrays.stream(PowerCorridor.values()).map(TestContent::corridor).toList());
        twice.add(TestContent.corridor(PowerCorridor.CLASSIC));
        assertFails(
                () -> BalanceDef.of(balance.wheel(), balance.streaks(), twice, balance.generation()),
                ErrorCode.DUPLICATE_ID);
    }

    @Test
    void corridorMustBeStoredUnderItsOwnKey() {
        BalanceDef balance = TestContent.balance();
        TreeMap<PowerCorridor, PowerCorridorDef> swapped = new TreeMap<>(balance.corridors());
        swapped.put(PowerCorridor.CLASSIC, TestContent.corridor(PowerCorridor.FULL_CHAOS));

        assertFails(
                () -> new BalanceDef(balance.wheel(), balance.streaks(), swapped, balance.generation()),
                ErrorCode.UNKNOWN_REFERENCE);
    }

    private static void assertFails(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
