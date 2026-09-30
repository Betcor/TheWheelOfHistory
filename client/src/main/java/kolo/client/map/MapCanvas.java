package kolo.client.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import kolo.engine.view.CellView;
import kolo.engine.view.CountryView;

/**
 * Карта світу на {@link Canvas}.
 *
 * <p>Заливка режиму растеризується у фоновому потоці один раз на режим ({@link MapLayers#paint}) і лежить у
 * зображеннях-плитках кількох рівнів деталізації; кадр лише масштабує потрібні плитки — це дешево навіть на
 * інтегрованій графіці. На великому зумі, де растр довелося б розтягувати, видимі провінції малюються векторно.
 * Виділення й підписи — поверх, щокадру. Кадр перемальовується лише після змін (камера, режим, наведення).
 */
public final class MapCanvas extends Region {

    /** Плитки не більші за цей розмір: найбільша текстура, яку гарантовано приймає слабка графіка. */
    static final int TILE = 2048;

    private static final double DRAG_THRESHOLD = 3;
    private static final double WHEEL_ZOOM = 1.0015;
    private static final double KEY_ZOOM = 1.25;
    private static final double KEY_PAN = 80;
    private static final double LABEL_FONT_FACTOR = 0.16;
    private static final double MIN_LABEL_FONT = 11;
    private static final double MAX_LABEL_FONT = 30;

    private final Canvas canvas = new Canvas();
    private final MapLayers layers;
    private final Executor background;
    private final MapCamera camera;
    private final AnimationTimer timer;

    private MapMode mode = MapMode.POLITICAL;
    private Color[] fills = new Color[0];
    private List<List<Tile>> tiles = List.of();
    private int paintRequest;
    private OptionalInt hovered = OptionalInt.empty();
    private OptionalInt selected = OptionalInt.empty();
    private Consumer<OptionalInt> onHover = cell -> {};
    private Consumer<OptionalInt> onSelect = cell -> {};
    private boolean dirty = true;
    private int frames;
    private double pressX;
    private double pressY;
    private double lastX;
    private double lastY;
    private boolean dragging;

    /** Шматок растру рівня. */
    private record Tile(WritableImage image, int x, int y, int width, int height) {}

