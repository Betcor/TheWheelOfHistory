package kolo.engine.rng;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.TreeSet;
import kolo.engine.error.ValidationException;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Positive;
import org.junit.jupiter.api.Test;

class RngTest {

    @Property
    void sameSeedGivesSameSequence(@ForAll long seed) {
        Rng first = Rng.of(seed);
        Rng second = Rng.of(seed);

        for (int i = 0; i < 100; i++) {
            assertThat(first.nextLong()).isEqualTo(second.nextLong());
        }
    }

    @Property
    void nextIntStaysWithinBound(@ForAll long seed, @ForAll @Positive int bound) {
        Rng rng = Rng.of(seed);

        for (int i = 0; i < 100; i++) {
            assertThat(rng.nextInt(bound)).isBetween(0, bound - 1);
        }
    }

    @Property
    void forkDoesNotDependOnParentConsumption(@ForAll long seed, @ForAll @IntRange(max = 50) int consumed) {
        Rng untouched = Rng.of(seed);
        Rng consumedParent = Rng.of(seed);
        for (int i = 0; i < consumed; i++) {
            consumedParent.nextLong();
        }

        Rng expected = untouched.fork("economy:cty_1");
        Rng actual = consumedParent.fork("economy:cty_1");

        assertThat(actual.seed()).isEqualTo(expected.seed());
        assertThat(actual.nextLong()).isEqualTo(expected.nextLong());
    }

    @Property
    void forkDoesNotConsumeParent(@ForAll long seed) {
        Rng forked = Rng.of(seed);
        forked.fork("war:frn_1");

        assertThat(forked.nextLong()).isEqualTo(Rng.of(seed).nextLong());
    }

    @Test
    void differentLabelsGiveDifferentStreams() {
        Rng year = Rng.of(Rng.mix(2026, 0));
        TreeSet<Long> seeds = new TreeSet<>();
        TreeSet<Long> firstValues = new TreeSet<>();
        int labels = 0;
        for (String system : new String[] {"economy", "war", "intel", "internal"}) {
            for (int id = 1; id <= 250; id++) {
                Rng stream = year.fork(system + ":cty_" + id);
                seeds.add(stream.seed());
                firstValues.add(stream.nextLong());
                labels++;
            }
        }

        assertThat(seeds).hasSize(labels);
        assertThat(firstValues).hasSize(labels);
    }

    @Test
    void yearSeedsOfOneWorldAreDistinct() {
        TreeSet<Long> seeds = new TreeSet<>();
        for (int turn = 0; turn < 10_000; turn++) {
            seeds.add(Rng.mix(42, turn));
        }

        assertThat(seeds).hasSize(10_000);
    }

    @Test
    void mixIsNotSymmetric() {
        assertThat(Rng.mix(1, 2)).isNotEqualTo(Rng.mix(2, 1));
    }

    @Test
    void nextLongOfRootStreamMatchesReferenceXoshiro() {
        // Rng.of(seed) — це xoshiro256**, ініціалізований SplitMix64(seed), без додаткових перетворень.
        Rng rng = Rng.of(42);

        assertThat(rng.nextLong()).isEqualTo(0x15780B2E0C2EC716L);
        assertThat(rng.nextLong()).isEqualTo(0x6104D9866D113A7EL);
    }

    @Test
    void labelHashIsFnv1a64() {
        // Еталонні значення FNV-1a 64 для ASCII-рядків.
        assertThat(Rng.labelHash("")).isEqualTo(0xCBF29CE484222325L);
        assertThat(Rng.labelHash("a")).isEqualTo(0xAF63DC4C8601EC8CL);
        assertThat(Rng.labelHash("foobar")).isEqualTo(0x85944171F73967E8L);
    }

    @Test
    void nextIntIsUniformForWheelBound() {
        int bound = 10_000;
        int draws = 1_000_000;
        int[] counts = new int[bound];
        Rng rng = Rng.of(7);
        for (int i = 0; i < draws; i++) {
            counts[rng.nextInt(bound)]++;
        }

        // Хі-квадрат із 9 999 ступенями свободи: середнє 9 999, σ ≈ 141. Поріг — середнє + 5σ.
        assertThat(chiSquare(counts, draws)).isLessThan(9_999 + 5 * 141.4);
    }

    @Test
    void nextIntIsUniformForSmallBound() {
        int bound = 7;
        int draws = 1_000_000;
        int[] counts = new int[bound];
        Rng rng = Rng.of(11);
        for (int i = 0; i < draws; i++) {
            counts[rng.nextInt(bound)]++;
        }

        // Критичне значення хі-квадрат для 6 ступенів свободи при p = 0,001.
        assertThat(chiSquare(counts, draws)).isLessThan(22.46);
    }

    @Test
    void nextIntRejectsIncompleteBucket() {
        // Для bound ≈ ⅔·2³¹ простий залишок від ділення дав би нижній третині ~44% замість 33%.
        int bound = (int) ((1L << 31) / 3 * 2);
        int draws = 200_000;
        int lowerThird = 0;
        Rng rng = Rng.of(3);
        for (int i = 0; i < draws; i++) {
            if (rng.nextInt(bound) < bound / 3) {
                lowerThird++;
            }
        }

        assertThat(lowerThird / (double) draws).isBetween(0.325, 0.342);
    }

    @Property
    void rejectsNonPositiveBound(@ForAll @IntRange(min = Integer.MIN_VALUE, max = 0) int bound) {
        assertThatThrownBy(() -> Rng.of(1).nextInt(bound)).isInstanceOf(ValidationException.class);
    }

    private static double chiSquare(int[] counts, int draws) {
        double expected = draws / (double) counts.length;
        double sum = 0;
        for (int count : counts) {
            double diff = count - expected;
            sum += diff * diff / expected;
        }
        return sum;
    }
}
