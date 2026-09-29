package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class ContinentMapTest {

    private static final int SEA = ContinentMap.SEA;

    @Test
    void mapKnowsLandAndSea() {
        ContinentMap map = new ContinentMap(
                List.of(new Continent(1, 2, List.of(0, 1)), new Continent(4, 1, List.of(4))),
                List.of(0, 0, SEA, SEA, 1),
                List.of());

        assertThat(map.isLand(0)).isTrue();
        assertThat(map.isLand(2)).isFalse();
        assertThat(map.landCells()).isEqualTo(3);
    }

    @Test
    void continentRequiresSortedCellsWithSeed() {
        assertFails(() -> new Continent(0, 1, List.of()), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new Continent(2, 1, List.of(2, 1)), ErrorCode.OUT_OF_ORDER);
        assertFails(() -> new Continent(2, 1, List.of(1, 1)), ErrorCode.OUT_OF_ORDER);
        assertFails(() -> new Continent(3, 1, List.of(1, 2)), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new Continent(1, 0, List.of(1)), ErrorCode.VALUE_OUT_OF_RANGE);
    }

    @Test
    void cellOwnersMustMatchContinents() {
        List<Continent> one = List.of(new Continent(0, 1, List.of(0)));

        assertFails(() -> new ContinentMap(List.of(), List.of(SEA), List.of()), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new ContinentMap(one, List.of(SEA, 0), List.of()), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new ContinentMap(one, List.of(0, 0), List.of()), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(() -> new ContinentMap(one, List.of(0, 3), List.of()), ErrorCode.VALUE_OUT_OF_RANGE);
        assertFails(
                () -> new ContinentMap(List.of(new Continent(2, 1, List.of(2))), List.of(0), List.of()),
                ErrorCode.VALUE_OUT_OF_RANGE);
    }

    private static void assertFails(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
