package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ValueNoiseTest {

    @Test
    void valuesStayInRangeAndUseMostOfIt() {
        ValueNoise noise = new ValueNoise(42);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int y = -500; y < 2_000; y += 7) {
            for (int x = -500; x < 4_000; x += 11) {
                int value = noise.sample(x, y, 400);
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }

        assertThat(min).isGreaterThanOrEqualTo(0);
        assertThat(max).isLessThan(ValueNoise.MAX);
        assertThat(max - min).isGreaterThan(ValueNoise.MAX / 3);
    }

    @Test
    void sameSeedGivesSameNoiseAndOtherSeedOtherNoise() {
        ValueNoise a = new ValueNoise(7);
        ValueNoise b = new ValueNoise(7);
        ValueNoise other = new ValueNoise(8);
        int differences = 0;
        for (int i = 0; i < 100; i++) {
            int x = i * 37;
            int y = i * 53;
            assertThat(a.sample(x, y, 100)).isEqualTo(b.sample(x, y, 100));
            if (a.sample(x, y, 100) != other.sample(x, y, 100)) {
                differences++;
            }
        }

        assertThat(differences).isGreaterThan(90);
    }

    @Test
    void neighbouringPointsHaveCloseValues() {
        ValueNoise noise = new ValueNoise(3);
        int period = 800;
        for (int y = 0; y < 3_000; y += 13) {
            for (int x = 0; x < 3_000; x += 17) {
                // Найдрібніша октава має крок 200: зсув на одиницю змінює кожну октаву менше ніж на 2 %.
                assertThat(Math.abs(noise.sample(x + 1, y, period) - noise.sample(x, y, period)))
                        .isLessThan(ValueNoise.MAX / 50);
                assertThat(Math.abs(noise.sample(x, y + 1, period) - noise.sample(x, y, period)))
                        .isLessThan(ValueNoise.MAX / 50);
            }
        }
    }

    @Test
    void periodOfOneGivesLatticeValues() {
        ValueNoise noise = new ValueNoise(5);

        // При кроці 1 усі октави потрапляють точно у вузли: шум = зважене середнє вузлів.
        int expected = (4 * noise.lattice(0, 3, 4) + 2 * noise.lattice(1, 3, 4) + noise.lattice(2, 3, 4)) / 7;
        assertThat(noise.sample(3, 4, 1)).isEqualTo(expected);
    }

    @Test
    void signedNoiseIsCenteredStretchedAndClamped() {
        ValueNoise noise = new ValueNoise(11);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int y = 0; y < 2_000; y += 7) {
            for (int x = 0; x < 4_000; x += 11) {
                int value = noise.signed(x, y, 400, 3);
                assertThat(value)
                        .isEqualTo(Math.clamp(
                                (2L * noise.sample(x, y, 400) - ValueNoise.MAX) * 3, -ValueNoise.MAX, ValueNoise.MAX));
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }

        // Розтяг утричі доводить шум до обох меж.
        assertThat(min).isEqualTo(-ValueNoise.MAX);
        assertThat(max).isEqualTo(ValueNoise.MAX);
    }

    @Test
    void smoothstepKeepsEndsAndMiddle() {
        assertThat(ValueNoise.smooth(0)).isZero();
        assertThat(ValueNoise.smooth(ValueNoise.MAX)).isEqualTo(ValueNoise.MAX);
        assertThat(ValueNoise.smooth(ValueNoise.MAX / 2)).isEqualTo(ValueNoise.MAX / 2);
        assertThat(ValueNoise.smooth(ValueNoise.MAX / 4)).isLessThan(ValueNoise.MAX / 4);
    }
}
