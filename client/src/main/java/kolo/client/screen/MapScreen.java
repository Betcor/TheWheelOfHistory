package kolo.client.screen;

import java.util.OptionalInt;
import java.util.concurrent.Executor;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import kolo.client.app.Navigator;
import kolo.client.i18n.Texts;
import kolo.client.map.MapCanvas;
import kolo.client.map.MapLayers;
import kolo.client.map.MapMode;
import kolo.engine.view.MapView;

/** Карта світу (GD §22.2): режими карти, панель обраної провінції й рядок стану з провінцією під курсором. */
public final class MapScreen {

    private static final double PANEL_WIDTH = 280;

    private MapScreen() {}

    public static Parent create(Navigator navigator, Texts texts, MapLayers layers, Executor background) {
        MapView view = layers.view();
        MapCanvas canvas = new MapCanvas(layers, background);

        ToggleGroup modes = new ToggleGroup();
        ToolBar toolbar = new ToolBar();
        for (MapMode mode : MapMode.values()) {
            ToggleButton button = new ToggleButton(texts.text("map.mode." + mode.key()));
            button.setToggleGroup(modes);
            button.setSelected(mode == canvas.mode());
            button.setOnAction(event -> {
                // Режим не можна «відтиснути»: якийсь завжди обраний.
                button.setSelected(true);
                canvas.setMode(mode);
            });
            toolbar.getItems().add(button);
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button fit = new Button(texts.text("map.fit"));
        fit.setOnAction(event -> canvas.fit());
        Button menu = new Button(texts.text("map.main_menu"));
        menu.setOnAction(event -> {
            canvas.dispose();
            navigator.showMainMenu();
        });
        toolbar.getItems()
                .addAll(
                        new Separator(),
                        new Label(texts.text(
                                "map.world", view.seed(), view.countries().size())),
                        spacer,
                        fit,
                        menu);

        Label title = new Label(texts.text("map.info.none"));
        title.getStyleClass().add("title-4");
        title.setWrapText(true);
        VBox details = new VBox(6);
        VBox panel = new VBox(12, title, details);
        panel.setPadding(new Insets(12));
        panel.setPrefWidth(PANEL_WIDTH);
        panel.setMinWidth(PANEL_WIDTH);

        Label status = new Label(texts.text("map.status.hint"));
        status.setPadding(new Insets(4, 8, 4, 8));

        canvas.setOnHover(cell -> status.setText(
                cell.isPresent()
                        ? ProvinceDescription.summary(view, cell.getAsInt(), texts)
                        : texts.text("map.status.hint")));
        canvas.setOnSelect(cell -> show(cell, view, texts, title, details));

        BorderPane root = new BorderPane(canvas);
        root.setTop(toolbar);
        root.setRight(panel);
        root.setBottom(status);
        canvas.requestFocus();
        return root;
    }

    private static void show(OptionalInt cell, MapView view, Texts texts, Label title, VBox details) {
        details.getChildren().clear();
        if (cell.isEmpty()) {
            title.setText(texts.text("map.info.none"));
            return;
        }
        title.setText(ProvinceDescription.title(view, cell.getAsInt(), texts));
        for (String line : ProvinceDescription.lines(view, cell.getAsInt(), texts)) {
            Label label = new Label(line);
            label.setWrapText(true);
            details.getChildren().add(label);
        }
    }
}
