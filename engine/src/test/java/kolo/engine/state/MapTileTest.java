package kolo.engine.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import kolo.engine.error.ValidationException;
import org.junit.jupiter.api.Test;

class MapTileTest {

    private static final MapTile LAND = TestWorldStates.land(1, List.of(0, 2), List.of(SeaZoneId.of(0)));
    private static final MapTile SEA = TestWorldStates.water(3, CellKind.SEA, List.of(2));

    @Test
    void landHasGeographyAndWaterDoesNot() {
        assertThat(LAND.isLand()).isTrue();
        assertThat(LAND.terrain()).contains(Terrain.PLAIN);
        assertThat(SEA.isLand()).isFalse();
        assertThat(SEA.seaZone()).contains(SeaZoneId.of(0));
        assertThat(TestWorldStates.water(4, CellKind.LAKE, List.of()).seaZone()).isEmpty();
    }

    @Test
    void landWithoutGeographyIsRejected() {
        assertInvalid(() -> new MapTile(
                LAND.site(),
                LAND.polygon(),
                LAND.neighbors(),
                CellKind.LAND,
                LAND.continent(),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                LAND.relief(),
                LAND.climate(),
                LAND.height(),
                LAND.fertility(),
                false,
                OptionalInt.empty()));
    }

    @Test
    void waterWithGeographyOrCoastIsRejected() {
        assertInvalid(() -> new MapTile(
                SEA.site(),
                SEA.polygon(),
                SEA.neighbors(),
                CellKind.SEA,
                OptionalInt.of(0),
                SEA.seaZone(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                false,
                OptionalInt.empty()));
        assertInvalid(() -> new MapTile(
                SEA.site(),
                SEA.polygon(),
                SEA.neighbors(),
                CellKind.SEA,
                OptionalInt.empty(),
                SEA.seaZone(),
                List.of(SeaZoneId.of(0)),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                false,
                OptionalInt.empty()));
    }

    @Test
    void seaNeedsZoneAndLakeHasNone() {
        assertInvalid(() -> new MapTile(
                SEA.site(),
                SEA.polygon(),
                SEA.neighbors(),
                CellKind.SEA,
                OptionalInt.empty(),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                false,
                OptionalInt.empty()));
        assertInvalid(() -> new MapTile(
                SEA.site(),
                SEA.polygon(),
                SEA.neighbors(),
                CellKind.LAKE,
                OptionalInt.empty(),
                Optional.of(SeaZoneId.of(0)),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                false,
                OptionalInt.empty()));
    }

    @Test
    void riverNeedsDownstreamAndOrderedLists() {
        assertInvalid(() -> river(true, OptionalInt.empty(), List.of(0, 2)));
        assertInvalid(() -> river(false, OptionalInt.of(2), List.of(0, 2)));
        assertInvalid(() -> river(true, OptionalInt.of(2), List.of(2, 0)));
        assertThat(river(true, OptionalInt.of(2), List.of(0, 2)).downstream()).hasValue(2);
    }

    @Test
    void heightAndFertilityStayInBounds() {
        assertInvalid(() -> withHeight(101));
        assertInvalid(() -> withHeight(-1));
        assertThat(withHeight(100).height()).hasValue(100);
    }

    private static MapTile river(boolean river, OptionalInt downstream, List<Integer> neighbors) {
        return new MapTile(
                LAND.site(),
                LAND.polygon(),
                neighbors,
                CellKind.LAND,
                LAND.continent(),
                Optional.empty(),
                List.of(),
                LAND.terrain(),
                LAND.relief(),
                LAND.climate(),
                LAND.height(),
                LAND.fertility(),
                river,
                downstream);
    }

    private static MapTile withHeight(int height) {
        return new MapTile(
                LAND.site(),
                LAND.polygon(),
                LAND.neighbors(),
                CellKind.LAND,
                LAND.continent(),
                Optional.empty(),
                List.of(),
                LAND.terrain(),
                LAND.relief(),
                LAND.climate(),
                OptionalInt.of(height),
                LAND.fertility(),
                false,
                OptionalInt.empty());
    }

    private static void assertInvalid(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOf(ValidationException.class);
    }
}
