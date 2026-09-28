package kolo.engine.rng;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SplitMix64Test {

    // Вектори з еталонної C-реалізації Vigna (для seed 1234567 збігаються з Rosetta Code).
    @Test
    void matchesReferenceVectorsForSeed1234567() {
        SplitMix64 rng = new SplitMix64(1234567);

        assertThat(new long[] {rng.nextLong(), rng.nextLong(), rng.nextLong(), rng.nextLong(), rng.nextLong()})
                .containsExactly(
                        0x599ED017FB08FC85L,
                        0x2C73F08458540FA5L,
                        0x883EBCE5A3F27C77L,
                        0x3FBEF740E9177B3FL,
                        0xE3B8346708CB5ECDL);
    }

    @Test
    void matchesReferenceVectorsForSeedZero() {
        SplitMix64 rng = new SplitMix64(0);

        assertThat(new long[] {rng.nextLong(), rng.nextLong(), rng.nextLong()})
                .containsExactly(0xE220A8397B1DCDAFL, 0x6E789E6AA1B965F4L, 0x06C45D188009454FL);
    }
}
