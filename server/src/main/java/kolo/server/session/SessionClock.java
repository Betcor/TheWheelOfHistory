package kolo.server.session;

import java.time.Duration;
import java.time.Instant;

/**
 * Час сесій: поточна мить і відкладені завдання — межі фази наказів (GD §6.1). На сервері — справжній годинник
 * ({@link SystemSessionClock}), у тестах — керований.
 */
interface SessionClock {

    Instant now();

    /**
     * Виконає {@code task} не раніше ніж через {@code delay} у потоці годинника; завдання саме передає роботу сесії.
     *
     * @return як скасувати завдання, якщо воно ще не почалося
     */
    Alarm schedule(Duration delay, Runnable task);

    /** Відкладене завдання. */
    interface Alarm {
        /** Скасовує завдання; виконане чи вже скасоване — нічого не робить. */
        void cancel();
    }
}
