package kolo.client.generation;

import java.util.List;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;

/**
 * Колесо фортуни одного запису: сектори за вагою, кольором — за якістю для держави (червоний — погано, зелений —
 * добре), стрілка вгорі. Крутиться до вже відомого результату ({@link WheelGeometry#finalRotation}) і показує його.
 */
public final class WheelView extends VBox {

    /** Найменша дуга, на якій ще пишемо назву сектора, градусів. */
    private static final double LABELED_EXTENT = 14;

    private static final int LABEL_CHARS = 16;

    private final RollRecord roll;
    private final GenerationLabels labels;
    private final Canvas canvas;
    private final Label result = new Label("?");
    private final DoubleProperty rotation = new SimpleDoubleProperty(0);
    private final List<WheelGeometry.Slice> slices;
    private Timeline spin;
    private boolean done;

    public WheelView(RollRecord roll, GenerationLabels labels, double size) {
        this.roll = roll;
        this.labels = labels;
        this.slices = WheelGeometry.slices(roll);
        this.canvas = new Canvas(size, size);
        Label title = new Label(labels.wheel(roll.kind()));
        title.getStyleClass().add("title-4");
        result.getStyleClass().add("text-bold");
        result.setWrapText(true);
        result.setMaxWidth(size);
        result.setPrefWidth(size);
        result.setAlignment(Pos.CENTER);
        result.setTextAlignment(TextAlignment.CENTER);
        setSpacing(6);
        setAlignment(Pos.TOP_CENTER);
        getChildren().addAll(title, canvas, result);
        rotation.addListener((property, old, angle) -> draw());
        draw();
    }

    public RollRecord roll() {
        return roll;
    }

    /** Чи колесо вже показує результат. */
    public boolean done() {
        return done;
    }

    /** Чи колесо зараз крутиться. */
    public boolean spinning() {
        return spin != null;
    }

    /** Крутить колесо до результату; {@code finished} — у потоці JavaFX, коли колесо зупинилося. */
    public void spin(Duration duration, Runnable finished) {
        stop();
        rotation.set(0);
        Interpolator easeOut = new Interpolator() {
            @Override
            protected double curve(double t) {
                return WheelGeometry.easeOut(t);
            }
        };
        spin = new Timeline(new KeyFrame(duration, new KeyValue(rotation, WheelGeometry.finalRotation(roll), easeOut)));
        spin.setOnFinished(event -> {
            spin = null;
            reveal();
            finished.run();
        });
        spin.play();
    }

    /** Одразу результат — без анімації чи обриваючи її. */
    public void showResult() {
        stop();
        rotation.set(WheelGeometry.finalRotation(roll));
        reveal();
    }

    /** Зупиняє анімацію (екран закрито). */
    public void stop() {
        if (spin != null && spin.getStatus() == Animation.Status.RUNNING) {
            spin.stop();
        }
        spin = null;
    }

    private void reveal() {
        done = true;
        result.setText(labels.result(roll));
        draw();
    }

    private void draw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double size = canvas.getWidth();
        double margin = 14;
        double radius = size / 2 - margin;
        double cx = size / 2;
        double cy = size / 2 + 4;
        g.clearRect(0, 0, size, size);
        double angle = rotation.get();
        for (int i = 0; i < slices.size(); i++) {
            WheelGeometry.Slice slice = slices.get(i);
            boolean chosen = done && slice.sector().id().equals(roll.resultSectorId());
            g.setFill(color(slice.sector(), i, chosen));
            // Дуга JavaFX — від «3 години» проти годинникової; наші кути — від верху за годинниковою.
            double from = slice.start() + angle;
            g.fillArc(
                    cx - radius,
                    cy - radius,
                    radius * 2,
                    radius * 2,
                    90 - from - slice.extent(),
                    slice.extent(),
                    ArcType.ROUND);
            g.setStroke(Color.WHITE);
            g.setLineWidth(1);
            g.strokeArc(
                    cx - radius,
                    cy - radius,
                    radius * 2,
                    radius * 2,
                    90 - from - slice.extent(),
                    slice.extent(),
                    ArcType.ROUND);
            if (slice.extent() >= LABELED_EXTENT) {
                label(g, slice, cx, cy, radius, from + slice.extent() / 2);
            }
        }
        g.setStroke(Color.web("#3d4450"));
        g.setLineWidth(2);
        g.strokeOval(cx - radius, cy - radius, radius * 2, radius * 2);
        // Стрілка вгорі.
        g.setFill(Color.web("#24292f"));
        g.fillPolygon(
                new double[] {cx - 9, cx + 9, cx},
                new double[] {cy - radius - 12, cy - radius - 12, cy - radius + 8},
                3);
    }

    private void label(GraphicsContext g, WheelGeometry.Slice slice, double cx, double cy, double radius, double mid) {
        String text = labels.sector(roll, slice.sector().id());
        if (text.length() > LABEL_CHARS) {
            text = text.substring(0, LABEL_CHARS - 1) + "…";
        }
        g.save();
        g.translate(cx, cy);
        g.setFill(Color.web("#1f2328"));
        g.setFont(Font.font(Math.max(9, radius / 11)));
        g.setTextBaseline(VPos.CENTER);
        // Текст уздовж радіуса біля краю; на лівій половині — розвернутий, щоб не читати догори дриґом.
        double at = ((mid % 360) + 360) % 360;
        if (at <= 180) {
            g.rotate(at - 90);
            g.setTextAlign(TextAlignment.RIGHT);
            g.fillText(text, radius - 6, 0);
        } else {
            g.rotate(at + 90);
            g.setTextAlign(TextAlignment.LEFT);
            g.fillText(text, -(radius - 6), 0);
        }
        g.restore();
    }

    private Color color(RolledSector sector, int index, boolean chosen) {
        // Якість 0..100 → відтінок від червоного (0°) до зеленого (120°); сусідні — трохи різної яскравості.
        double hue = sector.quality() * 1.2;
        double brightness = index % 2 == 0 ? 0.95 : 0.86;
        double saturation = chosen ? 0.75 : done ? 0.18 : 0.42;
        return Color.hsb(hue, saturation, brightness);
    }
}
