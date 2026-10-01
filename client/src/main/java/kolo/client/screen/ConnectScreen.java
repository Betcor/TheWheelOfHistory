package kolo.client.screen;

import java.net.InetSocketAddress;
import java.util.List;
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
import kolo.client.net.LanLobbies;
import kolo.client.net.RemoteLobby;
import kolo.client.net.ServerAddress;
import kolo.protocol.Protocol;
import kolo.protocol.message.LobbyInfo;
import kolo.protocol.message.Nicknames;
import kolo.protocol.message.WorldInfo;

/**
 * Підключення до гри в локальній мережі чи на окремому сервері (GD §21): пошук відкритих лобі в мережі або за адресою,
 * нікнейм і приєднання до обраного лобі (зі збереженим токеном цього світу — на своє місце), а також світи з теки
 * сервера за адресою: обраний завантажується в нове лобі, якщо на диску є токен місця в ньому.
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

        ListView<RemoteLobby> lobbies = new ListView<>();
        lobbies.setPrefHeight(200);
        Label noLobbies = new Label();
        noLobbies.setWrapText(true);
        lobbies.setPlaceholder(noLobbies);
        lobbies.setCellFactory(list -> new LobbyCell(texts));
        lobbies.setVisible(false);
        ListView<WorldInfo> worlds = new ListView<>();
        worlds.setPrefHeight(140);
        worlds.setPlaceholder(new Label(texts.text("connect.no_worlds")));
        worlds.setCellFactory(list -> new WorldCell(texts));
        worlds.setVisible(false);
        Label worldsTitle = new Label(texts.text("connect.worlds"));
        worldsTitle.visibleProperty().bind(worlds.visibleProperty());

        Label note = new Label();
        note.getStyleClass().add("text-muted");
        note.setWrapText(true);
        Label error = new Label();
        error.getStyleClass().add("danger");
        error.setWrapText(true);
        ProgressIndicator progress = new ProgressIndicator();
        progress.setVisible(false);
        progress.setPrefSize(28, 28);

        Button back = new Button(texts.text("connect.back"));
        back.setCancelButton(true);
        back.setOnAction(event -> context.navigator().showMainMenu());
        Button searchLan = new Button(texts.text("connect.search_lan"));
        searchLan.setDefaultButton(true);
        Button find = new Button(texts.text("connect.find"));
        Button join = new Button(texts.text("connect.join"));
        join.disableProperty()
                .bind(lobbies.getSelectionModel().selectedItemProperty().isNull());
        Button load = new Button(texts.text("connect.load"));
        load.disableProperty()
                .bind(worlds.getSelectionModel().selectedItemProperty().isNull());

        find.setOnAction(event -> {
            InetSocketAddress target;
            try {
                target = ServerAddress.parse(address.getText());
            } catch (IllegalArgumentException e) {
                error.setText(texts.text("connect.address_invalid"));
                return;
            }
            error.setText("");
            note.setText("");
            find.setDisable(true);
            searchLan.setDisable(true);
            progress.setVisible(true);
            UiFutures.onUi(
                    context.game()
                            .findLobbies(target)
                            .thenCompose(found ->
                                    context.game().findWorlds(target).thenApply(saved -> new Found(found, saved))),
                    texts,
                    found -> {
                        noLobbies.setText(texts.text("connect.none"));
                        lobbies.getItems()
                                .setAll(found.lobbies().stream()
                                        .map(lobby -> new RemoteLobby(target, lobby))
                                        .toList());
                        lobbies.setVisible(true);
                        worlds.getItems().setAll(found.worlds());
                        worlds.setVisible(true);
                        find.setDisable(false);
                        searchLan.setDisable(false);
                        progress.setVisible(false);
                    },
                    message -> {
                        error.setText(message);
                        find.setDisable(false);
                        searchLan.setDisable(false);
                        progress.setVisible(false);
                    });
        });

        searchLan.setOnAction(event -> {
            error.setText("");
            note.setText("");
            find.setDisable(true);
            searchLan.setDisable(true);
            progress.setVisible(true);
            UiFutures.onUi(
                    context.game().findLanLobbies(),
                    texts,
                    found -> {
                        noLobbies.setText(texts.text("connect.lan_none"));
                        lobbies.getItems().setAll(found.lobbies());
                        lobbies.setVisible(true);
                        // Світи з теки — лише в сервера за адресою: у мережі їх може бути кілька.
                        worlds.getItems().clear();
                        worlds.setVisible(false);
                        note.setText(incompatible(texts, found));
                        find.setDisable(false);
                        searchLan.setDisable(false);
                        progress.setVisible(false);
                    },
                    message -> {
                        error.setText(message);
                        find.setDisable(false);
                        searchLan.setDisable(false);
                        progress.setVisible(false);
                    });
        });

        join.setOnAction(event -> {
            Optional<String> player = NicknameInput.parse(nickname.getText());
            if (player.isEmpty()) {
                error.setText(texts.text("new_world.nickname_invalid", Nicknames.MAX_LENGTH));
                return;
            }
            RemoteLobby chosen = lobbies.getSelectionModel().getSelectedItem();
            error.setText("");
            context.session().reset();
            progress.setVisible(true);
            UiFutures.onUi(
                    context.game().joinLobby(chosen, player.get()),
                    texts,
                    joined -> context.navigator().showLobby(),
                    message -> {
                        error.setText(message);
                        progress.setVisible(false);
                    });
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
            progress.setVisible(true);
            UiFutures.onUi(
                    context.game().loadWorld(chosen, player.get()),
                    texts,
                    joined -> context.navigator().showLobby(),
                    message -> {
                        error.setText(message);
                        progress.setVisible(false);
                    });
        });

        HBox buttons = new HBox(10, back, searchLan, find, join, load, progress);
        buttons.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(texts.text("connect.title"));
        title.getStyleClass().add("title-2");
        VBox box = new VBox(18, title, form, lobbies, note, worldsTitle, worlds, buttons, error);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(24));
        box.setMaxWidth(560);
        VBox outer = new VBox(box);
        outer.setAlignment(Pos.CENTER);
        return outer;
    }

    /** Примітка про знайдені ігри іншої версії; порожньо, якщо таких немає. */
    private static String incompatible(Texts texts, LanLobbies found) {
        return found.incompatible() == 0 ? "" : texts.text("connect.lan_incompatible", found.incompatible());
    }

    /** Що знайшлося на сервері. */
    private record Found(List<LobbyInfo> lobbies, List<WorldInfo> worlds) {}

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

    private static final class LobbyCell extends ListCell<RemoteLobby> {
        private final Texts texts;

        LobbyCell(Texts texts) {
            this.texts = texts;
        }

        @Override
        protected void updateItem(RemoteLobby item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : LobbyLabels.remote(texts, item));
        }
    }
}
