package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kolo.engine.content.ContentPack;
import kolo.engine.content.TestResources;
import kolo.engine.rng.Rng;
import org.junit.jupiter.api.Test;

/**
 * Розподіл колеса ресурсів відповідає сумам придатності, а провінція родовища — придатності провінцій. Seed-и
 * фіксовані, тож тест детермінований; пороги — χ² для p = 0,001.
 */
class ResourceWheelDistributionTest {

    private static final ContentPack PACK = TestResourceMaps.PACK;
    private static final int RUNS = 60_000;
    private static final List<Integer> CELLS = List.of(0, 1, 2, 3);

    @Test
    void firstResourceFollowsSuitabilitySums() {
        // Руда 60, ліс 50, зерно 60 → 3529 : 2941 : 3530 (+1 bp зерну: рівні залишки — за id).
        long[] counts = new long[3];
        for (long seed = 0; seed < RUNS; seed++) {
            StartDeposit first = ResourceWheel.generate(Rng.of(seed), PACK, TestResourceMaps.SMALL, CELLS)
                    .deposits()
                    .getFirst();
            counts[index(first)]++;
        }

        // 2 ступені свободи.
        assertThat(chiSquared(counts, new double[] {3529, 2941, 3530})).isLessThan(13.82);
    }

    @Test
    void oreDepositFollowsProvinceSuitability() {
        // Руда: комірка 0 — 40, комірка 1 — 20.
        long[] counts = new long[2];
        long total = 0;
        for (long seed = 0; seed < RUNS; seed++) {
            for (StartDeposit deposit : ResourceWheel.generate(Rng.of(seed), PACK, TestResourceMaps.SMALL, CELLS)
                    .deposits()) {
                if (deposit.resource().equals(TestResources.ORE)) {
                    counts[deposit.cell()]++;
                    total++;
                }
            }
        }

        // 1 ступінь свободи.
        double expected0 = total * 2 / 3.0;
        double expected1 = total / 3.0;
        double chiSquared = (counts[0] - expected0) * (counts[0] - expected0) / expected0
                + (counts[1] - expected1) * (counts[1] - expected1) / expected1;
        assertThat(chiSquared).isLessThan(10.83);
    }

    @Test
    void countIsUniform() {
        // 4 провінції — рядок 1–2.
        long[] counts = new long[2];
        for (long seed = 0; seed < RUNS; seed++) {
            counts[
                    ResourceWheel.generate(Rng.of(seed), PACK, TestResourceMaps.SMALL, CELLS)
                                    .deposits()
                                    .size()
                            - 1]++;
        }

        assertThat(chiSquared(counts, new double[] {5000, 5000})).isLessThan(10.83);
    }

    private static int index(StartDeposit deposit) {
        if (deposit.resource().equals(TestResources.ORE)) {
            return 0;
        }
        return deposit.resource().equals(TestResources.WOOD) ? 1 : 2;
    }

    private static double chiSquared(long[] counts, double[] weightsBp) {
        double chiSquared = 0;
        for (int i = 0; i < counts.length; i++) {
            double mean = RUNS * weightsBp[i] / 10_000;
            chiSquared += (counts[i] - mean) * (counts[i] - mean) / mean;
        }
        return chiSquared;
    }
}
