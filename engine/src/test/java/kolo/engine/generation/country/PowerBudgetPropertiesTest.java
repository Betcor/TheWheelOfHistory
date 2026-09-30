package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kolo.engine.content.BalanceDef;
import kolo.engine.content.MedianRange;
import kolo.engine.content.PowerComponent;
import kolo.engine.content.TestPower;
import kolo.engine.state.PowerCorridor;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/** Властивості бюджету сили на довільних якостях складників у порядку ланцюжка. */
class PowerBudgetPropertiesTest {

    private static final BalanceDef BALANCE = TestPower.balance(TestPower.budget(2));

    @Property
    void strengthIsMeanQualityAndShiftPointsToTheCorridor(
            @ForAll @Size(min = 1, max = 8) List<@IntRange(min = 0, max = 100) Integer> qualities,
            @ForAll PowerCorridor corridor,
            @ForAll boolean npc) {
        PowerBudget budget = PowerBudget.start(corridor, npc);
        MedianRange range = budget.range(BALANCE);
        int sum = 0;
        for (int i = 0; i < qualities.size(); i++) {
            budget = budget.add(BALANCE, PowerComponent.values()[i], qualities.get(i));
            sum += qualities.get(i);

            // Усі ваги однакові: сила — проста середня якість у відсотках медіани 50.
            assertThat(budget.strengthPct()).isEqualTo(Math.floorDiv(sum * 2, i + 1));
            int strength = budget.strengthPct();
            int advantage = budget.advantage();
            assertThat(Math.abs(advantage)).isLessThanOrEqualTo(BALANCE.power().maxAdvantage());
            if (strength < range.minPct()) {
                assertThat(advantage).isNotNegative();
            } else if (strength > range.maxPct()) {
                assertThat(advantage).isNotPositive();
            } else {
                assertThat(advantage).isZero();
            }
        }
        assertThat(budget.steps()).hasSize(qualities.size());
    }

    @Property
    void sameQualitiesGiveSameBudget(
            @ForAll @Size(min = 1, max = 8) List<@IntRange(min = 0, max = 100) Integer> qualities) {
        assertThat(budget(qualities)).isEqualTo(budget(qualities));
    }

    private static PowerBudget budget(List<Integer> qualities) {
        PowerBudget budget = PowerBudget.start(PowerCorridor.EQUAL_CHANCES, false);
        for (int i = 0; i < qualities.size(); i++) {
            budget = budget.add(BALANCE, PowerComponent.values()[i], qualities.get(i));
        }
        return budget;
    }
}
