package kolo.client.screen;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import kolo.client.i18n.Texts;
import kolo.client.net.GameClient;
import kolo.engine.state.WorldLimits;
import kolo.protocol.message.PlayerInfo;
import kolo.protocol.message.ServerMessage;

/**
 * Лобі (GD §22.2): параметри світу, гравці в порядку приєднання (у тому ж порядку вони отримають держави) і кнопка
 * «Почати гру» для хоста. Коли гра почнеться, карту покаже застосунок — подія приходить усім гравцям лобі.
 */
public final class LobbyScreen {

    private LobbyScreen() {}

    public static Parent create(ScreenContext context) {
        Texts texts = context.texts();
        GameClient game = context.game();
        Label title = new Label(texts.text("lobby.title"));
        title.getStyleClass().add("title-2");
        Label settings = new Label();
        Label lan = new Label();
        lan.getStyleClass().add("text-muted");
        game.lanAddress()
                .ifPresentOrElse(
                        address -> lan.setText(texts.text("lobby.lan", address.getPort())),
                        () -> lan.setManaged(false));
        Label count = new Label();
        ListView<PlayerInfo> players = new ListView<>();
        players.setPrefHeight(260);
        players.setCellFactory(list -> new PlayerCell(texts, game));

        Label status = new Label();
        status.setWrapText(true);
        ProgressIndicator progress = new ProgressIndicator();
        progress.setVisible(false);
        progress.setPrefSize(28, 28);
        Button start = new Button(texts.text("lobby.start"));
        start.setDefaultButton(true);
        Button leave = new Button(texts.text("lobby.leave"));
        leave.setCancelButton(true);
        leave.setOnAction(event -> {
            game.leave();
            context.session().reset();
            context.navigator().showMainMenu();
        });
        start.setOnAction(event -> {
            start.setDisable(true);
            progress.setVisible(true);
            status.getStyleClass().remove("danger");
            status.setText(texts.text("lobby.generating"));
            game.startGame();
        });
        context.session().setOnError(error -> {
            start.setDisable(false);
            progress.setVisible(false);
            status.getStyleClass().add("danger");
            status.setText(texts.error(error.code(), error.details()));
        });

        ChangeListener<ServerMessage.Lobby> update = (property, old, lobby) -> {
            if (lobby == null) {
                return;
            }
            settings.setText(texts.text(
                    "lobby.settings",
                    lobby.seed(),
                    texts.text("npc_share." + lobby.npcShare().key())));
            count.setText(texts.text("lobby.players", lobby.players().size(), WorldLimits.MAX_PLAYERS));
            players.getItems().setAll(lobby.players());
            boolean host = lobby.players().stream().anyMatch(p -> p.host() && game.isMe(p));
            start.setVisible(host);
            start.setManaged(host);
            if (!host) {
                status.setText(texts.text("lobby.waiting"));
            } else if (!progress.isVisible()) {
                status.setText("");
            }
        };
        update.changed(
                context.session().lobby(), null, context.session().lobby().get());
        context.session().lobby().addListener(new WeakChangeListener<>(update));

        HBox buttons = new HBox(10, leave, start, progress);
        buttons.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(14, title, settings, lan, count, players, buttons, status);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(24));
        box.setMaxWidth(520);
        // Слабкий слухач живе, доки живе екран.
        box.getProperties().put(LobbyScreen.class, update);
        VBox outer = new VBox(box);
        outer.setAlignment(Pos.CENTER);
        return outer;
    }

    private static final class PlayerCell extends ListCell<PlayerInfo> {
        private final Texts texts;
        private final GameClient game;

        PlayerCell(Texts texts, GameClient game) {
            this.texts = texts;
            this.game = game;
        }

        @Override
        protected void updateItem(PlayerInfo item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : PlayerLabels.lobby(texts, item, game.isMe(item)));
        }
    }
}
