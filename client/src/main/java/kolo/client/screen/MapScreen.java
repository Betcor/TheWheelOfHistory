package kolo.client.screen;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import kolo.client.i18n.Texts;
import kolo.client.map.MapCanvas;
import kolo.client.map.MapLayers;
import kolo.client.map.MapMode;
import kolo.client.net.GameClient;
import kolo.client.net.GameStart;
import kolo.client.state.SessionModel;
import kolo.engine.state.WorldState;
import kolo.engine.view.MapView;
import kolo.protocol.message.PlayerInfo;
import kolo.protocol.message.ServerMessage;
import kolo.protocol.message.YearPhase;

/**
 * Карта світу (GD §22.2): режими карти, поточний рік і кнопка «Готово», панель обраної провінції, гравці (хто вже
 * готовий, хто не на зв'язку) і рядок стану. Поки систем немає, роки «порожні»: рік розв'язується, коли «Готово»
 * натиснули всі гравці на зв'язку, коли хост натиснув «Завершити рік» або коли вийшов час таймера ходу (тоді видно
 * відлік). Зв'язок втрачено — кнопка «Перепідключитися» повертає гравця до його держави.
 */
public final class MapScreen {

    private static final double PANEL_WIDTH = 280;

    private MapScreen() {}

    /** @param start світ, у який клієнт увійшов: рік і фаза */
    public static Parent create(ScreenContext context, MapLayers layers, GameStart start) {
        Texts texts = context.texts();
        GameClient game = context.game();
        SessionModel session = context.session();
        MapView view = layers.view();
        MapCanvas canvas = new MapCanvas(layers, context.background());

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
            game.leave();
            session.reset();
            context.navigator().showMainMenu();
        });
        Label year = new Label(yearText(texts, start.turn()));
        year.getStyleClass().add("title-4");
        Button endYear = new Button(texts.text("map.end_year"));
        Button finish = new Button(texts.text("map.finish_year"));
        finish.setTooltip(new Tooltip(texts.text("map.finish_year.tooltip")));
        Label timeLeft = new Label();
        Button reconnect = new Button(texts.text("map.reconnect"));
        reconnect.setVisible(false);
        reconnect.setManaged(false);
        toolbar.getItems()
                .addAll(
                        new Separator(),
                        new Label(texts.text(
                                "map.world", view.seed(), view.countries().size())),
                        spacer,
                        year,
                        timeLeft,
                        endYear,
                        finish,
                        reconnect,
                        new Separator(),
                        fit,
                        menu);

        Label title = new Label(texts.text("map.info.none"));
        title.getStyleClass().add("title-4");
        title.setWrapText(true);
        VBox details = new VBox(6);
        Label playersTitle = new Label(texts.text("map.players"));
        playersTitle.getStyleClass().add("title-4");
        VBox players = new VBox(4);
        VBox panel = new VBox(12, title, details, new Separator(), playersTitle, players);
        panel.setPadding(new Insets(12));
        panel.setPrefWidth(PANEL_WIDTH);
        panel.setMinWidth(PANEL_WIDTH);

        Label status = new Label(texts.text("map.status.hint"));
        status.setPadding(new Insets(4, 8, 4, 8));

        // Поточний рік і чи вже натиснуто «Готово» — лише в потоці JavaFX.
        int[] current = {start.turn()};
        boolean[] sent = {false};
        boolean[] finished = {false};
        Runnable refreshReady = () -> {
            ServerMessage.Phase phase = session.phase().get();
            boolean orders = phase != null && phase.phase() == YearPhase.ORDERS && phase.turn() == current[0];
            boolean meReady = session.players().get().stream().anyMatch(p -> game.isMe(p) && p.ready());
            boolean connected = session.connected().get();
            endYear.setDisable(!orders || sent[0] || meReady || !connected);
            boolean host = session.players().get().stream().anyMatch(p -> game.isMe(p) && p.host());
            finish.setVisible(host);
            finish.setManaged(host);
            finish.setDisable(!orders || finished[0] || !connected);
        };
        endYear.setOnAction(event -> {
            sent[0] = true;
            refreshReady.run();
            game.ready(current[0]);
        });
        finish.setOnAction(event -> {
            finished[0] = true;
            refreshReady.run();
            game.endYear(current[0]);
        });

        // Відлік до кінця фази наказів: межа — з моделі, час, що лишився, — щосекунди за годинником клієнта.
        Clock clock = Clock.systemUTC();
        Runnable refreshClock = () -> {
            Instant deadline = session.ordersDeadline().get();
            ServerMessage.Phase phase = session.phase().get();
            boolean counting = deadline != null && phase != null && phase.phase() == YearPhase.ORDERS;
            timeLeft.setVisible(counting);
            timeLeft.setManaged(counting);
            if (counting) {
                timeLeft.setText(TimerLabels.timeLeft(texts, java.time.Duration.between(clock.instant(), deadline)));
            }
        };
        Timeline ticker = new Timeline(new KeyFrame(Duration.seconds(1), event -> refreshClock.run()));
        ticker.setCycleCount(Animation.INDEFINITE);
        ChangeListener<Instant> deadlines = (property, old, deadline) -> {
            refreshClock.run();
            if (deadline != null) {
                ticker.play();
            } else {
                ticker.stop();
            }
        };

        ChangeListener<ServerMessage.Phase> phases = (property, old, phase) -> {
            if (phase == null) {
                return;
            }
            if (phase.phase() == YearPhase.RESOLVING) {
                status.setText(texts.text("map.year_resolving"));
            } else if (phase.phase() == YearPhase.ORDERS && phase.turn() > current[0]) {
                current[0] = phase.turn();
                sent[0] = false;
                finished[0] = false;
                year.setText(yearText(texts, phase.turn()));
                status.setText(texts.text("map.year_started", WorldState.year(phase.turn())));
            }
            refreshReady.run();
            refreshClock.run();
        };
        ChangeListener<List<PlayerInfo>> roster = (property, old, list) -> {
            players.getChildren().clear();
            for (PlayerInfo player : list) {
                Label label = new Label(PlayerLabels.game(texts, player));
                label.setWrapText(true);
                if (game.isMe(player)) {
                    label.getStyleClass().add("text-bold");
                }
                players.getChildren().add(label);
            }
            String waiting = PlayerLabels.waitingFor(list);
            if (sent[0] && !waiting.isEmpty()) {
                status.setText(texts.text("map.waiting", waiting));
            }
            refreshReady.run();
        };
        ChangeListener<Boolean> link = (property, old, connected) -> {
            reconnect.setVisible(!connected);
            reconnect.setManaged(!connected);
            if (!connected) {
                status.setText(texts.text("app.error.connection_lost"));
            }
            refreshReady.run();
        };
        session.phase().addListener(new WeakChangeListener<>(phases));
        session.players().addListener(new WeakChangeListener<>(roster));
        session.connected().addListener(new WeakChangeListener<>(link));
        session.ordersDeadline().addListener(new WeakChangeListener<>(deadlines));
        session.setOnError(error -> {
            sent[0] = false;
            finished[0] = false;
            status.setText(texts.error(error.code(), error.details()));
            refreshReady.run();
        });
        roster.changed(session.players(), null, session.players().get());
        link.changed(session.connected(), null, session.connected().get());
        deadlines.changed(
                session.ordersDeadline(), null, session.ordersDeadline().get());

        reconnect.setOnAction(event -> {
            reconnect.setDisable(true);
            UiFutures.onUi(
                    game.reconnect(),
                    texts,
                    // Карту заново покаже застосунок, коли сервер її надішле.
                    joined -> {
                        canvas.dispose();
                        status.setText(texts.text("map.reconnected"));
                    },
                    message -> {
                        reconnect.setDisable(false);
                        status.setText(message);
                    });
        });

        canvas.setOnHover(cell -> status.setText(
                cell.isPresent()
                        ? ProvinceDescription.summary(view, cell.getAsInt(), texts)
                        : texts.text("map.status.hint")));
        canvas.setOnSelect(cell -> show(cell, view, texts, title, details));

        BorderPane root = new BorderPane(canvas);
        root.setTop(toolbar);
        root.setRight(panel);
        root.setBottom(status);
        // Слабкі слухачі живуть, доки живе екран.
        root.getProperties().put(MapScreen.class, List.of(phases, roster, link, deadlines));
        // Екран прибрали зі сцени — відлік більше не потрібен.
        root.sceneProperty().addListener((property, old, scene) -> {
            if (scene == null) {
                ticker.stop();
            }
        });
        canvas.requestFocus();
        return root;
    }

    private static String yearText(Texts texts, int turn) {
        return texts.text("map.year", WorldState.year(turn));
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