    /**
     * @param background потік для растеризації режимів — не потік JavaFX
     */
    public MapCanvas(MapLayers layers, Executor background) {
        this.layers = Objects.requireNonNull(layers, "layers");
        this.background = Objects.requireNonNull(background, "background");
        camera = new MapCamera(layers.geometry().width(), layers.geometry().height(), layers.maxScale());
        getChildren().add(canvas);
        setFocusTraversable(true);
        setMinSize(0, 0);
        canvas.addEventHandler(ScrollEvent.SCROLL, this::scrolled);
        canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, this::pressed);
        canvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::dragged);
        canvas.addEventHandler(MouseEvent.MOUSE_RELEASED, this::released);
        canvas.addEventHandler(MouseEvent.MOUSE_MOVED, event -> hover(cellAt(event.getX(), event.getY())));
        canvas.addEventHandler(MouseEvent.MOUSE_EXITED, event -> hover(OptionalInt.empty()));
        addEventHandler(KeyEvent.KEY_PRESSED, this::keyPressed);
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (dirty) {
                    dirty = false;
                    draw();
                }
            }
        };
        timer.start();
        setMode(MapMode.POLITICAL);
    }

    /** Змінює режим; до готовності нових плиток лишаються старі. */
    public void setMode(MapMode newMode) {
        mode = Objects.requireNonNull(newMode, "mode");
        Color[] newFills = new Color[layers.view().cells().size()];
        for (int n = 0; n < newFills.length; n++) {
            newFills[n] = color(MapPalette.color(layers.view().cells().get(n), newMode));
        }
        fills = newFills;
        int request = ++paintRequest;
        background.execute(() -> {
            List<int[]> painted = layers.paint(newMode);
            Platform.runLater(() -> {
                if (request == paintRequest) {
                    tiles = tiles(painted);
                    redraw();
                }
            });
        });
        redraw();
    }

    public MapMode mode() {
        return mode;
    }

    /** Що робити, коли курсор переходить на іншу комірку (порожньо — поза картою). */
    public void setOnHover(Consumer<OptionalInt> action) {
        onHover = Objects.requireNonNull(action, "action");
    }

    /** Що робити, коли гравець клацнув комірку (порожньо — поза картою). */
    public void setOnSelect(Consumer<OptionalInt> action) {
        onSelect = Objects.requireNonNull(action, "action");
    }

    /** Уся карта у вікні. */
    public void fit() {
        camera.fit();
        redraw();
    }

    /** Зупиняє перемальовування — коли екран карти закрито. */
    public void dispose() {
        timer.stop();
    }

    /** Скільки кадрів намальовано — для смок-тесту. */
    int frames() {
        return frames;
    }

    /** Чи готові плитки растру поточного режиму — для смок-тесту. */
    boolean rasterReady() {
        return !tiles.isEmpty();
    }

    MapCamera camera() {
        return camera;
    }

    @Override
    protected void layoutChildren() {
        double width = getWidth();
        double height = getHeight();
        if (canvas.getWidth() != width || canvas.getHeight() != height) {
            canvas.setWidth(width);
            canvas.setHeight(height);
            camera.resize(width, height);
            redraw();
        }
    }

    private void redraw() {
        dirty = true;
    }

    private void draw() {
        frames++;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(color(MapPalette.SEA));
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        if (tiles.isEmpty() || camera.scale() >= layers.vectorScale()) {
            drawVector(gc);
        } else {
            drawRaster(gc);
        }
        hovered.ifPresent(cell -> outline(gc, cell, color(MapPalette.HOVER), 2));
        selected.ifPresent(cell -> outline(gc, cell, color(MapPalette.SELECTION), 3));
        if (mode == MapMode.POLITICAL) {
            drawLabels(gc);
        }
    }

    private void drawRaster(GraphicsContext gc) {
        int index = RasterLevel.choose(layers.levels(), camera.scale());
        RasterLevel level = layers.levels().get(index);
        gc.setImageSmoothing(true);
        for (Tile tile : tiles.get(index)) {
            double x1 = camera.toScreenX(tile.x() / level.scale());
            double y1 = camera.toScreenY(tile.y() / level.scale());
            double x2 = camera.toScreenX((tile.x() + tile.width()) / level.scale());
            double y2 = camera.toScreenY((tile.y() + tile.height()) / level.scale());
            if (x2 < 0 || y2 < 0 || x1 > canvas.getWidth() || y1 > canvas.getHeight()) {
                continue;
            }
            gc.drawImage(tile.image(), x1, y1, x2 - x1, y2 - y1);
        }
    }

    private void drawVector(GraphicsContext gc) {
        MapGeometry geometry = layers.geometry();
        for (int n = 0; n < geometry.cells(); n++) {
            if (!visible(geometry.minX(n), geometry.minY(n), geometry.maxX(n), geometry.maxY(n))) {
                continue;
            }
            gc.setFill(fills[n]);
            gc.fillPolygon(screenXs(n), screenYs(n), geometry.xs(n).length);
        }
        boolean political = mode == MapMode.POLITICAL;
        for (MapEdges.Edge edge : layers.edges().edges()) {
            double minX = Math.min(edge.x1(), edge.x2());
            double minY = Math.min(edge.y1(), edge.y2());
            if (!visible(minX, minY, Math.max(edge.x1(), edge.x2()), Math.max(edge.y1(), edge.y2()))) {
                continue;
            }
            switch (edge.kind()) {
                case PROVINCE -> {
                    gc.setStroke(Color.rgb(0, 0, 0, 0.14));
                    gc.setLineWidth(1);
                }
                case COAST -> {
                    gc.setStroke(Color.rgb(0, 0, 0, 0.35));
                    gc.setLineWidth(1.5);
                }
                case COUNTRY -> {
                    gc.setStroke(political ? color(MapPalette.COUNTRY_BORDER) : Color.rgb(0, 0, 0, 0.5));
                    gc.setLineWidth(political ? 2.5 : 2);
                }
            }
            gc.strokeLine(
                    camera.toScreenX(edge.x1()),
                    camera.toScreenY(edge.y1()),
                    camera.toScreenX(edge.x2()),
                    camera.toScreenY(edge.y2()));
        }
        drawRivers(gc);
    }

    private void drawRivers(GraphicsContext gc) {
        MapGeometry geometry = layers.geometry();
        gc.setStroke(color(MapPalette.RIVER));
        gc.setLineWidth(Math.clamp(camera.scale() * layers.view().cellSide() / 40, 1.5, 4));
        for (int n = 0; n < geometry.cells(); n++) {
            CellView cell = layers.view().cells().get(n);
            if (!cell.river() || !visible(geometry.minX(n), geometry.minY(n), geometry.maxX(n), geometry.maxY(n))) {
                continue;
            }
            int next = cell.downstream().getAsInt();
            double x2 = geometry.siteX(next);
            double y2 = geometry.siteY(next);
            if (!layers.view().cells().get(next).isLand()) {
                x2 = (geometry.siteX(n) + x2) / 2;
                y2 = (geometry.siteY(n) + y2) / 2;
            }
            gc.strokeLine(
                    camera.toScreenX(geometry.siteX(n)),
                    camera.toScreenY(geometry.siteY(n)),
                    camera.toScreenX(x2),
                    camera.toScreenY(y2));
        }
    }

    private void drawLabels(GraphicsContext gc) {
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.setLineWidth(3);
        gc.setStroke(Color.rgb(255, 255, 255, 0.8));
        gc.setFill(Color.rgb(30, 30, 30));
        for (CountryLabels.Label label : layers.labels()) {
            double size = label.size() * camera.scale() * LABEL_FONT_FACTOR;
            if (size < MIN_LABEL_FONT || !visible(label.x(), label.y(), label.x(), label.y())) {
                continue;
            }
            CountryView country = layers.view().countries().get(label.country());
            gc.setFont(Font.font(null, FontWeight.BOLD, Math.min(size, MAX_LABEL_FONT)));
            String text = country.name().shortName().nominative();
            double x = camera.toScreenX(label.x());
            double y = camera.toScreenY(label.y());
            gc.strokeText(text, x, y);
            gc.fillText(text, x, y);
        }
    }

    private void outline(GraphicsContext gc, int cell, Color color, double width) {
        gc.setStroke(color);
        gc.setLineWidth(width);
        gc.strokePolygon(screenXs(cell), screenYs(cell), layers.geometry().xs(cell).length);
    }

    private double[] screenXs(int cell) {
        double[] xs = layers.geometry().xs(cell);
        double[] result = new double[xs.length];
        for (int i = 0; i < xs.length; i++) {
            result[i] = camera.toScreenX(xs[i]);
        }
        return result;
    }

    private double[] screenYs(int cell) {
        double[] ys = layers.geometry().ys(cell);
        double[] result = new double[ys.length];
        for (int i = 0; i < ys.length; i++) {
            result[i] = camera.toScreenY(ys[i]);
        }
        return result;
    }

    private boolean visible(double minX, double minY, double maxX, double maxY) {
        return maxX >= camera.left() && minX <= camera.right() && maxY >= camera.top() && minY <= camera.bottom();
    }

    private OptionalInt cellAt(double x, double y) {
        return layers.hitTest().cellAt(camera.toMapX(x), camera.toMapY(y));
    }

    private void hover(OptionalInt cell) {
        if (!cell.equals(hovered)) {
            hovered = cell;
            onHover.accept(cell);
            redraw();
        }
    }

    private void scrolled(ScrollEvent event) {
        camera.zoom(Math.pow(WHEEL_ZOOM, event.getDeltaY()), event.getX(), event.getY());
        hover(cellAt(event.getX(), event.getY()));
        redraw();
        event.consume();
    }

    private void pressed(MouseEvent event) {
        requestFocus();
        pressX = event.getX();
        pressY = event.getY();
        lastX = pressX;
        lastY = pressY;
        dragging = false;
    }

    private void dragged(MouseEvent event) {
        if (!dragging && Math.hypot(event.getX() - pressX, event.getY() - pressY) < DRAG_THRESHOLD) {
            return;
        }
        dragging = true;
        camera.pan(event.getX() - lastX, event.getY() - lastY);
        lastX = event.getX();
        lastY = event.getY();
        redraw();
    }

    private void released(MouseEvent event) {
        if (!dragging && event.getButton() == MouseButton.PRIMARY) {
            selected = cellAt(event.getX(), event.getY());
            onSelect.accept(selected);
            redraw();
        }
    }

    private void keyPressed(KeyEvent event) {
        double centerX = canvas.getWidth() / 2;
        double centerY = canvas.getHeight() / 2;
        switch (event.getCode()) {
            case PLUS, EQUALS, ADD -> camera.zoom(KEY_ZOOM, centerX, centerY);
            case MINUS, SUBTRACT -> camera.zoom(1 / KEY_ZOOM, centerX, centerY);
            case LEFT -> camera.pan(KEY_PAN, 0);
            case RIGHT -> camera.pan(-KEY_PAN, 0);
            case UP -> camera.pan(0, KEY_PAN);
            case DOWN -> camera.pan(0, -KEY_PAN);
            case HOME -> camera.fit();
            default -> {
                return;
            }
        }
        redraw();
        event.consume();
    }

    private List<List<Tile>> tiles(List<int[]> painted) {
        List<List<Tile>> result = new ArrayList<>();
        for (int i = 0; i < painted.size(); i++) {
            RasterLevel level = layers.levels().get(i);
            int[] argb = painted.get(i);
            List<Tile> levelTiles = new ArrayList<>();
            for (int y = 0; y < level.height(); y += TILE) {
                for (int x = 0; x < level.width(); x += TILE) {
                    int width = Math.min(TILE, level.width() - x);
                    int height = Math.min(TILE, level.height() - y);
                    WritableImage image = new WritableImage(width, height);
                    image.getPixelWriter()
                            .setPixels(
                                    0,
                                    0,
                                    width,
                                    height,
                                    PixelFormat.getIntArgbPreInstance(),
                                    argb,
                                    y * level.width() + x,
                                    level.width());
                    levelTiles.add(new Tile(image, x, y, width, height));
                }
            }
            result.add(List.copyOf(levelTiles));
        }
        return List.copyOf(result);
    }

    private static Color color(int argb) {
        return Color.rgb(MapPalette.red(argb), MapPalette.green(argb), MapPalette.blue(argb));
    }
}
