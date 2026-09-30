package kolo.engine.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.util.TreeMap;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class PowerBudgetDefinitionsTest {

    private static final MedianRange RANGE = new MedianRange(75, 133);

    @Test
    void keepsWeightOfEveryComponent() {
        PowerBudgetDef def = TestPower.budget(3);

        assertThat(def.weights().keySet()).containsExactly(PowerComponent.values());
        assertThat(def.weight(PowerComponent.ARMY_SIZE)).isEqualTo(3);
        assertThat(PowerComponent.ARMY_TRAINING.key()).isEqualTo("army_training");
    }

    @Test
    void everyComponentMustHaveWeight() {
        TreeMap<PowerComponent, Integer> weights = weights(1);
        weights.remove(PowerComponent.HDI);

        assertThatThrownBy(() -> new PowerBudgetDef(50, weights, 50, 30))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.MISSING_DEFINITION);
                    assertThat(e.details()).contains(entry("field", "power_budget.weights"), entry("value", "hdi"));
                });
    }

    @Test
    void weightsAreBoundedAndNotAllZero() {
        TreeMap<PowerComponent, Integer> tooHeavy = weights(1);
        tooHeavy.put(PowerComponent.GDP, PowerBudgetDef.MAX_WEIGHT + 1);
        assertOutOfRange(() -> new PowerBudgetDef(50, tooHeavy, 50, 30), "power_budget.weights.gdp");
        TreeMap<PowerComponent, Integer> negative = weights(1);
        negative.put(PowerComponent.GDP, -1);
        assertOutOfRange(() -> new PowerBudgetDef(50, negative, 50, 30), "power_budget.weights.gdp");

        assertOutOfRange(() -> new PowerBudgetDef(50, weights(0), 50, 30), "power_budget.weights");
        TreeMap<PowerComponent, Integer> one = weights(0);
        one.put(PowerComponent.AREA, 1);
        assertThat(new PowerBudgetDef(50, one, 50, 30).weight(PowerComponent.AREA))
                .isEqualTo(1);
    }

    @Test
    void numbersAreBounded() {
        assertOutOfRange(() -> new PowerBudgetDef(0, weights(1), 50, 30), "power_budget.median_quality");
        assertOutOfRange(() -> new PowerBudgetDef(100, weights(1), 50, 30), "power_budget.median_quality");
        assertOutOfRange(() -> new PowerBudgetDef(50, weights(1), 0, 30), "power_budget.advantage_pct");
        assertOutOfRange(
                () -> new PowerBudgetDef(50, weights(1), PowerBudgetDef.MAX_ADVANTAGE_PCT + 1, 30),
                "power_budget.advantage_pct");
        assertOutOfRange(() -> new PowerBudgetDef(50, weights(1), 50, 0), "power_budget.max_advantage");
        assertOutOfRange(() -> new PowerBudgetDef(50, weights(1), 50, 101), "power_budget.max_advantage");
    }

    @Test
    void insideCorridorThereIsNoShift() {
        PowerBudgetDef def = TestPower.budget(1);

        assertThat(def.advantage(75, RANGE)).isZero();
        assertThat(def.advantage(100, RANGE)).isZero();
        assertThat(def.advantage(133, RANGE)).isZero();
    }

    @Test
    void shiftGrowsWithGapAndPointsToTheMiddle() {
        // 50 за 100 п. п.: вихід на 1 п. п. — ще нуль (вниз), на 10 — 5, на 11 — 5.
        PowerBudgetDef def = TestPower.budget(1);

        assertThat(def.advantage(74, RANGE)).isZero();
        assertThat(def.advantage(65, RANGE)).isEqualTo(5);
        assertThat(def.advantage(64, RANGE)).isEqualTo(5);
        assertThat(def.advantage(143, RANGE)).isEqualTo(-5);
        assertThat(def.advantage(144, RANGE)).isEqualTo(-5);
    }

    @Test
    void shiftIsCapped() {
        PowerBudgetDef def = TestPower.budget(1);

        assertThat(def.advantage(0, RANGE)).isEqualTo(30);
        assertThat(def.advantage(Integer.MAX_VALUE, RANGE)).isEqualTo(-30);
    }

    private static TreeMap<PowerComponent, Integer> weights(int weight) {
        TreeMap<PowerComponent, Integer> weights = new TreeMap<>();
        for (PowerComponent component : PowerComponent.values()) {
            weights.put(component, weight);
        }
        return weights;
    }

    private static void assertOutOfRange(ThrowingCallable call, String field) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ValidationException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE);
            assertThat(e.details()).contains(entry("field", field));
        });
    }
}
