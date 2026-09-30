package kolo.client.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.OptionalInt;
import kolo.client.TestWorlds;
import kolo.engine.view.CellView;
import kolo.engine.view.MapView;
import org.junit.jupiter.api.Test;

/** Шари карти зі світу, який згенерував вбудований сервер на вбудованому контенті. */
class MapLayersIntegrationTest {

    private static final MapView VIEW = TestWorlds.DEFAULT;
    private static final MapLayers LAYERS = MapLayers.build(VIEW);

    @Test
    void everySiteHitsItsCell() {
        MapGeometry geometry = LAYERS.geometry();
        for (int n = 0; n < geometry.cells(); n++) {
            assertThat(LAYERS.hitTest().cellAt(geometry.siteX(n), geometry.siteY(n)))
                    .as("cell %d", n)
                    .hasValue(n);
        }
    }

    @Test
    void rasterAgreesWithHitTest() {
        for (int i = 0; i < LAYERS.levels().size(); i++) {
            RasterLevel level = LAYERS.levels().get(i);
            int[] ids = LAYERS.cellIds(i);
            int checked = 0;
            int mismatched = 0;
            for (int y = 0; y < level.height(); y += 7) {
                for (int x = 0; x < level.width(); x += 7) {
                    OptionalInt hit = LAYERS.hitTest().cellAt((x + 0.5) / level.scale(), (y + 0.5) / level.scale());
                    checked++;
                    if (hit.isEmpty() || hit.getAsInt() != ids[y * level.width() + x]) {
                        mismatched++;
                    }
                }
            }
            // Растр і хіт-тест рахують межу однаково, але через різні множення: розбіжність можлива лише в пікселях,
            // чий центр лежить точно на ребрі.
            assertThat(mismatched)
                    .as("level %d: %d of %d", i, mismatched, checked)
                    .isLessThanOrEqualTo(checked / 10_000);
        }
    }

    @Test
    void everyProvinceIsVisibleOnDetailedRaster() {
        int[] pixels = new int[VIEW.cells().size()];
        for (int id : LAYERS.cellIds(LAYERS.levels().size() - 1)) {
            pixels[id]++;
        }
        for (int n = 0; n < pixels.length; n++) {
            assertThat(pixels[n]).as("cell %d", n).isPositive();
        }
    }

    @Test
    void labelsStandOverOwnProvinces() {
        assertThat(LAYERS.labels()).hasSize(VIEW.countries().size());
        for (CountryLabels.Label label : LAYERS.labels()) {
            assertThat(VIEW.cells().get(label.cell()).country()).hasValue(label.country());
        }
    }

    @Test
    void edgesJoinNeighbors() {
        assertThat(LAYERS.edges().edges()).isNotEmpty();
        long countryBorders = 0;
        for (MapEdges.Edge edge : LAYERS.edges().edges()) {
            CellView a = VIEW.cells().get(edge.a());
            assertThat(a.neighbors()).contains(edge.b());
            if (edge.kind() == MapEdges.Kind.COUNTRY) {
                countryBorders++;
            }
        }
        assertThat(countryBorders).isPositive();
    }

    @Test
    void everyModePaints() {
        for (MapMode mode : MapMode.values()) {
            int[] detailed = LAYERS.paint(mode).getLast();
            assertThat(Arrays.stream(detailed).distinct().count())
                    .as(mode.key())
                    .isGreaterThan(3);
        }
    }
}
