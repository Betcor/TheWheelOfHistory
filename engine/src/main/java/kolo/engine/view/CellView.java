package kolo.engine.view;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import kolo.engine.content.FertilityDef;
import kolo.engine.content.ReliefDef;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.map.GridPoint;
import kolo.engine.state.Climate;
import kolo.engine.state.Relief;
import kolo.engine.state.Terrain;

/**
 * Комірка карти так, як її бачить гравець: геометрія й усе, що показують режими карти.
 *
 * @param site центр комірки
 * @param polygon вершини проти годинникової стрілки, вісь {@code y} — вгору (як у сітці рушія); щонайменше 3
 * @param neighbors номери сусідніх комірок за зростанням
 * @param kind суходіл, море чи озеро
 * @param terrain тип місцевості; лише суходіл
 * @param relief рельєф під покривом; лише суходіл
 * @param climate кліматичний пояс; лише суходіл
 * @param height висота {@code 0..}{@value ReliefDef#MAX_HEIGHT}; лише суходіл
 * @param fertility родючість {@code 0..}{@value FertilityDef#MAX_VALUE}; лише суходіл
 * @param river чи тече провінцією річка
 * @param downstream куди стікає річка: сусідня комірка; лише для комірок з річкою
 * @param country номер держави-власника; порожньо — вода або нічийна земля
 */
public record CellView(
        GridPoint site,
        List<GridPoint> polygon,
        List<Integer> neighbors,
        CellKind kind,
        Optional<Terrain> terrain,
        Optional<Relief> relief,
        Optional<Climate> climate,
        OptionalInt height,
        OptionalInt fertility,
        boolean river,
        OptionalInt downstream,
        OptionalInt country) {

    public CellView {
        Objects.requireNonNull(site, "site");
        polygon = List.copyOf(polygon);
        neighbors = List.copyOf(neighbors);
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(relief, "relief");
        Objects.requireNonNull(climate, "climate");
        Objects.requireNonNull(height, "height");
        Objects.requireNonNull(fertility, "fertility");
        Objects.requireNonNull(downstream, "downstream");
        Objects.requireNonNull(country, "country");
        Checks.inRange("polygon", polygon.size(), 3, Integer.MAX_VALUE);
        boolean land = kind == CellKind.LAND;
        require("terrain", terrain.isPresent() == land);
        require("relief", relief.isPresent() == land);
        require("climate", climate.isPresent() == land);
        require("height", height.isPresent() == land);
        require("fertility", fertility.isPresent() == land);
        require("river", !river || land);
        require("downstream", downstream.isPresent() == river);
        require("country", country.isEmpty() || land);
        height.ifPresent(value -> Checks.inRange("height", value, 0, ReliefDef.MAX_HEIGHT));
        fertility.ifPresent(value -> Checks.inRange("fertility", value, 0, FertilityDef.MAX_VALUE));
    }

    /** Чи це суходіл. */
    public boolean isLand() {
        return kind == CellKind.LAND;
    }

    private static void require(String field, boolean condition) {
        if (!condition) {
            throw new ValidationException(ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", field));
        }
    }
}
