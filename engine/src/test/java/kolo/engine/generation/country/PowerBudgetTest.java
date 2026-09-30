package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.TreeMap;
import kolo.engine.content.BalanceDef;
import kolo.engine.content.PowerBudgetDef;
import kolo.engine.content.PowerComponent;
import kolo.engine.content.TestPower;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierSource;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.modifier.SourceKind;
import kolo.engine.state.PowerCorridor;
import kolo.engine.state.TechBranch;
import kolo.engine.wheel.WheelKind;
import org.junit.jupiter.api.Test;

class PowerBudgetTest {

    /** Ваги 1, медіана 50, коридор гравців 50..200, NPC 25..400; 50 за 100 п. п., не більше 30. */
    private static final BalanceDef BALANCE = TestPower.balance(TestPower.BUDGET);

    private static final PowerBudget PLAYER = PowerBudget.start(PowerCorridor.CLASSIC, false);

    @Test
    void startsAtMedianWithoutShift() {
        assertThat(PLAYER.strengthPct()).isEqualTo(PowerBudget.MEDIAN_PCT);
        assertThat(PLAYER.advantage()).isZero();
        assertThat(PLAYER.steps()).isEmpty();
        assertThat(PLAYER.modifiers(List.of(GdpWheel.KIND))).isEmpty();
    }

    @Test
    void strengthIsWeightedMeanQualityInPercentOfMedian() {
        TreeMap<PowerComponent, Integer> weights = new TreeMap<>(TestPower.BUDGET.weights());
        weights.put(PowerComponent.GDP, 3);
        BalanceDef balance = TestPower.balance(new PowerBudgetDef(50, weights, 50, 30));

        PowerBudget budget = PLAYER.add(balance, PowerComponent.AREA, 40).add(balance, PowerComponent.GDP, 80);

        // (1·40 + 3·80) · 100 / (4 · 50) = 140.
        assertThat(budget.strengthPct()).isEqualTo(140);
        assertThat(budget.steps())
                .containsExactly(
                        new PowerStep(PowerComponent.AREA, 40, 80, 0), new PowerStep(PowerComponent.GDP, 80, 140, 0));
    }

    @Test
    void zeroWeightComponentDoesNotChangeStrength() {
        TreeMap<PowerComponent, Integer> weights = new TreeMap<>(TestPower.BUDGET.weights());
        weights.put(PowerComponent.AREA, 0);
        BalanceDef balance = TestPower.balance(new PowerBudgetDef(50, weights, 50, 30));

        PowerBudget area = PLAYER.add(balance, PowerComponent.AREA, 0);
        assertThat(area.strengthPct()).isEqualTo(PowerBudget.MEDIAN_PCT);
        assertThat(area.advantage()).isZero();
        assertThat(area.add(balance, PowerComponent.POPULATION, 60).strengthPct())
                .isEqualTo(120);
    }

    @Test
    void weakCountryIsShiftedUpAsModifierOfEachWheel() {
        // Якість 20 → 40%: на 10 п. п. нижче за 50% → +5.
        PowerBudget budget = PLAYER.add(BALANCE, PowerComponent.AREA, 20);

        assertThat(budget.strengthPct()).isEqualTo(40);
        assertThat(budget.advantage()).isEqualTo(5);
        WheelKind first = DevelopmentWheel.kind(TechBranch.MILITARY);
        assertThat(budget.modifiers(List.of(PopulationWheel.KIND, first)))
                .containsExactly(modifier(PopulationWheel.KIND, 5, "classic"), modifier(first, 5, "classic"));
    }

    @Test
    void strongCountryIsShiftedDown() {
        // Медіана 25: якість 100 → 400%, на 200 п. п. вище за 200% → −100, обрізано до −30.
        BalanceDef balance = TestPower.balance(new PowerBudgetDef(25, TestPower.BUDGET.weights(), 50, 30));

        PowerBudget budget = PLAYER.add(balance, PowerComponent.GDP, 100);

        assertThat(budget.strengthPct()).isEqualTo(400);
        assertThat(budget.advantage()).isEqualTo(-30);
        assertThat(budget.modifiers(List.of(HdiWheel.KIND))).containsExactly(modifier(HdiWheel.KIND, -30, "classic"));
    }

    @Test
    void npcCorridorIsWider() {
        PowerBudget npc = PowerBudget.start(PowerCorridor.FULL_CHAOS, true);

        assertThat(npc.range(BALANCE))
                .isEqualTo(BALANCE.corridor(PowerCorridor.FULL_CHAOS).npc());
        assertThat(PLAYER.add(BALANCE, PowerComponent.AREA, 20).advantage()).isEqualTo(5);
        assertThat(npc.add(BALANCE, PowerComponent.AREA, 20).advantage()).isZero();
        // 10% — на 15 п. п. нижче за 25% → +7.
        assertThat(npc.add(BALANCE, PowerComponent.AREA, 5).advantage()).isEqualTo(7);
    }

    @Test
    void shiftFollowsTheLatestStrength() {
        PowerBudget budget = PLAYER.add(BALANCE, PowerComponent.AREA, 20).add(BALANCE, PowerComponent.POPULATION, 80);

        // (20 + 80) / 2 = 50 → 100%: знову в коридорі.
        assertThat(budget.strengthPct()).isEqualTo(100);
        assertThat(budget.advantage()).isZero();
        assertThat(budget.modifiers(List.of(GdpWheel.KIND))).isEmpty();
    }

    @Test
    void componentCountsOnce() {
        PowerBudget budget = PLAYER.add(BALANCE, PowerComponent.AREA, 50);

        assertThatThrownBy(() -> budget.add(BALANCE, PowerComponent.AREA, 50))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_ID));
    }

    @Test
    void stepIsValidated() {
        assertThatThrownBy(() -> new PowerStep(PowerComponent.AREA, 101, 100, 0))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new PowerStep(PowerComponent.AREA, 50, -1, 0)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new PowerStep(PowerComponent.AREA, 50, 100, 101))
                .isInstanceOf(ValidationException.class);
    }

    private static Modifier modifier(WheelKind kind, int value, String corridor) {
        return new Modifier(
                "power_corridor:" + kind.id(),
                new ModifierSource(SourceKind.POWER_BUDGET, corridor),
                ModifierTarget.wheel(kind),
                value,
                null,
                PowerBudget.DESCRIPTION_KEY);
    }
}
