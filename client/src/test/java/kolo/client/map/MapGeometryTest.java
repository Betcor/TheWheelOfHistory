package kolo.client.map;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MapGeometryTest {

    private static final MapGeometry GEOMETRY = MapGeometry.of(TestMaps.MAP);

    @Test
    void northIsUp() {
        // Рядок 0 рушія (y 0..10) — унизу екрана: v 10..20.
        assertThat(GEOMETRY.minY(0)).isEqualTo(10);
        assertThat(GEOMETRY.maxY(0)).isEqualTo(20);
        assertThat(GEOMETRY.minY(3)).isEqualTo(0);
        assertThat(GEOMETRY.siteX(1)).isEqualTo(15);
        assertThat(GEOMETRY.siteY(1)).isEqualTo(15);
        assertThat(GEOMETRY.width()).isEqualTo(30);
        assertThat(GEOMETRY.height()).isEqualTo(20);
        assertThat(GEOMETRY.cells()).isEqualTo(6);
    }

    @Test
    void containsInterior() {
        assertThat(GEOMETRY.contains(0, 5, 15)).isTrue();
        assertThat(GEOMETRY.contains(3, 5, 15)).isFalse();
        assertThat(GEOMETRY.contains(3, 5, 5)).isTrue();
    }

    @Test
    void sharedEdgeBelongsToExactlyOneCell() {
        for (double v = 0.25; v < 20; v += 0.5) {
            int owners = 0;
            for (int n = 0; n < GEOMETRY.cells(); n++) {
                if (GEOMETRY.contains(n, 10, v)) {
                    owners++;
                }
            }
            assertThat(owners).as("v=%s", v).isEqualTo(1);
        }
        int owners = 0;
        for (int n = 0; n < GEOMETRY.cells(); n++) {
            if (GEOMETRY.contains(n, 5, 10)) {
                owners++;
            }
        }
        assertThat(owners).isEqualTo(1);
    }

    @Test
    void crossingDoesNotDependOnDirection() {
        double forward = MapGeometry.crossing(0.1, 0.3, 7.7, 9.1, 4.2);
        double backward = MapGeometry.crossing(7.7, 9.1, 0.1, 0.3, 4.2);
        assertThat(forward).isEqualTo(backward);
        assertThat(MapGeometry.crossing(0, 0, 10, 0, 0)).isNaN();
        assertThat(MapGeometry.crossing(0, 0, 0, 10, 10)).isNaN();
        assertThat(MapGeometry.crossing(0, 0, 0, 10, 0)).isEqualTo(0);
    }
}
