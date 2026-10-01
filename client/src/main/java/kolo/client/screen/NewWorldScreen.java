package kolo.client.screen;

import java.security.SecureRandom;
import java.util.OptionalLong;
import java.util.concurrent.Executor;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import kolo.client.app.Navigator;
import kolo.client.i18n.Texts;
import kolo.client.map.MapLayers;
import kolo.client.net.ConnectionClosedException;
import kolo.client.net.ServerErrorException;
import kolo.client.net.WorldSource;
import kolo.engine.error.GameException;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldLimits;
import kolo.engine.view.MapView;

/**
 * Параметри нового світу (GD §3.4): seed, кількість гравців і частка NPC. Світ генерує сервер, клієнт отримує карту
 * повідомленнями; запит і підготовка шарів карти — у фоновому потоці, вікно тим часом не зависає.
 */
public final class NewWorldScreen {

    private NewWorldScreen() {}

    public static Parent create(Navigator navigator, Texts texts, WorldSource worlds, Executor background) {
        TextField seed = new TextField();
        seed.setPromptText(texts.text("new_world.seed_random"));
        Spinner<Integer> players = new Spinner<>(WorldLimits.MIN_PLAYERS, WorldLimits.MAX_PLAYERS, 1);
        players.setEditable(true);
        ComboBox<NpcShare> npc = new ComboBox<>();
        npc.getItems().setAll(NpcShare.values());
        npc.getSelectionModel().select(NpcShare.NORMAL);
        npc.setCellFactory(list -> new ShareCell(texts));
        npc.setButtonCell(new ShareCell(texts));

        GridPane form = new GridPane(12, 10);
        form.addRow(0, new Label(texts.text("new_world.seed")), seed);
        form.addRow(1, new Label(texts.text("new_world.players")), players);
        form.addRow(2, new Label(texts.text("new_world.npc")), npc);

        Label error = new Label();
        error.getStyleClass().add("danger");
        error.setWrapText(true);
        ProgressIndicator progress = new ProgressIndicator();
        progress.setVisible(false);
        progress.setPrefSize(28, 28);

        Button generate = new Button(texts.text("new_world.generate"));
        generate.setDefaultButton(true);
        Button back = new Button(texts.text("new_world.back"));
        back.setCancelButton(true);
        back.setOnAction(event -> navigator.showMainMenu());

        generate.setOnAction(event -> {
            OptionalLong parsed;
            try {
                parsed = SeedInput.parse(seed.getText());
            } catch (NumberFormatException e) {
                error.setText(texts.text("new_world.seed_invalid"));
                return;
            }
            long chosen = parsed.orElseGet(() -> new SecureRandom().nextLong());
            int playerCount = players.getValue();
            NpcShare share = npc.getValue();
            error.setText("");
            generate.setDisable(true);
            back.setDisable(true);
            progress.setVisible(true);
            background.execute(() -> {
                try {
                    MapView view = worlds.newWorld(chosen, playerCount, share);
                    MapLayers layers = MapLayers.build(view);
                    Platform.runLater(() -> navigator.showMap(layers));
                } catch (ServerErrorException e) {
                    String message = texts.error(e.error().code(), e.error().details());
                    Platform.runLater(() -> failed(message, error, generate, back, progress));
                } catch (GameException e) {
                    String message = texts.error(e.code(), e.details());
                    Platform.runLater(() -> failed(message, error, generate, back, progress));
                } catch (ConnectionClosedException e) {
                    Platform.runLater(
                            () -> failed(texts.text("app.error.connection_lost"), error, generate, back, progress));
                } catch (RuntimeException e) {
                    // Межа фонового потоку: інакше виняток зник би мовчки, а кнопка лишилася б вимкненою.
                    Platform.runLater(
                            () -> failed(texts.text("app.error.unexpected"), error, generate, back, progress));
                    throw e;
                }
            });
        });

        HBox buttons = new HBox(10, back, generate, progress);
        buttons.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(texts.text("new_world.title"));
        title.getStyleClass().add("title-2");
        VBox box = new VBox(18, title, form, buttons, error);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(24));
        box.setMaxWidth(520);
        VBox outer = new VBox(box);
        outer.setAlignment(Pos.CENTER);
        return outer;
    }

    private static void failed(String message, Label error, Button generate, Button back, ProgressIndicator progress) {
        error.setText(message);
        generate.setDisable(false);
        back.setDisable(false);
        progress.setVisible(false);
    }

    private static final class ShareCell extends ListCell<NpcShare> {
        private final Texts texts;

        ShareCell(Texts texts) {
            this.texts = texts;
        }

        @Override
        protected void updateItem(NpcShare item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : texts.text("npc_share." + item.key()));
        }
    }
}
