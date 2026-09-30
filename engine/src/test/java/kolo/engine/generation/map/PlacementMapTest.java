package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import kolo.engine.content.AreaLevelId;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import org.junit.jupiter.api.Test;

class PlacementMapTest {

    private static final int NONE = PlacementMap.NONE;

    @Test
    void mapAnswersOwnersAndCounts() {
        PlacementMap map = new PlacementMap(
                List.of(country(0, List.of(0, 1)), country(1, List.of(3))), List.of(0, 0, NONE, 1, NONE));

        assertThat(map.country(1)).isZero();
        assertThat(map.country(3)).isEqualTo(1);
        assertThat(map.isClaimed(2)).isFalse();
        assertThat(map.claimedCells()).isEqualTo(3);
    }

    @Test
    void mapRejectsInconsistentOwners() {
        assertThatThrownBy(() -> new PlacementMap(List.of(country(0, List.of(0, 1))), List.of(0, NONE)))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
        assertThatThrownBy(() -> new PlacementMap(List.of(country(0, List.of(0))), List.of(0, 0)))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
        assertThatThrownBy(() -> new PlacementMap(List.of(), List.of(NONE))).isInstanceOf(ValidationException.class);
    }

    @Test
    void countryRequiresSortedCellsWithSeed() {
        assertThatThrownBy(
                        () -> new PlacedCountry(0, new AreaLevelId("small"), List.of(), 2, 0, List.of(1, 0), List.of()))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.OUT_OF_ORDER));
        assertThatThrownBy(
                        () -> new PlacedCountry(0, new AreaLevelId("small"), List.of(), 2, 5, List.of(0, 1), List.of()))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
        assertThatThrownBy(() -> new PlacedCountry(0, new AreaLevelId("small"), List.of(), 2, 0, List.of(), List.of()))
                .isInstanceOf(ValidationException.class);
    }

    private static PlacedCountry country(int continent, List<Integer> cells) {
        return new PlacedCountry(
                continent,
                new AreaLevelId("small"),
                List.of("small_country"),
                cells.size(),
                cells.getFirst(),
                cells,
                List.of());
    }
}
