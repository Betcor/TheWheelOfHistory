package kolo.client.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kolo.engine.generation.map.GridPoint;
import kolo.engine.view.CellView;
import kolo.engine.view.MapView;

/**
 * Спільні ребра сусідніх комірок з видом межі — для векторного малювання на великому зумі. Вершини сітки цілі й
 * спільні для сусідів, тож ребро однозначно задане парою вершин.
 */
public final class MapEdges {

    /** Вид межі за зростанням важливості; межі між водою не малюються й сюди не потрапляють. */
    public enum Kind {
        PROVINCE,
        COAST,
        COUNTRY
    }

    /**
     * Ребро в координатах {@link MapGeometry}.
     *
     * @param a комірка з одного боку
     * @param b комірка з іншого боку
     */
    public record Edge(double x1, double y1, double x2, double y2, int a, int b, Kind kind) {}

    private final List<Edge> edges;

    private MapEdges(List<Edge> edges) {
        this.edges = List.copyOf(edges);
    }

    public static MapEdges of(MapView view) {
        Map<Key, Integer> open = new HashMap<>();
        List<Edge> edges = new ArrayList<>();
        double height = view.height();
        for (int n = 0; n < view.cells().size(); n++) {
            List<GridPoint> polygon = view.cells().get(n).polygon();
            for (int i = 0; i < polygon.size(); i++) {
                GridPoint p = polygon.get(i);
                GridPoint q = polygon.get((i + 1) % polygon.size());
                Key key = p.compareTo(q) < 0 ? new Key(p, q) : new Key(q, p);
                Integer other = open.remove(key);
                if (other == null) {
                    open.put(key, n);
                    continue;
                }
                Kind kind = kind(view.cells().get(other), view.cells().get(n));
                if (kind != null) {
                    edges.add(new Edge(p.x(), height - p.y(), q.x(), height - q.y(), other, n, kind));
                }
            }
        }
        return new MapEdges(edges);
    }

    public List<Edge> edges() {
        return edges;
    }

    private static Kind kind(CellView a, CellView b) {
        if (!a.isLand() && !b.isLand()) {
            return null;
        }
        if (!a.isLand() || !b.isLand()) {
            return Kind.COAST;
        }
        return a.country().equals(b.country()) ? Kind.PROVINCE : Kind.COUNTRY;
    }

    private record Key(GridPoint from, GridPoint to) {}
}
