package kolo.engine.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.TreeMap;
import kolo.engine.wheel.Advantage;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Віддача від вкладень на довільних спадних кривих. */
class InvestmentCurvePropertiesTest {

    @Property
    void eachInvestmentAddsLessThanThePreviousAndTotalStaysWithinAdvantage(@ForAll("curves") List<Integer> curve) {
        WheelBalanceDef wheel = new WheelBalanceDef(50, new TreeMap<>(), curve);

        int previousTotal = 0;
        int previousGain = Integer.MAX_VALUE;
        for (int count = 1; count <= wheel.maxInvestments(); count++) {
            int total = wheel.investmentAdvantage(count);
            int gain = total - previousTotal;
            assertThat(total).isBetween(0, Advantage.MAX);
            assertThat(gain).isBetween(0, previousGain);
            previousTotal = total;
            previousGain = gain;
        }
    }

    /** Строго спадні кроки 1..100: множина, відсортована за спаданням. */
    @Provide
    Arbitrary<List<Integer>> curves() {
        return Arbitraries.integers()
                .between(1, Advantage.MAX)
                .set()
                .ofMinSize(1)
                .ofMaxSize(8)
                .map(steps -> steps.stream().sorted((a, b) -> b - a).toList());
    }
}
