package kolo.engine.content;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import kolo.engine.error.Checks;
import kolo.engine.wheel.Advantage;

/**
 * Бюджет сили при генерації держави (GD §4.11): як рахувати проміжну силу й як зсувати наступні колеса, коли вона
 * виходить за коридор ({@link PowerCorridorDef}).
 *
 * <p>Сила — зважена середня якість уже обернених складників у відсотках нейтральної якості: {@code Σ(вага × якість) ×
 * 100 / (Σ ваг × medianQuality)}; поки жодного складника з вагою немає — 100%. За межею коридору наступні
 * складники отримують перевагу до середини: вихід за межу у відсоткових пунктах × {@code advantagePct} / 100 вниз,
 * не більше {@code maxAdvantage}.
 *
 * @param medianQuality нейтральна якість — сила 100% (медіана), {@code 1..99}
 * @param weights вага кожного складника, {@code 0..}{@value #MAX_WEIGHT}; нуль — складник не рахується; хоча б
 *     одна ненульова
 * @param advantagePct перевага за 100 відсоткових пунктів виходу за коридор, {@code 1..}{@value #MAX_ADVANTAGE_PCT}
 * @param maxAdvantage найбільший зсув, {@code 1..}{@value Advantage#MAX}
 */
public record PowerBudgetDef(
        int medianQuality, SortedMap<PowerComponent, Integer> weights, int advantagePct, int maxAdvantage) {

    public static final int MAX_WEIGHT = 100;

    /** Більше — і вихід на кілька пунктів уже давав би повну перевагу: зсув перестав би бути м'яким. */
    public static final int MAX_ADVANTAGE_PCT = 1_000;

    public PowerBudgetDef {
        Checks.inRange("power_budget.median_quality", medianQuality, 1, 99);
        TreeMap<PowerComponent, Integer> copy = new TreeMap<>();
        int sum = 0;
        for (Map.Entry<PowerComponent, Integer> entry : weights.entrySet()) {
            PowerComponent component = Objects.requireNonNull(entry.getKey(), "component");
            int weight = Objects.requireNonNull(entry.getValue(), "weight");
            Checks.inRange("power_budget.weights." + component.key(), weight, 0, MAX_WEIGHT);
            copy.put(component, weight);
            sum += weight;
        }
        weights = Defs.complete("power_budget.weights", copy, List.of(PowerComponent.values()), PowerComponent::key);
        Checks.inRange("power_budget.weights", sum, 1, MAX_WEIGHT * PowerComponent.values().length);
        Checks.inRange("power_budget.advantage_pct", advantagePct, 1, MAX_ADVANTAGE_PCT);
        Checks.inRange("power_budget.max_advantage", maxAdvantage, 1, Advantage.MAX);
    }

    public int weight(PowerComponent component) {
        return weights.get(Objects.requireNonNull(component, "component"));
    }

    /**
     * Зсув для сили за межами коридору: додатний — держава заслабка, від'ємний — засильна, нуль — у коридорі.
     *
     * @param strengthPct проміжна сила, % медіани
     */
    public int advantage(int strengthPct, MedianRange range) {
        Objects.requireNonNull(range, "range");
        if (strengthPct < range.minPct()) {
            return shift(range.minPct() - strengthPct);
        }
        if (strengthPct > range.maxPct()) {
            return -shift(strengthPct - range.maxPct());
        }
        return 0;
    }

    private int shift(int gapPct) {
        return (int) Math.min(maxAdvantage, Math.floorDiv((long) gapPct * advantagePct, 100));
    }
}
