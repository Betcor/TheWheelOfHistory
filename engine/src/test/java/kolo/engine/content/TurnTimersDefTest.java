package kolo.engine.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.state.TurnTimer;
import org.junit.jupiter.api.Test;

class TurnTimersDefTest {

    private static final TurnTimersDef TIMERS = new TurnTimersDef(List.of(120, 300), List.of(43_200));

    @Test
    void choicesStartWithManualThenShortestFirst() {
        assertThat(TIMERS.choices())
                .containsExactly(
                        TurnTimer.MANUAL,
                        new TurnTimer(TurnTimer.Mode.LIVE, 120),
                        new TurnTimer(TurnTimer.Mode.LIVE, 300),
                        new TurnTimer(TurnTimer.Mode.ASYNC, 43_200));
    }

    @Test
    void allowsOnlyListedDurationsOfTheirMode() {
        assertThat(TIMERS.allows(TurnTimer.MANUAL)).isTrue();
        assertThat(TIMERS.allows(new TurnTimer(TurnTimer.Mode.LIVE, 300))).isTrue();
        assertThat(TIMERS.allows(new TurnTimer(TurnTimer.Mode.LIVE, 180))).isFalse();
        assertThat(TIMERS.allows(new TurnTimer(TurnTimer.Mode.ASYNC, 120))).isFalse();
    }

    @Test
    void durationsMustBeAscendingAndPresent() {
        assertCode(() -> new TurnTimersDef(List.of(), List.of(60)), ErrorCode.EMPTY_COLLECTION);
        assertCode(() -> new TurnTimersDef(List.of(60, 60), List.of(60)), ErrorCode.VALUE_OUT_OF_RANGE);
        assertCode(() -> new TurnTimersDef(List.of(60), List.of(0)), ErrorCode.VALUE_OUT_OF_RANGE);
        assertCode(
                () -> new TurnTimersDef(List.of(60), List.of(TurnTimer.MAX_SECONDS + 1)), ErrorCode.VALUE_OUT_OF_RANGE);
    }

    private static void assertCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
