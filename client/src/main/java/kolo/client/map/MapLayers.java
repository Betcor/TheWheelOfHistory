package kolo.client.map;

import java.util.ArrayList;
import java.util.List;
import kolo.engine.view.MapView;

/**
 * Усе, що клієнт готує з карти один раз після її отримання: геометрія, хіт-тест, ребра, підписи й растри номерів
 * комірок для кожного рівня деталізації. Будується у фоновому потоці; кольори режиму — {@link #paint}.
 */
public final class MapLayers {

    /** Довша сторона растру на рівнях деталізації, пікселів. */
    static final int[] LONG_SIDES = {2048, 4096};

    /** Найбільший зум: середня провінція займає стільки пікселів екрана. */
    static final double MAX_CELL_PIXELS = 160;

    /** Ширше за стільки растр не збільшується: далі карта малюється векторно. */
    static final double MAX_MAGNIFICATION = 2;

    /** Від такої сторони провінції на растрі межі провінцій уже не шум. */
    static final double PROVINCE_BORDER_PIXELS = 10;

    /** Від такої сторони провінції кордони держав і річки — у 2 пікселі. */
    static final double THICK_LINES_PIXELS = 20;

    private final MapView view;
    private final MapGeometry geometry;
    private final MapHitTest hitTest;
    private final MapEdges edges;
    private final List<CountryLabels.Label> labels;
    private final List<RasterLevel> levels;
    private final List<int[]> ids;
    private final double maxScale;

    private MapLayers(MapView view) {
        this.view = view;
        geometry = MapGeometry.of(view);
        hitTest = new MapHitTest(geometry, view.cellSide());
        edges = MapEdges.of(view);
        labels = CountryLabels.of(view, geometry);
        maxScale = MAX_CELL_PIXELS / view.cellSide();
        levels = RasterLevel.of(view.width(), view.height(), maxScale, LONG_SIDES);
        List<int[]> cellIds = new ArrayList<>();
        for (RasterLevel level : levels) {
            cellIds.add(MapRaster.cellIds(geometry, level));
        }
        ids = List.copyOf(cellIds);
    }

    public static MapLayers build(MapView view) {
        return new MapLayers(view);
    }

    /** Кольори режиму для кожного рівня з {@link #levels()}, ARGB рядками. */
    public List<int[]> paint(MapMode mode) {
        List<int[]> result = new ArrayList<>();
        for (int i = 0; i < levels.size(); i++) {
            RasterLevel level = levels.get(i);
            double cellPixels = view.cellSide() * level.scale();
            result.add(MapRaster.paint(
                    view,
                    geometry,
                    level,
                    ids.get(i),
                    mode,
                    cellPixels >= PROVINCE_BORDER_PIXELS,
                    cellPixels >= THICK_LINES_PIXELS ? 2 : 1));
        }
        return List.copyOf(result);
    }

    public MapView view() {
        return view;
    }

    public MapGeometry geometry() {
        return geometry;
    }

    public MapHitTest hitTest() {
        return hitTest;
    }

    public MapEdges edges() {
        return edges;
    }

    public List<CountryLabels.Label> labels() {
        return labels;
    }

    public List<RasterLevel> levels() {
        return levels;
    }

    /** Номери комірок растру рівня {@code level}. Масив спільний — не змінювати. */
    int[] cellIds(int level) {
        return ids.get(level);
    }

    /** Найбільший масштаб камери. */
    public double maxScale() {
        return maxScale;
    }

    /** З якого масштабу камери карта малюється векторно, а не растром. */
    public double vectorScale() {
        return levels.getLast().scale() * MAX_MAGNIFICATION;
    }
}
