package kolo.engine.wheel;

import static kolo.engine.wheel.Wheels.CONSTRUCTION;
import static kolo.engine.wheel.Wheels.CONSTRUCTION_KIND;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kolo.engine.rng.Rng;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Розподіл 1 000 000 обертань відповідає вагам — для граничних і нейтральної переваги.
 *
 * <p>Критерій χ² на секторах з ненульовою вагою; пороги відповідають p = 0,001. Seed фіксований, тож тест
 * детермінований: він доводить, що колесо + RNG не мають систематичного зсуву, а не перевіряє удачу.
 */
class WheelDistributionTest {

    private static final int SPINS = 1_000_000;
    /** Критичні значення χ² для p = 0,001 за кількістю ступенів свободи (індекс). */
    private static final double[] CHI_SQUARED_CRITICAL = {0, 10.83, 13.82, 16.27, 18.47};

    @ParameterizedTest
    @ValueSource(ints = {-100, 0, 100})
    void spinsFollowWeights(int advantage) {
        List<Sector<String>> expected = Wheel.applyAdvantage(CONSTRUCTION, advantage, 100);
        Advantage adv = new Advantage(advantage, List.of());
        Rng rng = Rng.of(Rng.mix(1970, advantage));
        long[] counts = new long[expected.size()];

        for (int i = 0; i < SPINS; i++) {
            String id = Wheel.spin(rng, CONSTRUCTION_KIND, CONSTRUCTION, adv, 100, 0, null)
                    .outcome()
                    .id();
            counts[indexOf(expected, id)]++;
        }

        double chiSquared = 0;
        int nonZero = 0;
        for (int i = 0; i < counts.length; i++) {
            int weight = expected.get(i).weightBp();
            if (weight == 0) {
                assertThat(counts[i]).as("сектор з нульовою вагою").isZero();
                continue;
            }
            nonZero++;
            double mean = (double) SPINS * weight / Wheel.TOTAL_BP;
            chiSquared += (counts[i] - mean) * (counts[i] - mean) / mean;
        }
        assertThat(chiSquared).isLessThan(CHI_SQUARED_CRITICAL[nonZero - 1]);
    }

    private static int indexOf(List<Sector<String>> sectors, String id) {
        for (int i = 0; i < sectors.size(); i++) {
            if (sectors.get(i).id().equals(id)) {
                return i;
            }
        }
        throw new AssertionError(id);
    }
}
