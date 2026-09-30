package kolo.engine.generation.map;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.engine.content.MapGridDef;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.InvariantViolationException;
import kolo.engine.rng.Rng;
import kolo.engine.state.GridPoint;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;

/**
 * Сітка комірок Вороного (GD §3.5): випадкові центри, кілька ітерацій релаксації Ллойда, діаграма з триангуляції
 * Делоне JTS.
 *
 * <p>Розміри карти — з кількості комірок: площа = комірок × {@code cellSize²}, пропорція — з контенту. Центри —
 * цілі точки строго всередині прямокутника, без повторів.
 *
 * <p>Щоб комірки точно обрізалися краєм карти без окремого обрізання, кожен центр дзеркалиться відносно чотирьох
 * сторін: серединний перпендикуляр між центром і його відображенням — сама сторона. Від JTS береться лише
 * триангуляція; вершини комірок — центри описаних кіл трикутників — рахуються точно в цілих і округлюються однаково
 * для всіх комірок, що їх ділять. Тому сітка вкриває карту без щілин, а сусідство визначається спільними ребрами.
 *
 * <p>{@code double} — лише координати, які приймає JTS; усі цілі в них представлені точно.
 */
public final class VoronoiGrid {

    /** З запасом на море навколо 3500 провінцій суходолу. */
    public static final int MAX_CELLS = MapGridDef.MAX_CELLS;

    /**
     * Початкові центри не ближчі за чверть сторони комірки. Круг виключення займає ~5% площі на комірку, тож
     * відкидання кидків закінчується швидко, а розкид площ до релаксації лишається випадковим.
     */
    static final int SPACING_DIVISOR = 4;

    private VoronoiGrid() {}

    /**
     * @param rng окремий потік сітки; всередині — {@code sites}
     * @param cells кількість комірок, {@code 1..}{@value #MAX_CELLS}
     */
    public static MapGrid generate(Rng rng, MapGridDef grid, int cells) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(grid, "grid");
        Checks.inRange("cells", cells, 1, MAX_CELLS);
        long area = (long) cells * grid.cellSize() * grid.cellSize();
        int width = (int) Math.round(Math.sqrt((double) area * grid.aspectWidth() / grid.aspectHeight()));
        int height = (int) Math.round(Math.sqrt((double) area * grid.aspectHeight() / grid.aspectWidth()));

