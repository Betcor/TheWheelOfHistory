package kolo.server.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Справжній годинник сесій сервера: системний час UTC і один потік-демон {@code kolo-timers} для меж фаз усіх сесій.
 * Завдання лише ставлять роботу в чергу сесії, тож одного потоку досить.
 */
final class SystemSessionClock implements SessionClock, AutoCloseable {

    private final Clock clock = Clock.systemUTC();
    private final ScheduledThreadPoolExecutor timers;

    SystemSessionClock() {
        timers = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "kolo-timers");
            thread.setDaemon(true);
            return thread;
        });
        // Скасована межа (рік розв'язано раніше) не мусить годинами лежати в черзі.
        timers.setRemoveOnCancelPolicy(true);
        timers.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    @Override
    public Instant now() {
        return clock.instant();
    }

    @Override
    public Alarm schedule(Duration delay, Runnable task) {
        ScheduledFuture<?> future = timers.schedule(task, Math.max(0, delay.toMillis()), TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }

    /** Зупиняє потік; відкладені завдання вже не виконаються. */
    @Override
    public void close() {
        timers.shutdownNow();
    }
}
