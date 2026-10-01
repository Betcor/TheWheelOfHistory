package kolo.client.screen;

import java.util.Optional;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import kolo.client.i18n.Texts;
import kolo.protocol.Protocol;
import kolo.protocol.message.Nicknames;
import kolo.protocol.message.WorldInfo;

/**
 * «Завантажити» (GD §22.2): світи з теки вбудованого сервера, нікнейм (якщо на диску немає токена місця в світі —
 * клієнт зайде гостем і віддасть місце собі сам) і чи відкрити гру для локальної мережі. Обраний світ відкривається в
 * новому лобі; гра продовжиться з останнього збереженого року, коли хост почне її.
 */
public final class LoadScreen {

    private LoadScreen() {}

    public static Parent create(ScreenContext context) {
        Texts texts = context.texts();
        TextField nickname = new TextField(texts.text("new_world.nickname_default"));
        CheckBox lan = new CheckBox(texts.text("new_world.lan"));
        GridPane form = new GridPane(12, 10);
        form.addRow(0, new Label(texts.text("new_world.nickname")), nickname);
        form.add(lan, 1, 1);

        ListView<WorldInfo> worlds = new ListView<>();
        worlds.setPrefHeight(260);
        worlds.setPlaceholder(new Label(texts.text("load.none")));
        worlds.setCellFactory(list -> new WorldCell(texts));

        Label error = new Label();
        error.getStyleClass().add("danger");
        error.setWrapText(true);
        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(28, 28);

        Button back = new Button(texts.text("load.back"));
        back.setCancelButton(true);
        back.setOnAction(event -> context.navigator().showMainMenu());
        Button load = new Button(texts.text("load.load"));
        load.setDefaultButton(true);
        load.disableProperty()
                .bind(worlds.getSelectionModel().selectedItemProperty().isNull());

        UiFutures.onUi(
                context.game().localWorlds(),
                texts,
                found -> {
                    worlds.getItems().setAll(found);
                    progress.setVisible(false);
                },
                message -> {
                    error.setText(message);
                    progress.setVisible(false);
                });

        load.setOnAction(event -> {
            Optional<String> player = NicknameInput.parse(nickname.getText());
            if (player.isEmpty()) {
                error.setText(texts.text("new_world.nickname_invalid", Nicknames.MAX_LENGTH));
                return;
            }
            WorldInfo chosen = worlds.getSelectionModel().getSelectedItem();
            error.setText("");
            context.session().reset();
            back.setDisable(true);
            progress.setVisible(true);
            UiFutures.onUi(
                    context.game().loadLocalWorld(chosen, player.get(), lan.isSelected()),
                    texts,
                    joined -> context.navigator().showLobby(),
                    message -> {
                        error.setText(message);
                        back.setDisable(false);
                        progress.setVisible(false);
                    });
        });

        HBox buttons = new HBox(10, back, load, progress);
        buttons.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(texts.text("load.title"));
        title.getStyleClass().add("title-2");
        Label port = new Label(texts.text("lobby.lan", Protocol.DEFAULT_PORT));
        port.getStyleClass().add("text-muted");
        port.visibleProperty().bind(lan.selectedProperty());
        VBox box = new VBox(18, title, worlds, form, port, buttons, error);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(24));
        box.setMaxWidth(560);
        VBox outer = new VBox(box);
        outer.setAlignment(Pos.CENTER);
        return outer;
    }

    private static final class WorldCell extends ListCell<WorldInfo> {
        private final Texts texts;

        WorldCell(Texts texts) {
            this.texts = texts;
        }

        @Override
        protected void updateItem(WorldInfo item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : LobbyLabels.world(texts, item));
        }
    }
}
