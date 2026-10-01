package kolo.client.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import kolo.client.i18n.Texts;
import kolo.engine.state.TurnTimer;
import org.junit.jupiter.api.Test;

class TimerLabelsTest {

    private static final Texts TEXTS = Texts.ukrainian();

    @Test
    void timersNameTheModeAndDuration() {
        assertThat(TimerLabels.timer(TEXTS, TurnTimer.MANUAL)).isEqualTo("ручний");
        assertThat(TimerLabels.timer(TEXTS, new TurnTimer(TurnTimer.Mode.LIVE, 180)))
                .isEqualTo("живий, 3 хв на хід");
        assertThat(TimerLabels.timer(TEXTS, new TurnTimer(TurnTimer.Mode.ASYNC, 12 * 3600)))
                .isEqualTo("асинхронний, 12 год на хід");
    }

    @Test
    void durationsDropZeroParts() {
        assertThat(TimerLabels.duration(TEXTS, 45)).isEqualTo("45 с");
        assertThat(TimerLabels.duration(TEXTS, 90)).isEqualTo("1 хв 30 с");
        assertThat(TimerLabels.duration(TEXTS, 5400)).isEqualTo("1 год 30 хв");
        assertThat(TimerLabels.duration(TEXTS, 7200)).isEqualTo("2 год");
    }

    @Test
    void timeLeftRoundsUpToTheSecond() {
        assertThat(TimerLabels.timeLeft(TEXTS, Duration.ofSeconds(299))).isEqualTo("Лишилося 4:59");
        assertThat(TimerLabels.timeLeft(TEXTS, Duration.ofMillis(500))).isEqualTo("Лишилося 0:01");
        assertThat(TimerLabels.timeLeft(TEXTS, Duration.ofSeconds(-3))).isEqualTo("Лишилося 0:00");
        assertThat(TimerLabels.timeLeft(TEXTS, Duration.ofHours(12).minusSeconds(61)))
                .isEqualTo("Лишилося 11 год 58 хв");
    }
}
