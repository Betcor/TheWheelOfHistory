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
}
