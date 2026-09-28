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
