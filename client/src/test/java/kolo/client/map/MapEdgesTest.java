package kolo.client.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MapEdgesTest {

    @Test
    void classifiesSharedEdges() {
        List<MapEdges.Edge> edges = MapEdges.of(TestMaps.MAP).edges();

        // 7 внутрішніх ребер сітки 3 × 2, з них одне — між морем і озером — не малюється.
        assertThat(edges).hasSize(6);
        assertThat(kind(edges, 0, 1)).isEqualTo(MapEdges.Kind.COUNTRY);
        assertThat(kind(edges, 0, 3)).isEqualTo(MapEdges.Kind.PROVINCE);
        assertThat(kind(edges, 1, 2)).isEqualTo(MapEdges.Kind.COAST);
        assertThat(kind(edges, 1, 4)).isEqualTo(MapEdges.Kind.COUNTRY);
        assertThat(kind(edges, 3, 4)).isEqualTo(MapEdges.Kind.COUNTRY);
        assertThat(kind(edges, 4, 5)).isEqualTo(MapEdges.Kind.COAST);
    }

    @Test
    void edgesAreInScreenCoordinates() {
        MapEdges.Edge edge = MapEdges.of(TestMaps.MAP).edges().stream()
                .filter(e -> e.a() == 0 && e.b() == 1)
                .findFirst()
                .orElseThrow();

        assertThat(edge.x1()).isEqualTo(10);
        assertThat(edge.x2()).isEqualTo(10);
        assertThat(List.of(edge.y1(), edge.y2())).containsExactlyInAnyOrder(10.0, 20.0);
    }

    private static MapEdges.Kind kind(List<MapEdges.Edge> edges, int a, int b) {
        return edges.stream()
                .filter(edge -> Math.min(edge.a(), edge.b()) == a && Math.max(edge.a(), edge.b()) == b)
                .findFirst()
                .orElseThrow()
                .kind();
    }
}
