package kolo.client.map;

/**
 * Камера карти: масштаб (пікселів екрана на одиницю карти) і зсув. Масштаб обмежено знизу — уся карта у вікні, згори
 * — {@code maxScale}. Карта, менша за вікно, стоїть по центру; більша — не від'їжджає від краю вікна.
 */
public final class MapCamera {

    private final double mapWidth;
    private final double mapHeight;
    private final double maxScale;
    private double viewWidth = 1;
    private double viewHeight = 1;
    private double scale;
    private double left;
    private double top;

    /**
     * @param mapWidth ширина карти в одиницях карти
     * @param mapHeight висота карти
     * @param maxScale найбільший масштаб, пікселів на одиницю
     */
    public MapCamera(double mapWidth, double mapHeight, double maxScale) {
        if (!(mapWidth > 0 && mapHeight > 0 && maxScale > 0)) {
            throw new IllegalArgumentException("map size and max scale must be positive");
        }
        this.mapWidth = mapWidth;
        this.mapHeight = mapHeight;
        this.maxScale = maxScale;
        fit();
    }

    /** Новий розмір вікна; точка карти в центрі вікна лишається в центрі. */
    public void resize(double width, double height) {
        double centerU = toMapX(viewWidth / 2);
        double centerV = toMapY(viewHeight / 2);
        viewWidth = Math.max(1, width);
        viewHeight = Math.max(1, height);
        scale = Math.clamp(scale, minScale(), maxScale());
        left = centerU - viewWidth / 2 / scale;
        top = centerV - viewHeight / 2 / scale;
        clamp();
    }

    /** Уся карта у вікні. */
    public void fit() {
        scale = minScale();
        left = 0;
        top = 0;
        clamp();
    }

    /** Масштабує в {@code factor} разів так, що точка екрана {@code (x, y)} лишається над тією самою точкою карти. */
    public void zoom(double factor, double x, double y) {
        double u = toMapX(x);
        double v = toMapY(y);
        scale = Math.clamp(scale * factor, minScale(), maxScale());
        left = u - x / scale;
        top = v - y / scale;
        clamp();
    }

    /** Зсуває карту за курсором на {@code (dx, dy)} пікселів екрана. */
    public void pan(double dx, double dy) {
        left -= dx / scale;
        top -= dy / scale;
        clamp();
    }

    public double scale() {
        return scale;
    }

    /** Найменший масштаб: уся карта у вікні. */
    public double minScale() {
        return Math.min(Math.min(viewWidth / mapWidth, viewHeight / mapHeight), maxScale);
    }

    public double maxScale() {
        return maxScale;
    }

    public double toScreenX(double u) {
        return (u - left) * scale;
    }

    public double toScreenY(double v) {
        return (v - top) * scale;
    }

    public double toMapX(double x) {
        return left + x / scale;
    }

    public double toMapY(double y) {
        return top + y / scale;
    }

    /** Ліва межа видимої частини карти в одиницях карти. */
    public double left() {
        return left;
    }

    /** Верхня межа видимої частини карти. */
    public double top() {
        return top;
    }

    public double right() {
        return left + viewWidth / scale;
    }

    public double bottom() {
        return top + viewHeight / scale;
    }

    private void clamp() {
        left = clampAxis(left, viewWidth / scale, mapWidth);
        top = clampAxis(top, viewHeight / scale, mapHeight);
    }

    private static double clampAxis(double start, double visible, double size) {
        if (visible >= size) {
            return (size - visible) / 2;
        }
        return Math.clamp(start, 0, size - visible);
    }
}
