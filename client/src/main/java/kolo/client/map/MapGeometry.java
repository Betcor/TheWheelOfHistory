package kolo.client.map;

import java.util.List;
import kolo.engine.state.GridPoint;
import kolo.engine.view.CellView;
import kolo.engine.view.MapView;

/**
 * Геометрія карти в координатах екрана: вісь {@code v} — вниз (у сітці рушія {@code y} — вгору), тож північ угорі.
 * Одиниці — ті самі, що в сітці. Спільна для растеризації, хіт-тесту й векторного малювання.
 */
public final class MapGeometry {

    private final double width;
    private final double height;
    private final double[][] xs;
    private final double[][] ys;
    private final double[] siteX;
    private final double[] siteY;
    private final double[] minX;
    private final double[] minY;
    private final double[] maxX;
    private final double[] maxY;

    private MapGeometry(MapView view) {
        width = view.width();
        height = view.height();
        int cells = view.cells().size();
        xs = new double[cells][];
        ys = new double[cells][];
        siteX = new double[cells];
        siteY = new double[cells];
        minX = new double[cells];
        minY = new double[cells];
        maxX = new double[cells];
        maxY = new double[cells];
        for (int n = 0; n < cells; n++) {
            CellView cell = view.cells().get(n);
            List<GridPoint> polygon = cell.polygon();
            double[] x = new double[polygon.size()];
            double[] y = new double[polygon.size()];
            minX[n] = Double.MAX_VALUE;
            minY[n] = Double.MAX_VALUE;
            maxX[n] = -Double.MAX_VALUE;
            maxY[n] = -Double.MAX_VALUE;
            for (int i = 0; i < polygon.size(); i++) {
                x[i] = polygon.get(i).x();
                y[i] = height - polygon.get(i).y();
                minX[n] = Math.min(minX[n], x[i]);
                minY[n] = Math.min(minY[n], y[i]);
                maxX[n] = Math.max(maxX[n], x[i]);
                maxY[n] = Math.max(maxY[n], y[i]);
            }
            xs[n] = x;
            ys[n] = y;
            siteX[n] = cell.site().x();
            siteY[n] = height - cell.site().y();
        }
    }

    public static MapGeometry of(MapView view) {
        return new MapGeometry(view);
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    public int cells() {
        return xs.length;
    }

    /** Вершини комірки {@code n} за віссю {@code u}. Масив спільний — не змінювати. */
    public double[] xs(int n) {
        return xs[n];
    }

    /** Вершини комірки {@code n} за віссю {@code v}. Масив спільний — не змінювати. */
    public double[] ys(int n) {
        return ys[n];
    }

    public double siteX(int n) {
        return siteX[n];
    }

    public double siteY(int n) {
        return siteY[n];
    }

    public double minX(int n) {
        return minX[n];
    }

    public double minY(int n) {
        return minY[n];
    }

    public double maxX(int n) {
        return maxX[n];
    }

    public double maxY(int n) {
        return maxY[n];
    }

    /**
     * Чи точка всередині комірки. Правило напіввідкритих ребер — те саме, що в растеризації ({@link MapRaster}): точка
     * на спільному ребрі належить рівно одній з двох комірок.
     */
    public boolean contains(int n, double u, double v) {
        if (u < minX[n] || u > maxX[n] || v < minY[n] || v > maxY[n]) {
            return false;
        }
        double[] x = xs[n];
        double[] y = ys[n];
        boolean inside = false;
        for (int i = 0, j = x.length - 1; i < x.length; j = i++) {
            double crossing = crossing(x[j], y[j], x[i], y[i], v);
            if (!Double.isNaN(crossing) && u < crossing) {
                inside = !inside;
            }
        }
        return inside;
    }

    /**
     * Де ребро перетинає горизонталь {@code v}: {@code NaN}, якщо не перетинає. Нижня вершина ребра враховується,
     * верхня — ні. Кінці впорядковуються, тож спільне ребро двох комірок (обійдене в різні боки) дає той самий
     * результат до біта.
     */
    static double crossing(double x1, double y1, double x2, double y2, double v) {
        if (y1 > y2) {
            return crossing(x2, y2, x1, y1, v);
        }
        if (v < y1 || v >= y2) {
            return Double.NaN;
        }
        return x1 + (v - y1) * (x2 - x1) / (y2 - y1);
    }
}
