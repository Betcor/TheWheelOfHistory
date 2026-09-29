package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestDevelopment.PACK;
import static kolo.engine.generation.country.TestDevelopment.REGIME;
import static kolo.engine.generation.country.TestDevelopment.WEIGHTS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kolo.engine.content.DevelopmentLevelDef;
import kolo.engine.modifier.Modifier;
import kolo.engine.rng.Rng;
import kolo.engine.state.TechBranch;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import org.junit.jupiter.api.Test;

/**
 * Розподіл рівнів відповідає вагам контенту, зсунутим перевагою ладу. Seed-и фіксовані, тож тест детермінований;
 * поріг — χ² для p = 0,001 і 5 ступенів свободи.
 */
class DevelopmentWheelDistributionTest {

    private static final int RUNS = 60_000;
    private static final double CHI_SQUARED_5_DOF = 20.52;

    @Test
    void withoutAdvantageEveryBranchFollowsContentWeights() {
        long[][] counts = count(List.of());

        for (TechBranch branch : TechBranch.values()) {
            assertThat(chiSquared(counts[branch.ordinal()], WEIGHTS))
                    .as(branch.key())
                    .isLessThan(CHI_SQUARED_5_DOF);
        }
    }

    @Test
    void regimeAdvantageShiftsBranchesToAdjustedWeights() {
        long[][] counts = count(REGIME.modifiers());
        int strength = PACK.balance().wheel().strength(DevelopmentWheel.kind(TechBranch.ENERGY_SCIENCE));

        assertThat(chiSquared(counts[TechBranch.ENERGY_SCIENCE.ordinal()], adjusted(Advantage.MAX, strength)))
                .isLessThan(CHI_SQUARED_5_DOF);
        assertThat(chiSquared(counts[TechBranch.MILITARY.ordinal()], adjusted(Advantage.MIN, strength)))
                .isLessThan(CHI_SQUARED_5_DOF);
        assertThat(chiSquared(counts[TechBranch.ECONOMY.ordinal()], adjusted(30, strength)))
                .isLessThan(CHI_SQUARED_5_DOF);
        // Лідерів в енергетиці більше, ніж без переваги, а у війську менше.
        assertThat(counts[TechBranch.ENERGY_SCIENCE.ordinal()][5])
                .isGreaterThan(counts[TechBranch.SOCIETY.ordinal()][5]);
        assertThat(counts[TechBranch.MILITARY.ordinal()][5]).isLessThan(counts[TechBranch.SOCIETY.ordinal()][5]);
    }

    private static long[][] count(List<Modifier> modifiers) {
        long[][] counts = new long[TechBranch.values().length][WEIGHTS.length];
        for (long seed = 0; seed < RUNS; seed++) {
            StartDevelopment development = DevelopmentWheel.generate(Rng.of(seed), PACK, modifiers);
            for (TechBranch branch : TechBranch.values()) {
                counts[branch.ordinal()][development.level(branch) + 3]++;
            }
        }
        return counts;
    }

    private static int[] adjusted(int advantage, int strength) {
        List<Sector<DevelopmentLevelDef>> sectors =
                Wheel.applyAdvantage(DevelopmentWheel.sectors(PACK), advantage, strength);
        return sectors.stream().mapToInt(Sector::weightBp).toArray();
    }

    private static double chiSquared(long[] counts, int[] weightsBp) {
        double chiSquared = 0;
        for (int i = 0; i < counts.length; i++) {
            double mean = (double) RUNS * weightsBp[i] / Wheel.TOTAL_BP;
            chiSquared += (counts[i] - mean) * (counts[i] - mean) / mean;
        }
        return chiSquared;
    }
}
