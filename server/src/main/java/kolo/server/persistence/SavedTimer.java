package kolo.server.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import kolo.engine.error.Checks;
import kolo.engine.state.TurnTimer;

/**
 * Таймер ходу світу у файлі (GD §6.1).
 *
 * @param timer режим і тривалість фази наказів, які обрав хост
 * @param deadline межа фази наказів року, якщо вона вже йшла; для іншого року — не діє
 */
public record SavedTimer(TurnTimer timer, Optional<Deadline> deadline) {

    public SavedTimer {
        Objects.requireNonNull(timer, "timer");
        Objects.requireNonNull(deadline, "deadline");
    }

    /** Межа фази наказів року {@code turn}, якщо вона збережена саме для нього. */
    public Optional<Instant> deadlineOf(int turn) {
        return deadline.filter(d -> d.turn() == turn).map(Deadline::at);
    }

    /**
     * Межа фази наказів.
     *
     * @param turn рік (хід), для якого вона діє
     * @param at мить, коли фаза наказів закінчується
     */
    public record Deadline(int turn, Instant at) {

        public Deadline {
            Checks.inRange("turn", turn, 0, Integer.MAX_VALUE);
            Objects.requireNonNull(at, "at");
        }
    }
}
