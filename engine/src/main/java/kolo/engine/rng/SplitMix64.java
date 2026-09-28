package kolo.engine.rng;

/**
 * SplitMix64 (Steele, Lea, Flood; реалізація Vigna, public domain).
 *
 * <p>Використовується для ініціалізації стану {@link Xoshiro256StarStar} і для змішування seed-ів. Результати
 * збігаються з еталонною C-реалізацією з <a href="https://prng.di.unimi.it/">prng.di.unimi.it</a>.
 */
public final class SplitMix64 {

    static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private long state;

    public SplitMix64(long seed) {
        this.state = seed;
    }

    public long nextLong() {
        state += GOLDEN_GAMMA;
        return mix(state);
    }

    /** Фіналізатор SplitMix64: біекція на {@code long} з хорошим лавинним ефектом. */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
