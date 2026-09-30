package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import kolo.engine.state.GridPoint;

/** Перевірки цілісності сітки, спільні для тестів рушія й інтеграційних тестів на вбудованому контенті. */
public final class GridChecks {

    private GridChecks() {}

    /** Комірки вкривають карту без щілин і накладань, сусідство симетричне, сітка зв'язна, центр — у своїй комірці. */
    public static void assertValid(MapGrid grid, int cells) {
        assertThat(grid.cells()).hasSize(cells);
        long area = 0;
        for (int i = 0; i < grid.cells().size(); i++) {
            MapCell cell = grid.cells().get(i);
            area += cell.doubleArea();
            assertThat(cell.doubleArea()).isPositive();
            assertThat(cell.polygon().getFirst()).isEqualTo(Collections.min(cell.polygon()));
            assertThat(contains(cell.polygon(), cell.site())).isTrue();
            assertThat(cell.neighbors()).doesNotContain(i);
            for (int neighbor : cell.neighbors()) {
                assertThat(grid.cells().get(neighbor).neighbors()).contains(i);
            }
            for (GridPoint p : cell.polygon()) {
                assertThat(p.x()).isBetween(0, grid.width());
                assertThat(p.y()).isBetween(0, grid.height());
            }
            assertNoSpikes(cell.polygon());
            if (i > 0) {
                assertThat(grid.cells().get(i - 1).site()).isLessThan(cell.site());
            }
        }
        assertThat(area).isEqualTo(2L * grid.width() * grid.height());
        assertThat(reachable(grid)).isEqualTo(cells);
    }

    private static int reachable(MapGrid grid) {
        boolean[] seen = new boolean[grid.cells().size()];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(0);
        seen[0] = true;
        int count = 0;
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            count++;
            for (int neighbor : grid.cells().get(cell).neighbors()) {
                if (!seen[neighbor]) {
                    seen[neighbor] = true;
                    queue.add(neighbor);
                }
            }
        }
        return count;
    }

    /** Жодне ребро не повертає назад уздовж попереднього — комірка без «шпичаків» нульової ширини. */
    private static void assertNoSpikes(List<GridPoint> polygon) {
        for (int i = 0; i < polygon.size(); i++) {
            GridPoint a = polygon.get(i);
            GridPoint b = polygon.get((i + 1) % polygon.size());
            GridPoint c = polygon.get((i + 2) % polygon.size());
            long cross = (long) (b.x() - a.x()) * (c.y() - b.y()) - (long) (b.y() - a.y()) * (c.x() - b.x());
            long dot = (long) (b.x() - a.x()) * (c.x() - b.x()) + (long) (b.y() - a.y()) * (c.y() - b.y());
            assertThat(cross != 0 || dot > 0)
                    .as("шпичак у %s біля %s", polygon, b)
                    .isTrue();
        }
    }

    /**
     * Точка всередині многокутника або на його межі — перевірка променем. Опуклості не вимагає: після округлення й
     * злиття вершин комірка може бути трохи неопуклою біля короткого ребра.
     */
    static boolean contains(List<GridPoint> polygon, GridPoint point) {
        boolean inside = false;
        for (int i = 0; i < polygon.size(); i++) {
            GridPoint a = polygon.get(i);
            GridPoint b = polygon.get((i + 1) % polygon.size());
            long cross = (long) (b.x() - a.x()) * (point.y() - a.y()) - (long) (b.y() - a.y()) * (point.x() - a.x());
            boolean onEdge = cross == 0
                    && point.x() >= Math.min(a.x(), b.x())
                    && point.x() <= Math.max(a.x(), b.x())
                    && point.y() >= Math.min(a.y(), b.y())
                    && point.y() <= Math.max(a.y(), b.y());
            if (onEdge) {
                return true;
            }
            if ((a.y() > point.y()) != (b.y() > point.y())) {
                // Перетин променя вправо з ребром: x перетину > x точки, без ділення.
                long dy = b.y() - a.y();
                long lhs = (long) (point.x() - a.x()) * dy;
                long rhs = (long) (b.x() - a.x()) * (point.y() - a.y());
                if (dy > 0 ? lhs < rhs : lhs > rhs) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }
}
