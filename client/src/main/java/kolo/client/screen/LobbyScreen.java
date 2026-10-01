package kolo.client.screen;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import kolo.client.i18n.Texts;
import kolo.client.net.GameClient;
import kolo.engine.state.TurnTimer;
import kolo.engine.state.WorldLimits;
import kolo.protocol.message.LobbySetup;
import kolo.protocol.message.PlayerInfo;
import kolo.protocol.message.ServerMessage;

/**
 * Лобі (GD §22.2): параметри світу, гравці, таймер ходу й кнопка «Почати гру» для хоста. У лобі нового світу гравці — в порядку
 * приєднання (у тому ж порядку вони отримають держави). У лобі завантаженого — гравці світу з державами (вільні місця
 * теж) і гості без держави: хост віддає гостеві вільне місце. Коли гра почнеться, карту покаже застосунок — подія
 * приходить усім гравцям лобі.
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
        BooleanProperty saved = new SimpleBooleanProperty();
        players.setCellFactory(list -> new PlayerCell(texts, game, saved));

        // Хост завантаженого світу: обраному в списку гостеві — вільне місце.
        ComboBox<PlayerInfo> seats = new ComboBox<>();
        seats.setPromptText(texts.text("lobby.seat_choose"));
        seats.setCellFactory(list -> new SeatCell(texts));
        seats.setButtonCell(new SeatCell(texts));
        Button assign = new Button(texts.text("lobby.assign"));
        assign.disableProperty()
                .bind(seats.valueProperty()
                        .isNull()
                        .or(players.getSelectionModel().selectedItemProperty().isNull()));
        assign.setOnAction(event -> {
            PlayerInfo guest = players.getSelectionModel().getSelectedItem();
            if (guest != null && PlayerLabels.guest(guest)) {
                game.assignSeat(guest.number(), seats.getValue().number());
            }
        });
        HBox seating = new HBox(10, seats, assign);
        seating.setAlignment(Pos.CENTER_LEFT);

        // Хост обирає таймер ходу (GD §6.1); решта бачить його в параметрах світу.
        ComboBox<TurnTimer> timer = new ComboBox<>();
        timer.setCellFactory(list -> new TimerCell(texts));
        timer.setButtonCell(new TimerCell(texts));
        boolean[] showing = {false};
        timer.valueProperty().addListener((property, old, chosen) -> {
            ServerMessage.Lobby lobby = context.session().lobby().get();
            // Значення, яке показує стан лобі з сервера, — не вибір хоста.
            if (!showing[0]
                    && chosen != null
                    && lobby != null
                    && !chosen.equals(lobby.setup().timer())) {
                game.setTimer(chosen);
            }
        });
        HBox timing = new HBox(10, new Label(texts.text("lobby.timer")), timer);
        timing.setAlignment(Pos.CENTER_LEFT);

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
            status.setText(texts.text(saved.get() ? "lobby.resuming" : "lobby.generating"));
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
            saved.set(lobby.setup() instanceof LobbySetup.SavedWorld);
            settings.setText(LobbyLabels.setup(texts, lobby.setup()));
            long connected =
                    lobby.players().stream().filter(PlayerInfo::connected).count();
            count.setText(texts.text("lobby.players", connected, WorldLimits.MAX_PLAYERS));
            players.getItems().setAll(lobby.players());
            boolean host = lobby.players().stream().anyMatch(p -> p.host() && game.isMe(p));
            start.setVisible(host);
            start.setManaged(host);
            showing[0] = true;
            timer.getItems().setAll(lobby.timers());
            timer.setValue(lobby.setup().timer());
            showing[0] = false;
            timing.setVisible(host);
            timing.setManaged(host);
            boolean seatingVisible = host && saved.get();
            seating.setVisible(seatingVisible);
            seating.setManaged(seatingVisible);
            seats.getItems()
                    .setAll(lobby.players().stream()
                            .filter(PlayerLabels::freeSeat)
                            .toList());
            if (!host) {
                status.getStyleClass().remove("danger");
                status.setText(texts.text("lobby.waiting"));
            } else if (!progress.isVisible()) {
                boolean guests = lobby.players().stream().anyMatch(PlayerLabels::guest);
                status.getStyleClass().remove("danger");
                status.setText(saved.get() && guests ? texts.text("lobby.guests_waiting") : "");
            }
        };
        update.changed(
                context.session().lobby(), null, context.session().lobby().get());
        context.session().lobby().addListener(new WeakChangeListener<>(update));

        HBox buttons = new HBox(10, leave, start, progress);
        buttons.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(14, title, settings, lan, timing, count, players, seating, buttons, status);
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
        private final BooleanProperty saved;

        PlayerCell(Texts texts, GameClient game, BooleanProperty saved) {
            this.texts = texts;
            this.game = game;
            this.saved = saved;
        }

        @Override
        protected void updateItem(PlayerInfo item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
            } else if (saved.get()) {
                setText(PlayerLabels.savedLobby(texts, item, game.isMe(item)));
            } else {
                setText(PlayerLabels.lobby(texts, item, game.isMe(item)));
            }
        }
    }

    private static final class TimerCell extends ListCell<TurnTimer> {
        private final Texts texts;

        TimerCell(Texts texts) {
            this.texts = texts;
        }

        @Override
        protected void updateItem(TurnTimer item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : TimerLabels.timer(texts, item));
        }
    }

    private static final class SeatCell extends ListCell<PlayerInfo> {
        private final Texts texts;

        SeatCell(Texts texts) {
            this.texts = texts;
        }

        @Override
        protected void updateItem(PlayerInfo item, boolean empty) {
            super.updateItem(item, empty);
            setText(
                    empty || item == null
                            ? null
                            : texts.text(
                                    "lobby.seat_option",
                                    item.nickname(),
                                    item.country().orElse(0) + 1));
        }
    }
}
