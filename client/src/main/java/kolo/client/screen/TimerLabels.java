package kolo.client.screen;

import java.time.Duration;
import java.util.Locale;
import kolo.client.i18n.Texts;
import kolo.engine.state.TurnTimer;

/** Підписи таймера ходу (GD §6.1): режим із тривалістю в лобі й час, що лишився, на карті. */
public final class TimerLabels {

    private static final long MINUTE = 60;
    private static final long HOUR = 60 * MINUTE;

    private TimerLabels() {}

    /** Таймер ходу: «Ручний», «Живий, 3 хв на хід», «Асинхронний, 12 год на хід». */
    public static String timer(Texts texts, TurnTimer timer) {
        return switch (timer.mode()) {
            case MANUAL -> texts.text("timer.manual");
            case LIVE -> texts.text("timer.live", duration(texts, timer.seconds()));
            case ASYNC -> texts.text("timer.async", duration(texts, timer.seconds()));
        };
    }

    /** Тривалість без зайвих нулів: «3 хв», «12 год», «1 год 30 хв», «1 хв 30 с», «45 с». */
    static String duration(Texts texts, long seconds) {
        long hours = seconds / HOUR;
        long minutes = seconds % HOUR / MINUTE;
        long rest = seconds % MINUTE;
        if (hours > 0) {
            return minutes > 0
                    ? texts.text("duration.hours_minutes", hours, minutes)
                    : texts.text("duration.hours", hours);
        }
        if (minutes > 0) {
            return rest > 0
                    ? texts.text("duration.minutes_seconds", minutes, rest)
                    : texts.text("duration.minutes", minutes);
        }
        return texts.text("duration.seconds", rest);
    }

    /**
     * Скільки лишилося до кінця фази наказів: до години — «4:59», більше — «11 год 59 хв». Неповна секунда
     * округлюється вгору: «0:00» — лише коли час справді вийшов.
     */
    public static String timeLeft(Texts texts, Duration left) {
        long millis = Math.max(0, left.toMillis());
        long seconds = Math.ceilDiv(millis, 1000);
        if (seconds < HOUR) {
            return texts.text(
                    "map.time_left", String.format(Locale.ROOT, "%d:%02d", seconds / MINUTE, seconds % MINUTE));
        }
        return texts.text(
                "map.time_left", texts.text("duration.hours_minutes", seconds / HOUR, seconds % HOUR / MINUTE));
    }
}
