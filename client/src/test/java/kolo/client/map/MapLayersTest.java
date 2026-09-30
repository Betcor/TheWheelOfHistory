package kolo.client.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MapLayersTest {

    private static final MapLayers LAYERS = MapLayers.build(TestMaps.MAP);

    @Test
    void zoomLimitsFollowProvinceSize() {
        // Сторона провінції 10 одиниць → найбільший зум 16 пікселів на одиницю.
        assertThat(LAYERS.maxScale()).isEqualTo(MapLayers.MAX_CELL_PIXELS / TestMaps.SIDE);
        assertThat(LAYERS.levels()).isNotEmpty();
        assertThat(LAYERS.levels().getLast().scale()).isLessThanOrEqualTo(LAYERS.maxScale());
        assertThat(LAYERS.vectorScale()).isEqualTo(LAYERS.levels().getLast().scale() * MapLayers.MAX_MAGNIFICATION);
    }

    @Test
    void paintsEveryLevel() {
        List<int[]> painted = LAYERS.paint(MapMode.CLIMATE);

        assertThat(painted).hasSameSizeAs(LAYERS.levels());
        for (int i = 0; i < painted.size(); i++) {
            RasterLevel level = LAYERS.levels().get(i);
            assertThat(painted.get(i)).hasSize(level.width() * level.height());
            assertThat(LAYERS.cellIds(i)).hasSize(level.width() * level.height());
        }
    }

    @Test
    void keepsParts() {
        assertThat(LAYERS.view()).isSameAs(TestMaps.MAP);
        assertThat(LAYERS.geometry().cells()).isEqualTo(6);
        assertThat(LAYERS.hitTest().cellAt(5, 5)).hasValue(3);
        assertThat(LAYERS.edges().edges()).hasSize(6);
        assertThat(LAYERS.labels()).hasSize(2);
    }
}
