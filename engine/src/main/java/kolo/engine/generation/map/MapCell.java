package kolo.engine.generation.map;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.GridPoint;

/**
 * Комірка діаграми Вороного — майбутня провінція або частина моря.
 *
 * @param site центр комірки: точка, від якої будувалася діаграма
 * @param polygon вершини без повтору першої, проти годинникової стрілки (вісь {@code y} — вгору), починаючи з
 *     найменшої за {@link GridPoint#compareTo}; щонайменше 3
 * @param neighbors номери сусідніх комірок у {@link MapGrid#cells()} за зростанням — тих, з якими спільне ребро, а не
 *     лише вершина
 * @param edge чи торкається комірка краю карти
 */
public record MapCell(GridPoint site, List<GridPoint> polygon, List<Integer> neighbors, boolean edge) {

    public MapCell {
        Objects.requireNonNull(site, "site");
        polygon = List.copyOf(polygon);
        neighbors = List.copyOf(neighbors);
        Checks.inRange("polygon", polygon.size(), 3, Integer.MAX_VALUE);
        int previous = -1;
        for (int neighbor : neighbors) {
            if (neighbor <= previous) {
                throw new ValidationException(
                        ErrorCode.OUT_OF_ORDER, ErrorDetails.of("field", "neighbors", "value", neighbor));
            }
            previous = neighbor;
        }
    }

    /** Подвоєна площа за формулою шнурівки: ціла й точна, додатна для обходу проти годинникової стрілки. */
    public long doubleArea() {
        return doubleArea(polygon);
    }

    static long doubleArea(List<GridPoint> polygon) {
        long sum = 0;
        for (int i = 0; i < polygon.size(); i++) {
            GridPoint a = polygon.get(i);
            GridPoint b = polygon.get((i + 1) % polygon.size());
            sum += (long) a.x() * b.y() - (long) b.x() * a.y();
        }
        return sum;
    }
}
