package kolo.engine.content;

import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

/** Бюджет сили для тестових пакетів: кожен складник з вагою 1, нейтральна якість 50. */
public final class TestPower {

    public static final PowerBudgetDef BUDGET = budget(1);

    /** Таймери ходу тестових пакетів: живі 2 і 5 хв, асинхронні 12 і 24 год. */
    public static final TurnTimersDef TIMERS = new TurnTimersDef(List.of(120, 300), List.of(43_200, 86_400));

    private TestPower() {}

    /** Баланс тестового пакета з цим бюджетом: коридори гравців 50..200, NPC 25..400 % медіани. */
    public static BalanceDef balance(PowerBudgetDef power) {
        BalanceDef balance = TestContent.balance();
        return new BalanceDef(
                balance.wheel(),
                balance.streaks(),
                balance.corridors(),
                balance.generation(),
                balance.religion(),
                balance.world(),
                balance.resources(),
                power,
                balance.timers());
    }

    /** Усі складники з однаковою вагою, перевага 50 за 100 п. п., не більше 30. */
    public static PowerBudgetDef budget(int weight) {
        TreeMap<PowerComponent, Integer> weights = new TreeMap<>();
        Arrays.stream(PowerComponent.values()).forEach(component -> weights.put(component, weight));
        return new PowerBudgetDef(50, weights, 50, 30);
    }
}
