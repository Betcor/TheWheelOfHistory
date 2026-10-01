package kolo.server.session;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Керований годинник сесій для тестів: час іде лише за {@link #advance}, тоді ж спрацьовують відкладені завдання. */
final class ManualClock implements SessionClock {

    static final Instant START = Instant.parse("2026-10-01T12:00:00Z");

    private final List<Pending> pending = new ArrayList<>();
    private Instant now = START;

    @Override
    public synchronized Instant now() {
        return now;
    }

    @Override
    public synchronized Alarm schedule(Duration delay, Runnable task) {
        Pending alarm = new Pending(now.plus(delay.isNegative() ? Duration.ZERO : delay), task);
        pending.add(alarm);
        return () -> {
            synchronized (this) {
                pending.remove(alarm);
            }
        };
    }

    /** Скільки відкладених завдань чекає. */
    synchronized int pending() {
        return pending.size();
    }

    /** Переводить час уперед і виконає завдання, чия мить настала, у порядку їхніх митей. */
    void advance(Duration duration) {
        List<Pending> due = new ArrayList<>();
        synchronized (this) {
            now = now.plus(duration);
            for (Pending alarm : List.copyOf(pending)) {
                if (!alarm.at.isAfter(now)) {
                    due.add(alarm);
                    pending.remove(alarm);
                }
            }
        }
        due.sort((a, b) -> a.at.compareTo(b.at));
        due.forEach(alarm -> alarm.task.run());
    }

    private record Pending(Instant at, Runnable task) {}
}
