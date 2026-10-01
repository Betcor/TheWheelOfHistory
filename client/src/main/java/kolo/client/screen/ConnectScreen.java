package kolo.client.screen;

import java.net.InetSocketAddress;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import kolo.client.i18n.Texts;
import kolo.client.net.ServerAddress;
import kolo.protocol.Protocol;
import kolo.protocol.message.LobbyInfo;
import kolo.protocol.message.Nicknames;

/**
 * Підключення до гри в локальній мережі чи на окремому сервері (GD §21): адреса, нікнейм, список відкритих лобі й
 * приєднання до обраного.
 */
public final class ConnectScreen {

    private ConnectScreen() {}

    public static Parent create(ScreenContext context) {
        Texts texts = context.texts();
        TextField address = new TextField();
        address.setPromptText(texts.text("connect.address_hint", Protocol.DEFAULT_PORT));
        TextField nickname = new TextField(texts.text("new_world.nickname_default"));
        GridPane form = new GridPane(12, 10);
        form.addRow(0, new Label(texts.text("connect.address")), address);
        form.addRow(1, new Label(texts.text("connect.nickname")), nickname);

        ListView<LobbyInfo> lobbies = new ListView<>();
        lobbies.setPrefHeight(200);
        lobbies.setPlaceholder(new Label(texts.text("connect.none")));
        lobbies.setCellFactory(list -> new LobbyCell(texts));
        lobbies.setVisible(false);

        Label error = new Label();
        error.getStyleClass().add("danger");
        error.setWrapText(true);
        ProgressIndicator progress = new ProgressIndicator();
        progress.setVisible(false);
        progress.setPrefSize(28, 28);

        Button back = new Button(texts.text("connect.back"));
        back.setCancelButton(true);
        back.setOnAction(event -> context.navigator().showMainMenu());
        Button find = new Button(texts.text("connect.find"));
        find.setDefaultButton(true);
        Button join = new Button(texts.text("connect.join"));
        join.disableProperty()
                .bind(lobbies.getSelectionModel().selectedItemProperty().isNull());

        find.setOnAction(event -> {
            InetSocketAddress target;
            try {
                target = ServerAddress.parse(address.getText());
            } catch (IllegalArgumentException e) {
                error.setText(texts.text("connect.address_invalid"));
                return;
            }
            error.setText("");
            find.setDisable(true);
            progress.setVisible(true);
            UiFutures.onUi(
                    context.game().findLobbies(target),
                    texts,
                    found -> {
                        lobbies.getItems().setAll(found);
                        lobbies.setVisible(true);
                        find.setDisable(false);
                        progress.setVisible(false);
                    },
                    message -> {
                        error.setText(message);
                        find.setDisable(false);
                        progress.setVisible(false);
                    });
        });

        join.setOnAction(event -> {
            Optional<String> player = NicknameInput.parse(nickname.getText());
            if (player.isEmpty()) {
                error.setText(texts.text("new_world.nickname_invalid", Nicknames.MAX_LENGTH));
                return;
            }
            LobbyInfo chosen = lobbies.getSelectionModel().getSelectedItem();
            error.setText("");
            context.session().reset();
            progress.setVisible(true);
            UiFutures.onUi(
                    context.game().joinLobby(chosen.session(), player.get()),
                    texts,
                    joined -> context.navigator().showLobby(),
                    message -> {
                        error.setText(message);
                        progress.setVisible(false);
                    });
        });

        HBox buttons = new HBox(10, back, find, join, progress);
        buttons.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(texts.text("connect.title"));
        title.getStyleClass().add("title-2");
        VBox box = new VBox(18, title, form, lobbies, buttons, error);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(24));
        box.setMaxWidth(560);
        VBox outer = new VBox(box);
        outer.setAlignment(Pos.CENTER);
        return outer;
    }

    private static final class LobbyCell extends ListCell<LobbyInfo> {
        private final Texts texts;

        LobbyCell(Texts texts) {
            this.texts = texts;
        }

        @Override
        protected void updateItem(LobbyInfo item, boolean empty) {
            super.updateItem(item, empty);
            setText(
                    empty || item == null
                            ? null
                            : texts.text(
                                    "connect.lobby",
                                    item.host(),
                                    item.players(),
                                    texts.text("npc_share." + item.npcShare().key())));
        }
    }
}
