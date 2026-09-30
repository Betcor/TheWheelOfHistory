package kolo.engine.state;

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

/**
 * Комірка карти в стані світу: геометрія й географія, що не змінюються після генерації. Змінне (власник, населення,
 * родовища) — у {@link Province} суходолу.
 *
 * @param site центр комірки
 * @param polygon вершини проти годинникової стрілки, вісь {@code y} — вгору; щонайменше 3
 * @param neighbors номери сусідніх комірок за зростанням
 * @param kind суходіл, море чи озеро
 * @param continent номер материка; лише суходіл
 * @param seaZone морська зона; лише море
 * @param coast морські зони, до яких провінція має вихід, за зростанням номера; лише суходіл, порожньо — не на березі
 * @param terrain тип місцевості; лише суходіл
 * @param relief рельєф під покривом; лише суходіл
 * @param climate кліматичний пояс; лише суходіл
 * @param height висота {@code 0..}{@value ReliefDef#MAX_HEIGHT}; лише суходіл
 * @param fertility родючість {@code 0..}{@value FertilityDef#MAX_VALUE}; лише суходіл
 * @param river чи тече провінцією річка
 * @param downstream куди стікає річка: сусідня комірка; лише для комірок з річкою
 */
public record MapTile(
        GridPoint site,
        List<GridPoint> polygon,
        List<Integer> neighbors,
        CellKind kind,
        OptionalInt continent,
        Optional<SeaZoneId> seaZone,
        List<SeaZoneId> coast,
        Optional<Terrain> terrain,
        Optional<Relief> relief,
        Optional<Climate> climate,
        OptionalInt height,
        OptionalInt fertility,
        boolean river,
        OptionalInt downstream) {

    public MapTile {
        Objects.requireNonNull(site, "site");
        polygon = List.copyOf(polygon);
        neighbors = List.copyOf(neighbors);
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(continent, "continent");
        Objects.requireNonNull(seaZone, "seaZone");
        coast = List.copyOf(coast);
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(relief, "relief");
        Objects.requireNonNull(climate, "climate");
        Objects.requireNonNull(height, "height");
        Objects.requireNonNull(fertility, "fertility");
        Objects.requireNonNull(downstream, "downstream");
        Checks.inRange("polygon", polygon.size(), 3, Integer.MAX_VALUE);
        boolean land = kind == CellKind.LAND;
        require("continent", continent.isPresent() == land);
        require("sea_zone", seaZone.isPresent() == (kind == CellKind.SEA));
        require("coast", coast.isEmpty() || land);
        require("terrain", terrain.isPresent() == land);
        require("relief", relief.isPresent() == land);
        require("climate", climate.isPresent() == land);
        require("height", height.isPresent() == land);
        require("fertility", fertility.isPresent() == land);
        require("river", !river || land);
        require("downstream", downstream.isPresent() == river);
        require(
                "neighbors",
                isAscending(neighbors.stream().mapToLong(Integer::longValue).toArray()));
        require("coast", isAscending(coast.stream().mapToLong(SeaZoneId::number).toArray()));
        continent.ifPresent(value -> Checks.inRange("continent", value, 0, Integer.MAX_VALUE));
        height.ifPresent(value -> Checks.inRange("height", value, 0, ReliefDef.MAX_HEIGHT));
        fertility.ifPresent(value -> Checks.inRange("fertility", value, 0, FertilityDef.MAX_VALUE));
    }

    /** Чи це суходіл — провінція. */
    public boolean isLand() {
        return kind == CellKind.LAND;
    }

    private static boolean isAscending(long[] values) {
        for (int i = 1; i < values.length; i++) {
            if (values[i - 1] >= values[i]) {
                return false;
            }
        }
        return true;
    }

    private static void require(String field, boolean condition) {
        if (!condition) {
            throw new ValidationException(ErrorCode.VALUE_OUT_OF_RANGE, ErrorDetails.of("field", field));
        }
    }
}
