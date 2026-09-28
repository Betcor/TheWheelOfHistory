package kolo.engine.rng;

import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * xoshiro256** (Blackman, Vigna; public domain).
 *
 * <p>Результати збігаються з еталонною C-реалізацією з <a href="https://prng.di.unimi.it/">prng.di.unimi.it</a>.
 * Сам по собі не використовується рушієм — лише через {@link Rng}.
 */
final class Xoshiro256StarStar {

    private long s0;
    private long s1;
    private long s2;
    private long s3;

    /** Стан заповнюється чотирма значеннями SplitMix64, як радять автори алгоритму. */
    Xoshiro256StarStar(long seed) {
        SplitMix64 init = new SplitMix64(seed);
        this.s0 = init.nextLong();
        this.s1 = init.nextLong();
        this.s2 = init.nextLong();
        this.s3 = init.nextLong();
    }

    /** Прямо задає стан; лише для перевірки проти еталонних векторів. */
    Xoshiro256StarStar(long s0, long s1, long s2, long s3) {
        if ((s0 | s1 | s2 | s3) == 0) {
            throw new ValidationException(ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", "state", "value", 0));
        }
        this.s0 = s0;
        this.s1 = s1;
        this.s2 = s2;
        this.s3 = s3;
    }

    long nextLong() {
        long result = Long.rotateLeft(s1 * 5, 7) * 9;
        long t = s1 << 17;
        s2 ^= s0;
        s3 ^= s1;
        s1 ^= s2;
        s0 ^= s3;
        s2 ^= t;
        s3 = Long.rotateLeft(s3, 45);
        return result;
    }
}
