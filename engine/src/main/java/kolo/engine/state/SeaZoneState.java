package kolo.engine.state;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;

/**
 * Морська зона в стані світу: частина моря, якою ходять флоти. Не змінюється після генерації.
 *
 * @param id id зони
 * @param cells номери морських комірок зони за зростанням; непорожньо
 * @param neighbors сусідні зони за зростанням номера
 */
public record SeaZoneState(SeaZoneId id, List<Integer> cells, List<SeaZoneId> neighbors) {

    public SeaZoneState {
        Objects.requireNonNull(id, "id");
        cells = List.copyOf(cells);
        neighbors = List.copyOf(neighbors);
        Checks.inRange("cells", cells.size(), 1, Integer.MAX_VALUE);
    }
}
