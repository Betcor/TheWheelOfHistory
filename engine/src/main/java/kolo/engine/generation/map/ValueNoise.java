package kolo.engine.generation.map;

import kolo.engine.error.Checks;
import kolo.engine.rng.Rng;

/**
 * Фрактальний шум значень у цілих числах: випадкові значення у вузлах квадратної ґратки, між ними — гладка
 * інтерполяція (smoothstep), три октави з удвічі меншим кроком і вагами 4 : 2 : 1.
 *
 * <p>Власний, а не FastNoiseLite: материкам і рельєфу досить плавного поля без напрямку (напрямок горам дають хребти),
 * а цілочисельний шум однаковий усюди без
 * жодних застережень щодо {@code float}. Значення у вузлі — хеш координат і seed-а, тож шум не має стану й не
 * залежить від порядку запитів.
 */
final class ValueNoise {

    /** Значення шуму — у {@code [0, MAX)}. */
    static final int MAX = 1 << 16;

    private static final int[] OCTAVE_WEIGHTS = {4, 2, 1};
    private static final int WEIGHT_SUM = 7;

    private final long seed;

    ValueNoise(long seed) {
        this.seed = seed;
    }

    /**
     * @param period крок найгрубішої октави в одиницях карти, {@code ≥ 1}; наступні — удвічі дрібніші
     * @return значення в {@code [0, MAX)}
     */
    int sample(int x, int y, int period) {
        Checks.inRange("period", period, 1, Integer.MAX_VALUE);
        long sum = 0;
        for (int octave = 0; octave < OCTAVE_WEIGHTS.length; octave++) {
            int step = Math.max(1, period >> octave);
            sum += (long) OCTAVE_WEIGHTS[octave] * octave(octave, x, y, step);
        }
        return (int) (sum / WEIGHT_SUM);
    }

    /**
     * Шум зі знаком: відхилення від середини, розтягнуте в {@code contrast} разів і обрізане. Середнє трьох октав
     * скупчене біля середини (типове відхилення — чверть півдіапазону), тож без розтягу крайніх значень майже немає.
     *
     * @return значення в {@code [−MAX, MAX]}
     */
    int signed(int x, int y, int period, int contrast) {
        return Math.clamp((2L * sample(x, y, period) - MAX) * contrast, -MAX, MAX);
    }

    /** Одна октава: білінійна інтерполяція вузлів ґратки з кроком {@code step} зі згладженими вагами. */
    private int octave(int octave, int x, int y, int step) {
        long cellX = Math.floorDiv(x, step);
        long cellY = Math.floorDiv(y, step);
        long tx = smooth((long) Math.floorMod(x, step) * MAX / step);
        long ty = smooth((long) Math.floorMod(y, step) * MAX / step);
        long top = lerp(lattice(octave, cellX, cellY), lattice(octave, cellX + 1, cellY), tx);
        long bottom = lerp(lattice(octave, cellX, cellY + 1), lattice(octave, cellX + 1, cellY + 1), tx);
        return (int) lerp(top, bottom, ty);
    }

    /** Значення вузла — верхні 16 біт хешу координат: у {@code [0, MAX)}. */
    int lattice(int octave, long cellX, long cellY) {
        return (int) (Rng.mix(Rng.mix(Rng.mix(seed, octave), cellX), cellY) >>> 48);
    }

    /** {@code 3t² − 2t³} для {@code t} у {@code [0, MAX]}, результат у тих самих одиницях. */
    static long smooth(long t) {
        return t * t * (3L * MAX - 2 * t) / ((long) MAX * MAX);
    }

    /** Між {@code a} і {@code b} за вагою {@code t} у {@code [0, MAX]}. */
    private static long lerp(long a, long b, long t) {
        return a + Math.floorDiv((b - a) * t, MAX);
    }
}
