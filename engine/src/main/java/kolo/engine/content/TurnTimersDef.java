package kolo.engine.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.TurnTimer;

/**
 * Таймери ходу, з яких обирає хост (GD §6.1, §3.4): тривалості фази наказів для живого й асинхронного режимів. Ручний
 * режим є завжди.
 *
 * @param liveSeconds «Живий», секунди; непорожній, за зростанням без повторів
 * @param asyncSeconds «Асинхронний», секунди; непорожній, за зростанням без повторів
 */
public record TurnTimersDef(List<Integer> liveSeconds, List<Integer> asyncSeconds) {

    public TurnTimersDef {
        liveSeconds = ascending("turn_timers.live", liveSeconds);
        asyncSeconds = ascending("turn_timers.async", asyncSeconds);
    }

    /** Усі варіанти в порядку показу: ручний, живі, асинхронні — від коротшого. */
    public List<TurnTimer> choices() {
        List<TurnTimer> choices = new ArrayList<>(1 + liveSeconds.size() + asyncSeconds.size());
        choices.add(TurnTimer.MANUAL);
        liveSeconds.forEach(seconds -> choices.add(new TurnTimer(TurnTimer.Mode.LIVE, seconds)));
        asyncSeconds.forEach(seconds -> choices.add(new TurnTimer(TurnTimer.Mode.ASYNC, seconds)));
        return List.copyOf(choices);
    }

    /** Чи може хост обрати цей таймер. */
    public boolean allows(TurnTimer timer) {
        Objects.requireNonNull(timer, "timer");
        return switch (timer.mode()) {
            case MANUAL -> true;
            case LIVE -> liveSeconds.contains(timer.seconds());
            case ASYNC -> asyncSeconds.contains(timer.seconds());
        };
    }

    private static List<Integer> ascending(String field, List<Integer> values) {
        if (values.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", field));
        }
        int previous = 0;
        for (int i = 0; i < values.size(); i++) {
            int seconds = Objects.requireNonNull(values.get(i), field);
            Checks.inRange(field + "[" + i + "]", seconds, previous + 1, TurnTimer.MAX_SECONDS);
            previous = seconds;
        }
        return List.copyOf(values);
    }
}
