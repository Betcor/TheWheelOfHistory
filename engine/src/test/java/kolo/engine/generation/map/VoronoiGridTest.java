package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import kolo.engine.content.MapGridDef;
import kolo.engine.content.TestMaps;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.rng.Rng;
import kolo.engine.state.GridPoint;
import org.junit.jupiter.api.Test;

class VoronoiGridTest {

    @Test
    void mapSizeFollowsCellCountAndAspect() {
        MapGrid grid = VoronoiGrid.generate(Rng.of(1), new MapGridDef(100, 2, 1, 0), 200);

        // 200 × 100² = 2 000 000 = 2000 × 1000.
        assertThat(grid.width()).isEqualTo(2000);
        assertThat(grid.height()).isEqualTo(1000);
        assertThat(grid.cellSide()).isEqualTo(100);
        GridChecks.assertValid(grid, 200);
    }

    @Test
    void singleCellCoversWholeMap() {
        MapGrid grid = VoronoiGrid.generate(Rng.of(7), new MapGridDef(20, 1, 1, 3), 1);

        MapCell cell = grid.cells().getFirst();
        assertThat(cell.polygon())
                .containsExactly(
                        new GridPoint(0, 0), new GridPoint(20, 0), new GridPoint(20, 20), new GridPoint(0, 20));
        assertThat(cell.neighbors()).isEmpty();
        assertThat(cell.edge()).isTrue();
    }

    @Test
    void twoCellsAreNeighbors() {
        MapGrid grid = VoronoiGrid.generate(Rng.of(3), TestMaps.GRID, 2);

        assertThat(grid.cells().get(0).neighbors()).containsExactly(1);
        assertThat(grid.cells().get(1).neighbors()).containsExactly(0);
        GridChecks.assertValid(grid, 2);
    }

    @Test
    void innerCellsDoNotTouchMapEdge() {
        MapGrid grid = VoronoiGrid.generate(Rng.of(11), new MapGridDef(50, 2, 1, 2), 500);

        for (MapCell cell : grid.cells()) {
            boolean touches = cell.polygon().stream()
                    .anyMatch(p -> p.x() == 0 || p.y() == 0 || p.x() == grid.width() || p.y() == grid.height());
            assertThat(cell.edge()).isEqualTo(touches);
        }
        assertThat(grid.cells()).anyMatch(MapCell::edge).anyMatch(cell -> !cell.edge());
    }

    @Test
    void relaxationEvensOutCellAreas() {
        MapGrid raw = VoronoiGrid.generate(Rng.of(5), new MapGridDef(50, 2, 1, 0), 800);
        MapGrid relaxed = VoronoiGrid.generate(Rng.of(5), new MapGridDef(50, 2, 1, 3), 800);

        assertThat(spread(relaxed)).isLessThan(spread(raw));
    }

    @Test
    void differentSeedsGiveDifferentGrids() {
        assertThat(VoronoiGrid.generate(Rng.of(1), TestMaps.GRID, 50))
                .isNotEqualTo(VoronoiGrid.generate(Rng.of(2), TestMaps.GRID, 50));
    }

    @Test
    void rejectsCellCountOutsideLimits() {
        assertThatThrownBy(() -> VoronoiGrid.generate(Rng.of(1), TestMaps.GRID, 0))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
        assertThatThrownBy(() -> VoronoiGrid.generate(Rng.of(1), TestMaps.GRID, VoronoiGrid.MAX_CELLS + 1))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void cellRejectsDegenerateOrUnsortedData() {
        List<GridPoint> triangle = List.of(new GridPoint(0, 0), new GridPoint(1, 0), new GridPoint(0, 1));
        assertThatThrownBy(() -> new MapCell(new GridPoint(0, 0), triangle.subList(0, 2), List.of(), true))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new MapCell(new GridPoint(0, 0), triangle, List.of(2, 1), true))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.OUT_OF_ORDER));
        assertThat(new MapCell(new GridPoint(0, 0), triangle, List.of(), true).doubleArea())
                .isEqualTo(1);
    }

    @Test
    void gridDefRejectsValuesOutsideLimits() {
        assertThatThrownBy(() -> new MapGridDef(MapGridDef.MIN_CELL_SIZE - 1, 2, 1, 0))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new MapGridDef(MapGridDef.MAX_CELL_SIZE + 1, 2, 1, 0))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new MapGridDef(100, 0, 1, 0)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new MapGridDef(100, 2, MapGridDef.MAX_ASPECT + 1, 0))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new MapGridDef(100, 2, 1, MapGridDef.MAX_RELAXATION + 1))
                .isInstanceOf(ValidationException.class);
    }

    /** Відношення найбільшої площі до найменшої. */
    private static double spread(MapGrid grid) {
        long min = Long.MAX_VALUE;
        long max = 0;
        for (MapCell cell : grid.cells()) {
            min = Math.min(min, cell.doubleArea());
            max = Math.max(max, cell.doubleArea());
        }
        return (double) max / min;
    }
}
