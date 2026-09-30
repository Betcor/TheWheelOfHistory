package kolo.engine.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import kolo.engine.error.ValidationException;
import org.junit.jupiter.api.Test;

class GameMapTest {

    private static final GameMap MAP = TestWorldStates.map();

    @Test
    void findsTilesAndZones() {
        assertThat(MAP.tile(ProvinceId.of(2))).isSameAs(MAP.tile(2));
        assertThat(MAP.tile(2).coast()).containsExactly(SeaZoneId.of(0));
        assertThat(MAP.seaZone(SeaZoneId.of(0)).cells()).containsExactly(3);
    }

    @Test
    void rejectsUnknownTilesAndZones() {
        assertThatThrownBy(() -> MAP.tile(5)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> MAP.tile(ProvinceId.of(5))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> MAP.seaZone(SeaZoneId.of(1))).isInstanceOf(ValidationException.class);
    }

    @Test
    void seaZonesAreNumberedInOrder() {
        assertThatThrownBy(() -> new GameMap(
                        MAP.width(),
                        MAP.height(),
                        MAP.tiles(),
                        List.of(new SeaZoneState(SeaZoneId.of(1), List.of(3), List.of()))))
                .isInstanceOf(ValidationException.class);
    }
}
