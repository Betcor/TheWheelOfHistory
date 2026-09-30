package kolo.client.map;

import java.util.Arrays;
import kolo.engine.view.CellView;
import kolo.engine.view.MapView;

/**
 * Растеризація карти в пікселі без JavaFX — тож працює у фоновому потоці й тестується без дисплея.
 *
 * <p>Спершу — растр номерів комірок ({@link #cellIds}): піксель належить комірці, що містить його центр (правило
 * напіввідкритих ребер, як у {@link MapGeometry#contains}); комірки вкривають карту без щілин, тож кожен піксель
 * отримує рівно одну комірку. З нього для кожного режиму — кольори ({@link #paint}) з кордонами там, де сусідні
 * пікселі належать різним коміркам, і річками поверх.
 */
public final class MapRaster {

    /** Піксель без комірки — лише до заповнення щілин. */
    static final int NONE = -1;

    private static final double PROVINCE_SHADE = 0.86;
    private static final double COAST_SHADE = 0.68;
    private static final double BORDER_SHADE = 0.5;

    private MapRaster() {}

    /** Номер комірки для кожного пікселя растру {@code level}, рядками зверху вниз. */
    public static int[] cellIds(MapGeometry geometry, RasterLevel level) {
        int width = level.width();
        int height = level.height();
        double scale = level.scale();
        int[] ids = new int[width * height];
        Arrays.fill(ids, NONE);
        double[] crossings = new double[16];
        for (int n = 0; n < geometry.cells(); n++) {
            double[] xs = geometry.xs(n);
            double[] ys = geometry.ys(n);
            if (crossings.length < xs.length) {
                crossings = new double[xs.length];
            }
            int firstRow = Math.max(0, (int) Math.ceil(geometry.minY(n) * scale - 0.5));
            int lastRow = Math.min(height - 1, (int) Math.ceil(geometry.maxY(n) * scale - 0.5) - 1);
            for (int row = firstRow; row <= lastRow; row++) {
                double v = (row + 0.5) / scale;
                int count = 0;
                for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
                    double crossing = MapGeometry.crossing(xs[j], ys[j], xs[i], ys[i], v);
                    if (!Double.isNaN(crossing)) {
                        crossings[count++] = crossing;
                    }
                }
                Arrays.sort(crossings, 0, count);
                for (int k = 0; k + 1 < count; k += 2) {
                    int from = Math.max(0, (int) Math.ceil(crossings[k] * scale - 0.5));
                    int to = Math.min(width, (int) Math.ceil(crossings[k + 1] * scale - 0.5));
                    Arrays.fill(ids, row * width + from, row * width + Math.max(from, to), n);
                }
            }
        }
        fillGaps(ids, width);
        return ids;
    }

    /**
     * Кольори растру в режимі {@code mode}, ARGB рядками зверху вниз.
     *
     * @param ids номери комірок з {@link #cellIds} для того самого рівня
     * @param provinceBorders чи малювати межі провінцій усередині держави — на грубому рівні вони зливаються в шум
     * @param thickness товщина кордонів держав і річок у пікселях, 1 або 2
     */
    public static int[] paint(
            MapView view,
            MapGeometry geometry,
            RasterLevel level,
            int[] ids,
            MapMode mode,
            boolean provinceBorders,
            int thickness) {
        int width = level.width();
        int height = level.height();
        int cells = view.cells().size();
        int[] colors = new int[cells];
        int[] owners = new int[cells];
        boolean[] land = new boolean[cells];
        for (int n = 0; n < cells; n++) {
            CellView cell = view.cells().get(n);
            colors[n] = MapPalette.color(cell, mode);
            owners[n] = cell.country().orElse(NONE);
            land[n] = cell.isLand();
        }
        int[] argb = new int[width * height];
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                int p = row * width + column;
                int a = ids[p];
                int color = colors[a];
                if (land[a]) {
                    Border border = Border.NONE;
                    // Праворуч і вниз — тонка лінія з одного боку; з товщиною 2 — ще ліворуч і вгору, тобто з обох.
                    if (column + 1 < width) {
                        border = border.max(border(a, ids[p + 1], land, owners));
                    }
                    if (row + 1 < height) {
                        border = border.max(border(a, ids[p + width], land, owners));
                    }
                    if (thickness > 1 && column > 0) {
                        border = border.max(border(a, ids[p - 1], land, owners));
                    }
                    if (thickness > 1 && row > 0) {
                        border = border.max(border(a, ids[p - width], land, owners));
                    }
                    color = switch (border) {
                        case NONE -> color;
                        case PROVINCE -> provinceBorders ? MapPalette.darker(color, PROVINCE_SHADE) : color;
                        case COAST -> MapPalette.darker(color, COAST_SHADE);
                        case COUNTRY ->
                            mode == MapMode.POLITICAL
                                    ? MapPalette.COUNTRY_BORDER
                                    : MapPalette.darker(color, BORDER_SHADE);
                    };
                }
                argb[p] = color;
            }
        }
        drawRivers(view, geometry, level, argb, thickness);
        return argb;
    }

    /** Межа між пікселями комірок {@code a} (суходіл) і {@code b}, за зростанням важливості. */
    private enum Border {
        NONE,
        PROVINCE,
        COAST,
        COUNTRY;

        Border max(Border other) {
            return other.ordinal() > ordinal() ? other : this;
        }
    }

    private static Border border(int a, int b, boolean[] land, int[] owners) {
        if (a == b) {
            return Border.NONE;
        }
        if (!land[b]) {
            return Border.COAST;
        }
        return owners[a] == owners[b] ? Border.PROVINCE : Border.COUNTRY;
    }

    private static void drawRivers(MapView view, MapGeometry geometry, RasterLevel level, int[] argb, int thickness) {
        double scale = level.scale();
        for (int n = 0; n < view.cells().size(); n++) {
            CellView cell = view.cells().get(n);
            if (!cell.river()) {
                continue;
            }
            int next = cell.downstream().getAsInt();
            double x2 = geometry.siteX(next);
            double y2 = geometry.siteY(next);
            if (!view.cells().get(next).isLand()) {
                // У воду річка впадає на березі, а не в центрі морської комірки.
                x2 = (geometry.siteX(n) + x2) / 2;
                y2 = (geometry.siteY(n) + y2) / 2;
            }
            line(
                    argb,
                    level.width(),
                    level.height(),
                    geometry.siteX(n) * scale,
                    geometry.siteY(n) * scale,
                    x2 * scale,
                    y2 * scale,
                    thickness,
                    MapPalette.RIVER);
        }
    }

    /** Відрізок квадратним пензлем {@code thickness × thickness}. */
    static void line(
            int[] argb, int width, int height, double x1, double y1, double x2, double y2, int thickness, int color) {
        int steps = (int) Math.ceil(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)));
        int offset = (thickness - 1) / 2;
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double) i / steps;
            int cx = (int) Math.floor(x1 + (x2 - x1) * t) - offset;
            int cy = (int) Math.floor(y1 + (y2 - y1) * t) - offset;
            for (int dy = 0; dy < thickness; dy++) {
                for (int dx = 0; dx < thickness; dx++) {
                    int x = cx + dx;
                    int y = cy + dy;
                    if (x >= 0 && x < width && y >= 0 && y < height) {
                        argb[y * width + x] = color;
                    }
                }
            }
        }
    }

    /** Пікселі без комірки (щілини округлення на краю карти) беруть комірку сусіда зліва або згори. */
    private static void fillGaps(int[] ids, int width) {
        for (int p = 0; p < ids.length; p++) {
            if (ids[p] != NONE) {
                continue;
            }
            if (p % width > 0 && ids[p - 1] != NONE) {
                ids[p] = ids[p - 1];
            } else if (p >= width && ids[p - width] != NONE) {
                ids[p] = ids[p - width];
            }
        }
        for (int p = ids.length - 1; p >= 0; p--) {
            if (ids[p] == NONE) {
                if (p % width + 1 < width && ids[p + 1] != NONE) {
                    ids[p] = ids[p + 1];
                } else if (p + width < ids.length && ids[p + width] != NONE) {
                    ids[p] = ids[p + width];
                } else {
                    ids[p] = 0;
                }
            }
        }
    }
}
