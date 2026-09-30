package kolo.client.map;

import java.util.OptionalInt;
import java.util.function.IntConsumer;

/**
 * Яка комірка під точкою карти. Комірки розкладено в кошики рівномірної сітки за їхніми рамками, тож запит перевіряє
 * лише кілька многокутників поблизу.
 */
public final class MapHitTest {

    private final MapGeometry geometry;
    private final double bucket;
    private final int columns;
    private final int rows;
    private final int[][] buckets;

    public MapHitTest(MapGeometry geometry, double bucketSize) {
        this.geometry = geometry;
        this.bucket = Math.max(1, bucketSize);
        columns = Math.max(1, (int) Math.ceil(geometry.width() / bucket));
        rows = Math.max(1, (int) Math.ceil(geometry.height() / bucket));
        int[] counts = new int[columns * rows];
        for (int n = 0; n < geometry.cells(); n++) {
            forEachBucket(n, index -> counts[index]++);
        }
        buckets = new int[columns * rows][];
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = new int[counts[i]];
            counts[i] = 0;
        }
        for (int n = 0; n < geometry.cells(); n++) {
            int cell = n;
            forEachBucket(n, index -> buckets[index][counts[index]++] = cell);
        }
    }

    /** Комірка під точкою; порожньо — точка поза картою. */
    public OptionalInt cellAt(double u, double v) {
        if (!(u >= 0 && u < geometry.width() && v >= 0 && v < geometry.height())) {
            return OptionalInt.empty();
        }
        int[] candidates = buckets[column(u) + row(v) * columns];
        for (int cell : candidates) {
            if (geometry.contains(cell, u, v)) {
                return OptionalInt.of(cell);
            }
        }
        // Точка на самому краю карти або в щілині округлення вершин: найближчий центр серед сусідів по кошику.
        int nearest = -1;
        double best = Double.MAX_VALUE;
        for (int cell : candidates) {
            double du = geometry.siteX(cell) - u;
            double dv = geometry.siteY(cell) - v;
            double distance = du * du + dv * dv;
            if (distance < best) {
                best = distance;
                nearest = cell;
            }
        }
        return nearest < 0 ? OptionalInt.empty() : OptionalInt.of(nearest);
    }

    private void forEachBucket(int n, IntConsumer action) {
        int c0 = column(geometry.minX(n));
        int c1 = column(geometry.maxX(n));
        int r0 = row(geometry.minY(n));
        int r1 = row(geometry.maxY(n));
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                action.accept(c + r * columns);
            }
        }
    }

    private int column(double u) {
        return Math.clamp((long) Math.floor(u / bucket), 0, columns - 1);
    }

    private int row(double v) {
        return Math.clamp((long) Math.floor(v / bucket), 0, rows - 1);
    }
}
