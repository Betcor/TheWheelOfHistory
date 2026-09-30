package kolo.client.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class MapHitTestTest {

    private static final MapGeometry GEOMETRY = MapGeometry.of(TestMaps.MAP);

    @Test
    void findsCellUnderPoint() {
        MapHitTest hit = new MapHitTest(GEOMETRY, 7);

        assertThat(hit.cellAt(5, 15)).hasValue(0);
        assertThat(hit.cellAt(25, 15)).hasValue(2);
        assertThat(hit.cellAt(15, 5)).hasValue(4);
        assertThat(hit.cellAt(29.99, 0.01)).hasValue(5);
        for (int n = 0; n < GEOMETRY.cells(); n++) {
            assertThat(hit.cellAt(GEOMETRY.siteX(n), GEOMETRY.siteY(n))).hasValue(n);
        }
    }

    @Test
    void outsideMapIsEmpty() {
        MapHitTest hit = new MapHitTest(GEOMETRY, 10);

        assertThat(hit.cellAt(-0.1, 5)).isEqualTo(OptionalInt.empty());
        assertThat(hit.cellAt(30, 5)).isEqualTo(OptionalInt.empty());
        assertThat(hit.cellAt(5, 20)).isEqualTo(OptionalInt.empty());
        assertThat(hit.cellAt(Double.NaN, 5)).isEqualTo(OptionalInt.empty());
    }

    @Test
    void bucketSizeDoesNotChangeAnswer() {
        MapHitTest small = new MapHitTest(GEOMETRY, 1);
        MapHitTest large = new MapHitTest(GEOMETRY, 1000);
        for (double u = 0.5; u < 30; u += 1.3) {
            for (double v = 0.5; v < 20; v += 1.1) {
                assertThat(small.cellAt(u, v)).isEqualTo(large.cellAt(u, v));
            }
        }
    }
}
