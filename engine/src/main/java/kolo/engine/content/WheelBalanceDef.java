package kolo.engine.content;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.Wheel;
import kolo.engine.wheel.WheelKind;

/**
 * Баланс коліс (GD §2.3–2.4): сила переваги й віддача від вкладень.
 *
 * @param defaultStrength сила переваги {@code k} колеса, для якого не задано окремої, {@code 0..}{@link
 *     Wheel#MAX_STRENGTH}
 * @param strengths окрема сила для типу колеса
 * @param investmentCurve скільки переваги дає кожне наступне вкладення в дію; кожне дає менше за попереднє
 *     (спадна віддача), але більше нуля
 */
public record WheelBalanceDef(
        int defaultStrength, SortedMap<WheelKind, Integer> strengths, List<Integer> investmentCurve) {

    public WheelBalanceDef {
        Checks.inRange("wheel.default_strength", defaultStrength, 0, Wheel.MAX_STRENGTH);
        TreeMap<WheelKind, Integer> copy = new TreeMap<>();
        strengths.forEach((kind, strength) -> copy.put(
                Objects.requireNonNull(kind, "wheel.strength"),
                Checks.inRange("wheel.strength." + kind.id(), strength, 0, Wheel.MAX_STRENGTH)));
        strengths = Collections.unmodifiableSortedMap(copy);

        if (investmentCurve.isEmpty()) {
            throw new ValidationException(
                    ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", "wheel.investment_curve"));
        }
        int previous = Advantage.MAX + 1;
        for (int i = 0; i < investmentCurve.size(); i++) {
            int step = Objects.requireNonNull(investmentCurve.get(i), "wheel.investment_curve");
            Checks.inRange("wheel.investment_curve[" + i + "]", step, 1, previous - 1);
            previous = step;
        }
        investmentCurve = List.copyOf(investmentCurve);
    }

    /** Сила переваги {@code k} для типу колеса: окрема або {@link #defaultStrength()}. */
    public int strength(WheelKind kind) {
        return strengths.getOrDefault(Objects.requireNonNull(kind, "kind"), defaultStrength);
    }

    /** Найбільша кількість вкладень у одну дію. */
    public int maxInvestments() {
        return investmentCurve.size();
    }

    /**
     * Сумарна перевага від {@code count} вкладень, обрізана до {@link Advantage#MAX}.
     *
     * @param count {@code 0..}{@link #maxInvestments()}
     */
    public int investmentAdvantage(int count) {
        Checks.inRange("investments", count, 0, maxInvestments());
        int sum = 0;
        for (int i = 0; i < count; i++) {
            sum += investmentCurve.get(i);
        }
        return Math.min(sum, Advantage.MAX);
    }
}
