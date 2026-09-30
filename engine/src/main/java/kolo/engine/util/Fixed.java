package kolo.engine.util;

/**
 * Цілочисельна арифметика рушія з явним округленням.
 *
 * <p>Рушій не використовує {@code float}/{@code double}: частки й імовірності — у базисних пунктах (bp), де
 * {@link #BP_SCALE} = 100%. Усі ділення тут — з округленням униз (до −∞), як {@link Math#floorDiv(long, long)},
 * а не до нуля, як оператор {@code /}. Переповнення не маскується, а кидає {@link ArithmeticException}.
 */
public final class Fixed {

    /** 100% у базисних пунктах. */
    public static final int BP_SCALE = 10_000;

    private Fixed() {}

    /** {@code floor(a * b / c)}; кидає {@link ArithmeticException} при переповненні або {@code c == 0}. */
    public static long mulDiv(long a, long b, long c) {
        return Math.floorDiv(Math.multiplyExact(a, b), c);
    }

    /** {@code floor(a * b / c)} для {@code int}; проміжний добуток рахується в {@code long}. */
    public static int mulDiv(int a, int b, int c) {
        return Math.toIntExact(Math.floorDiv((long) a * b, c));
    }

    /** Частка {@code bp} базисних пунктів від {@code value}, з округленням униз. */
    public static long applyBp(long value, int bp) {
        return mulDiv(value, bp, BP_SCALE);
    }

    /** Частка {@code bp} базисних пунктів від {@code value}, з округленням униз. */
    public static int applyBp(int value, int bp) {
        return mulDiv(value, bp, BP_SCALE);
    }

    /**
     * {@code total}, поділене пропорційно вагам: частки вниз, залишок — по одному найбільшим залишкам, при рівності —
     * менший номер. Нульова вага нічого не отримує.
     *
     * @param total невід'ємне
     * @param weights невід'ємні, сума більша за нуль
     */
    public static long[] distribute(long total, long[] weights) {
        long sum = 0;
        for (long weight : weights) {
            sum = Math.addExact(sum, weight);
        }
        long[] shares = new long[weights.length];
        long[] remainders = new long[weights.length];
        long given = 0;
        for (int i = 0; i < weights.length; i++) {
            long part = Math.multiplyExact(total, weights[i]);
            shares[i] = part / sum;
            remainders[i] = weights[i] == 0 ? -1 : part % sum;
            given += shares[i];
        }
        for (long left = total - given; left > 0; left--) {
            int best = -1;
            for (int i = 0; i < weights.length; i++) {
                if (remainders[i] >= 0 && (best < 0 || remainders[i] > remainders[best])) {
                    best = i;
                }
            }
            shares[best]++;
            remainders[best] = -1;
        }
        return shares;
    }
}
