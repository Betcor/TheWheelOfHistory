package kolo.engine.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

class FixedTest {

    @Test
    void roundsTowardNegativeInfinity() {
        assertThat(Fixed.mulDiv(7L, 1L, 2L)).isEqualTo(3L);
        assertThat(Fixed.mulDiv(-7L, 1L, 2L)).isEqualTo(-4L);
        assertThat(Fixed.mulDiv(7L, 1L, -2L)).isEqualTo(-4L);
        assertThat(Fixed.mulDiv(-7, 1, 2)).isEqualTo(-4);
    }

    @Test
    void distributeUsesLargestRemainders() {
        assertThat(Fixed.distribute(10, new long[] {1, 1, 1})).containsExactly(4, 3, 3);
        assertThat(Fixed.distribute(5, new long[] {0, 1, 1})).containsExactly(0, 3, 2);
        assertThat(Fixed.distribute(7, new long[] {2, 5})).containsExactly(2, 5);
        assertThat(Fixed.distribute(0, new long[] {3})).containsExactly(0);
    }

    @Property
    void distributeGivesExactlyTotal(@ForAll long seed) {
        long total = Math.floorMod(seed, 100_000);
        long[] weights = {Math.floorMod(seed, 7), Math.floorMod(seed >> 8, 13) + 1, Math.floorMod(seed >> 16, 5)};

        long[] shares = Fixed.distribute(total, weights);

        assertThat(java.util.Arrays.stream(shares).sum()).isEqualTo(total);
        long sum = java.util.Arrays.stream(weights).sum();
        for (int i = 0; i < weights.length; i++) {
            assertThat(shares[i]).isBetween(total * weights[i] / sum, total * weights[i] / sum + 1);
        }
    }

    @Test
    void intVersionUsesLongIntermediate() {
        assertThat(Fixed.mulDiv(Integer.MAX_VALUE, 2, 2)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void overflowIsNotMasked() {
        assertThatThrownBy(() -> Fixed.mulDiv(Long.MAX_VALUE, 2L, 2L)).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> Fixed.mulDiv(Integer.MAX_VALUE, 2, 1)).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void divisionByZeroThrows() {
        assertThatThrownBy(() -> Fixed.mulDiv(1L, 1L, 0L)).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> Fixed.mulDiv(1, 1, 0)).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void appliesBasisPoints() {
        assertThat(Fixed.applyBp(12_345L, 5_000)).isEqualTo(6_172L);
        assertThat(Fixed.applyBp(100, Fixed.BP_SCALE)).isEqualTo(100);
        assertThat(Fixed.applyBp(-1, 1)).isEqualTo(-1);
    }

    @Property
    void longMulDivMatchesExactFloorDivision(@ForAll int a, @ForAll int b, @ForAll long c) {
        // Множники — int, щоб добуток гарантовано вміщався в long і випадки не відкидалися.
        Assume.that(c != 0);
        long expected = new BigDecimal(BigInteger.valueOf((long) a * b))
                .divide(BigDecimal.valueOf(c), 0, RoundingMode.FLOOR)
                .longValueExact();

        assertThat(Fixed.mulDiv((long) a, (long) b, c)).isEqualTo(expected);
    }

    @Property
    void intMulDivMatchesExactFloorDivision(@ForAll int a, @ForAll int b, @ForAll int c) {
        Assume.that(c != 0);
        BigInteger expected = new BigDecimal(BigInteger.valueOf((long) a * b))
                .divide(BigDecimal.valueOf(c), 0, RoundingMode.FLOOR)
                .toBigInteger();
        Assume.that(expected.bitLength() < Integer.SIZE);

        assertThat(Fixed.mulDiv(a, b, c)).isEqualTo(expected.intValueExact());
    }
}