        int spacing = Math.max(1, grid.cellSize() / SPACING_DIVISOR);
        List<GridPoint> sites = randomSites(rng.fork("sites"), cells, width, height, spacing);
        for (int i = 0; i < grid.relaxation(); i++) {
            sites = relax(sites, voronoi(sites, width, height), width, height);
        }
        return build(sites, voronoi(sites, width, height), width, height);
    }

    /**
     * Цілі точки в {@code [1, width − 1] × [1, height − 1]} (на краю центр збігся б зі своїм відображенням), не ближчі
     * одна до одної за {@code spacing}: інакше між двома майже однаковими центрами виходить комірка-голка, яку
     * округлення вершин до цілих може вивернути.
     */
    static List<GridPoint> randomSites(Rng rng, int count, int width, int height, int spacing) {
        int columns = width / spacing + 1;
        int rows = height / spacing + 1;
        List<List<GridPoint>> buckets = new ArrayList<>(columns * rows);
        for (int i = 0; i < columns * rows; i++) {
            buckets.add(new ArrayList<>());
        }
        long minDistance2 = (long) spacing * spacing;
        List<GridPoint> sites = new ArrayList<>(count);
        while (sites.size() < count) {
            GridPoint site = new GridPoint(1 + rng.nextInt(width - 1), 1 + rng.nextInt(height - 1));
            int column = site.x() / spacing;
            int row = site.y() / spacing;
            if (isFar(site, buckets, column, row, columns, rows, minDistance2)) {
                buckets.get(row * columns + column).add(site);
                sites.add(site);
            }
        }
        return sites;
    }

    private static boolean isFar(
            GridPoint site, List<List<GridPoint>> buckets, int column, int row, int columns, int rows, long min2) {
        for (int r = Math.max(0, row - 1); r <= Math.min(rows - 1, row + 1); r++) {
            for (int c = Math.max(0, column - 1); c <= Math.min(columns - 1, column + 1); c++) {
                for (GridPoint other : buckets.get(r * columns + c)) {
                    long dx = other.x() - site.x();
                    long dy = other.y() - site.y();
                    if (dx * dx + dy * dy < min2) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Цілі вершини комірок у порядку центрів: без повторів, проти годинникової стрілки, від найменшої вершини. */
    static List<List<GridPoint>> voronoi(List<GridPoint> sites, int width, int height) {
        TreeMap<GridPoint, Integer> index = new TreeMap<>();
        List<Coordinate> coordinates = new ArrayList<>(sites.size() * 5);
        for (GridPoint site : sites) {
            index.put(site, index.size());
            int x = site.x();
            int y = site.y();
            coordinates.add(new Coordinate(x, y));
            coordinates.add(new Coordinate(-x, y));
            coordinates.add(new Coordinate(2.0 * width - x, y));
            coordinates.add(new Coordinate(x, -y));
            coordinates.add(new Coordinate(x, 2.0 * height - y));
        }
        DelaunayTriangulationBuilder builder = new DelaunayTriangulationBuilder();
        builder.setSites(coordinates);
        QuadEdgeSubdivision subdivision = builder.getSubdivision();
        List<List<GridPoint>> cells = new ArrayList<>(Collections.nCopies(sites.size(), null));
        for (Object edge : subdivision.getVertexUniqueEdges(false)) {
            QuadEdge start = (QuadEdge) edge;
            Coordinate site = start.orig().getCoordinate();
            // Центри цілі, тож збіг точний; відображення лежать поза прямокутником і в покажчику не знайдуться.
            Integer i = index.get(new GridPoint((int) site.x, (int) site.y));
            if (i != null) {
                cells.set(i, vertices(start, width, height));
            }
        }
        TreeMap<GridPoint, GridPoint> merged = merge(cells, width, height);
        for (int i = 0; i < cells.size(); i++) {
            List<GridPoint> ring = cells.get(i) == null ? null : normalize(cells.get(i), merged);
            cells.set(i, ring);
            if (ring == null || ring.size() < 3 || MapCell.doubleArea(ring) <= 0) {
                throw new InvariantViolationException(ErrorDetails.of(
                        "field", "voronoi_cell", "value", sites.get(i).toString()));
            }
        }
        return cells;
    }

    /**
     * Обхід трикутників Делоне навколо центру: вершина комірки — центр описаного кола трикутника зліва від ребра.
     * Рахується не JTS у {@code double}, а точно в цілих: вершина, що лежить рівно на x,5, у різних трикутниках
     * округлювалася б у різні боки, а точний розрахунок дає однакову цілу точку всім коміркам, що її ділять.
     */
    private static List<GridPoint> vertices(QuadEdge start, int width, int height) {
        List<GridPoint> vertices = new ArrayList<>();
        QuadEdge edge = start;
        do {
            vertices.add(circumcenter(
                    edge.orig().getCoordinate(),
                    edge.dest().getCoordinate(),
                    edge.lNext().dest().getCoordinate(),
                    width,
                    height));
            edge = edge.oPrev();
        } while (edge != start);
        return vertices;
    }

    /**
     * Зливає вершини, що після округлення стоять поруч (не далі одиниці за кожною віссю): дві справжні вершини на
     * відстані в частку одиниці округлення може поміняти місцями, і в комірці з'явиться «шпичак» нульової ширини.
     * Злиття спільне для всіх комірок, тож сітка лишається без щілин; ребро коротше за одиницю зникає, і дві комірки,
     * які воно розділяло, торкаються лише вершиною.
     *
     * <p>Представник групи — кут карти, інакше точка на краю, інакше найменша: так прямокутник карти лишається точним.
     *
     * @return кожна вершина → її представник
     */
    static TreeMap<GridPoint, GridPoint> merge(List<List<GridPoint>> cells, int width, int height) {
        TreeSet<GridPoint> points = new TreeSet<>();
        for (List<GridPoint> cell : cells) {
            if (cell != null) {
                points.addAll(cell);
            }
        }
        TreeMap<GridPoint, GridPoint> representative = new TreeMap<>();
        for (GridPoint start : points) {
            if (representative.containsKey(start)) {
                continue;
            }
            List<GridPoint> group = new ArrayList<>();
            ArrayDeque<GridPoint> queue = new ArrayDeque<>();
            queue.add(start);
            representative.put(start, start);
            while (!queue.isEmpty()) {
                GridPoint point = queue.poll();
                group.add(point);
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        GridPoint next = new GridPoint(point.x() + dx, point.y() + dy);
                        if (points.contains(next) && !representative.containsKey(next)) {
                            representative.put(next, next);
                            queue.add(next);
                        }
                    }
                }
            }
            GridPoint best = group.getFirst();
            for (GridPoint point : group) {
                int rank = rank(point, width, height);
                int bestRank = rank(best, width, height);
                if (rank > bestRank || (rank == bestRank && point.compareTo(best) < 0)) {
                    best = point;
                }
            }
            for (GridPoint point : group) {
                representative.put(point, best);
            }
        }
        return representative;
    }

    /** Кут — 2, край — 1, усередині — 0. */
    private static int rank(GridPoint point, int width, int height) {
        int sides = (point.x() == 0 || point.x() == width ? 1 : 0) + (point.y() == 0 || point.y() == height ? 1 : 0);
        return sides;
    }

    /** Злиті вершини без повторів, проти годинникової стрілки, від найменшої. */
    private static List<GridPoint> normalize(List<GridPoint> vertices, TreeMap<GridPoint, GridPoint> merged) {
        List<GridPoint> ring = new ArrayList<>(vertices.size());
        for (GridPoint vertex : vertices) {
            GridPoint point = merged.get(vertex);
            if (ring.isEmpty() || !ring.getLast().equals(point)) {
                ring.add(point);
            }
        }
        while (ring.size() > 1 && ring.getFirst().equals(ring.getLast())) {
            ring.removeLast();
        }
        if (MapCell.doubleArea(ring) < 0) {
            Collections.reverse(ring);
        }
        Collections.rotate(ring, -ring.indexOf(Collections.min(ring)));
        return ring;
    }

    /** Центр описаного кола трьох цілих точок, округлений до найближчої цілої (половина — вгору) і обрізаний картою. */
    static GridPoint circumcenter(Coordinate a, Coordinate b, Coordinate c, int width, int height) {
        long ax = (long) a.x;
        long ay = (long) a.y;
        long bx = (long) b.x;
        long by = (long) b.y;
        long cx = (long) c.x;
        long cy = (long) c.y;
        long d = 2 * (ax * (by - cy) + bx * (cy - ay) + cx * (ay - by));
        if (d == 0) {
            throw new InvariantViolationException(ErrorDetails.of("field", "delaunay_triangle", "value", "collinear"));
        }
        long a2 = ax * ax + ay * ay;
        long b2 = bx * bx + by * by;
        long c2 = cx * cx + cy * cy;
        long x = a2 * (by - cy) + b2 * (cy - ay) + c2 * (ay - by);
        long y = a2 * (cx - bx) + b2 * (ax - cx) + c2 * (bx - ax);
        return new GridPoint(clamp(roundDiv(x, d), 0, width), clamp(roundDiv(y, d), 0, height));
    }

    /** {@code num / den} до найближчого цілого, половина — вгору. */
    private static long roundDiv(long num, long den) {
        if (den < 0) {
            num = -num;
            den = -den;
        }
        return Math.floorDiv(2 * num + den, 2 * den);
    }

    /** Ітерація Ллойда: центр переходить у центр мас своєї комірки; зайняте місце — найближче вільне. */
    static List<GridPoint> relax(List<GridPoint> sites, List<List<GridPoint>> cells, int width, int height) {
        TreeSet<GridPoint> taken = new TreeSet<>();
        List<GridPoint> relaxed = new ArrayList<>(sites.size());
        for (List<GridPoint> cell : cells) {
            GridPoint site = nearestFree(centroid(cell, width, height), taken, width, height);
            taken.add(site);
            relaxed.add(site);
        }
        return relaxed;
    }

    /** Центр мас многокутника, округлений до цілих у межах {@code [1, width − 1] × [1, height − 1]}. */
    static GridPoint centroid(List<GridPoint> polygon, int width, int height) {
        long area2 = 0;
        long sumX = 0;
        long sumY = 0;
        for (int i = 0; i < polygon.size(); i++) {
            GridPoint a = polygon.get(i);
            GridPoint b = polygon.get((i + 1) % polygon.size());
            long cross = (long) a.x() * b.y() - (long) b.x() * a.y();
            area2 += cross;
            sumX += (a.x() + b.x()) * cross;
            sumY += (a.y() + b.y()) * cross;
        }
        // Центр мас = Σ / (3 · подвоєна площа).
        return new GridPoint(
                clamp(roundDiv(sumX, 3 * area2), 1, width - 1), clamp(roundDiv(sumY, 3 * area2), 1, height - 1));
    }

    /** Обхід квадратними кільцями навколо точки — детермінований, без кидків. */
    private static GridPoint nearestFree(GridPoint target, TreeSet<GridPoint> taken, int width, int height) {
        if (!taken.contains(target)) {
            return target;
        }
        for (int radius = 1; ; radius++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) {
                        continue;
                    }
                    GridPoint candidate = new GridPoint(target.x() + dx, target.y() + dy);
                    if (candidate.x() >= 1
                            && candidate.x() <= width - 1
                            && candidate.y() >= 1
                            && candidate.y() <= height - 1
                            && !taken.contains(candidate)) {
                        return candidate;
                    }
                }
            }
        }
    }

    static MapGrid build(List<GridPoint> sites, List<List<GridPoint>> polygons, int width, int height) {
        List<Integer> order = new ArrayList<>(sites.size());
        for (int i = 0; i < sites.size(); i++) {
            order.add(i);
        }
        order.sort((a, b) -> sites.get(a).compareTo(sites.get(b)));

        List<TreeSet<Integer>> neighbors = new ArrayList<>(order.size());
        TreeMap<Edge, Integer> owners = new TreeMap<>();
        for (int cell = 0; cell < order.size(); cell++) {
            neighbors.add(new TreeSet<>());
            List<GridPoint> ring = polygons.get(order.get(cell));
            for (int v = 0; v < ring.size(); v++) {
                Edge edge = Edge.of(ring.get(v), ring.get((v + 1) % ring.size()));
                Integer owner = owners.putIfAbsent(edge, cell);
                if (owner != null && owner != cell) {
                    neighbors.get(owner).add(cell);
                    neighbors.get(cell).add(owner);
                }
            }
        }

        List<MapCell> cells = new ArrayList<>(order.size());
        for (int cell = 0; cell < order.size(); cell++) {
            List<GridPoint> ring = polygons.get(order.get(cell));
            boolean edge = ring.stream().anyMatch(p -> p.x() == 0 || p.y() == 0 || p.x() == width || p.y() == height);
            cells.add(new MapCell(sites.get(order.get(cell)), ring, List.copyOf(neighbors.get(cell)), edge));
        }
        return new MapGrid(width, height, cells);
    }

    private static int clamp(long value, int min, int max) {
        return Math.clamp(value, min, max);
    }

    /** Ребро без напрямку: кінці впорядковані. */
    private record Edge(GridPoint from, GridPoint to) implements Comparable<Edge> {

        static Edge of(GridPoint a, GridPoint b) {
            return a.compareTo(b) <= 0 ? new Edge(a, b) : new Edge(b, a);
        }

        @Override
        public int compareTo(Edge other) {
            int byFrom = from.compareTo(other.from);
            return byFrom != 0 ? byFrom : to.compareTo(other.to);
        }
    }
}
