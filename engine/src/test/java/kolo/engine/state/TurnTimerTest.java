package kolo.engine.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import org.junit.jupiter.api.Test;

class TurnTimerTest {

    @Test
    void manualTimerHasNoDuration() {
        assertThat(TurnTimer.MANUAL.timed()).isFalse();
        assertThat(TurnTimer.MANUAL.seconds()).isZero();
        assertThatThrownBy(() -> new TurnTimer(TurnTimer.Mode.MANUAL, 60))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.CONFLICTING_FIELDS));
    }

    @Test
    void timedModesNeedADurationWithinAWeek() {
        assertThat(new TurnTimer(TurnTimer.Mode.LIVE, 120).timed()).isTrue();
        assertThat(new TurnTimer(TurnTimer.Mode.ASYNC, TurnTimer.MAX_SECONDS).timed())
                .isTrue();
        for (int seconds : new int[] {0, -1, TurnTimer.MAX_SECONDS + 1}) {
            assertThatThrownBy(() -> new TurnTimer(TurnTimer.Mode.ASYNC, seconds))
                    .isInstanceOfSatisfying(
                            ValidationException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
        }
    }

    @Test
    void modeKeysAreLowerCase() {
        assertThat(TurnTimer.Mode.MANUAL.key()).isEqualTo("manual");
        assertThat(TurnTimer.Mode.LIVE.key()).isEqualTo("live");
        assertThat(TurnTimer.Mode.ASYNC.key()).isEqualTo("async");
    }
}
