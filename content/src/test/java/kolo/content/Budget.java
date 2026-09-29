package kolo.content;

import java.util.function.Supplier;

/**
 * Замір для тестів бюджету продуктивності. Gradle ганяє тести модулів паралельно, а CI-машина повільна й спільна:
 * одиночний замір ловить чужі навантаження й паузи GC. Тому — прогрів і найкращий з кількох запусків: це час, якого
 * код досягає, а не шум оточення.
 */
final class Budget {

    /** Замірів після прогріву. */
    static final int RUNS = 3;

    private Budget() {}

    /** Результат останнього запуску й найкращий час серед {@value #RUNS} замірів, мс. */
    record Timed<T>(T result, long millis) {}

    static <T> Timed<T> best(Supplier<T> run) {
        T result = run.get();
        long best = Long.MAX_VALUE;
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            result = run.get();
            best = Math.min(best, (System.nanoTime() - start) / 1_000_000);
        }
        return new Timed<>(result, best);
    }
}
