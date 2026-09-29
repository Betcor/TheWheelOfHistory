package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kolo.engine.content.ContentPack;
import kolo.engine.content.StateReligionDef;
import kolo.engine.content.TagCondition;
import kolo.engine.content.TestReligions;
import kolo.engine.generation.name.TestNames;
import kolo.engine.generation.religion.StartReligion;
import kolo.engine.generation.religion.WorldReligionsWheel;
import kolo.engine.rng.Rng;
import org.junit.jupiter.api.Test;

/**
 * Розподіл колеса релігії держави відповідає вагам: релігії світу — рівні, світська держава — за мітками ладу.
 * Seed-и фіксовані, тож тест детермінований; пороги — χ² для p = 0,001.
 */
class StateReligionWheelDistributionTest {

    private static final int RUNS = 20_000;

    /** Критичні значення χ² для p = 0,001 за кількістю ступенів свободи (індекс — ступені). */
    private static final double[] CHI_SQUARED = {0, 10.83, 13.82, 16.27, 18.47, 20.52, 22.46};

    /** Світська держава: 100, демократія +200, не монархія. Мітки тестових ідеологій — їхні id. */
    private static final ContentPack PACK = TestNames.pack(
            TestReligions.content(new StateReligionDef(
                    100,
                    TestReligions.secular(
                            100,
                            Map.of("democracy", 200),
                            new TagCondition(List.of(), List.of(), List.of("monarchy"))))),
            TestReligions.BALANCE);

    private static final List<StartReligion> RELIGIONS =
            WorldReligionsWheel.generate(Rng.of(1970), PACK, 12).religions();

    @Test
    void withoutRegimeTagsReligionsAndSecularStateAreEqual() {
        long[] weights = new long[RELIGIONS.size() + 1];
        Arrays.fill(weights, 100);
        assertThat(chiSquared(counts(Set.of()), weights)).isLessThan(CHI_SQUARED[weights.length - 1]);
    }

    @Test
    void democracyTendsToSecularState() {
        long[] weights = new long[RELIGIONS.size() + 1];
        Arrays.fill(weights, 100);
        weights[RELIGIONS.size()] = 300;
        assertThat(chiSquared(counts(Set.of("democracy")), weights)).isLessThan(CHI_SQUARED[weights.length - 1]);
    }

    @Test
    void monarchyIsNeverSecularAndReligionsAreEqual() {
        long[] counts = counts(Set.of("monarchy"));
        assertThat(counts[RELIGIONS.size()]).isZero();

        long[] religions = Arrays.copyOf(counts, RELIGIONS.size());
        long[] weights = new long[RELIGIONS.size()];
        Arrays.fill(weights, 1);
        assertThat(chiSquared(religions, weights)).isLessThan(CHI_SQUARED[weights.length - 1]);
    }

    /** Частоти: релігії світу за номером, світська держава — останньою. */
    private static long[] counts(Set<String> tags) {
        long[] counts = new long[RELIGIONS.size() + 1];
        for (long seed = 0; seed < RUNS; seed++) {
            StartStateReligion result = StateReligionWheel.generate(Rng.of(seed), PACK, tags, RELIGIONS);
            counts[result.religion().orElse(RELIGIONS.size())]++;
        }
        return counts;
    }

    /** χ² спостережених частот проти очікуваних за відносними вагами. */
    private static double chiSquared(long[] counts, long[] weights) {
        long total = 0;
        long weightTotal = 0;
        for (int i = 0; i < counts.length; i++) {
            total += counts[i];
            weightTotal += weights[i];
        }
        double chiSquared = 0;
        for (int i = 0; i < counts.length; i++) {
            double mean = (double) total * weights[i] / weightTotal;
            chiSquared += (counts[i] - mean) * (counts[i] - mean) / mean;
        }
        return chiSquared;
    }
}
