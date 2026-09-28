package kolo.engine.rng;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kolo.engine.error.ValidationException;
import org.junit.jupiter.api.Test;

class Xoshiro256StarStarTest {

    // Вектори з еталонної C-реалізації Vigna.
    @Test
    void matchesReferenceVectorsForExplicitState() {
        Xoshiro256StarStar rng = new Xoshiro256StarStar(1, 2, 3, 4);

        assertThat(next(rng, 6))
                .containsExactly(
                        0x0000000000002D00L,
                        0x0000000000000000L,
                        0x000000005A007080L,
                        0x10E0000000009D80L,
                        0x10E0B61CE1009D80L,
                        0x0870021CE143AD00L);
    }

    @Test
    void matchesReferenceVectorsWhenSeededBySplitMix64() {
        Xoshiro256StarStar rng = new Xoshiro256StarStar(42);

        assertThat(next(rng, 6))
                .containsExactly(
                        0x15780B2E0C2EC716L,
                        0x6104D9866D113A7EL,
                        0xAE17533239E499A1L,
                        0xECB8AD4703B360A1L,
                        0xFDE6DC7FE2EC5E64L,
                        0xC50DA53101795238L);
    }

    @Test
    void rejectsAllZeroState() {
        assertThatThrownBy(() -> new Xoshiro256StarStar(0, 0, 0, 0)).isInstanceOf(ValidationException.class);
    }

    private static long[] next(Xoshiro256StarStar rng, int count) {
        long[] values = new long[count];
        for (int i = 0; i < count; i++) {
            values[i] = rng.nextLong();
        }
        return values;
    }
}
