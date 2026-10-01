package kolo.client.state;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import kolo.client.net.GameStart;
import kolo.client.net.SessionListener;
import kolo.protocol.message.PlayerInfo;
import kolo.protocol.message.ServerMessage;

/**
 * Сесія, як її бачать екрани: стан лобі, гравці, поточна фаза року, зв'язок. Події з'єднання приходять у потоці
 * мережі й переходять у потік UI через виконавця {@code ui} (у грі — {@code Platform::runLater}): властивості
 * змінюються лише там.
 *
 * <p>Події, які не є станом (почалася гра, прийшла помилка), — обробникам, які ставить поточний екран.
 */
public final class SessionModel implements SessionListener {

    private final Executor ui;
    private final ObjectProperty<ServerMessage.Lobby> lobby = new SimpleObjectProperty<>();
    private final ObjectProperty<List<PlayerInfo>> players = new SimpleObjectProperty<>(List.of());
    private final ObjectProperty<ServerMessage.Phase> phase = new SimpleObjectProperty<>();
    private final BooleanProperty connected = new SimpleBooleanProperty(true);
    private Consumer<GameStart> onGameStarted = start -> {};
    private Consumer<ServerMessage.Error> onError = error -> {};

    /** @param ui виконавець потоку UI */
    public SessionModel(Executor ui) {
        this.ui = Objects.requireNonNull(ui, "ui");
    }

    /** Стан лобі; {@code null} — клієнт не в лобі. */
    public ReadOnlyObjectProperty<ServerMessage.Lobby> lobby() {
        return lobby;
    }

    /** Гравці гри, що йде; до її початку — порожньо. */
    public ReadOnlyObjectProperty<List<PlayerInfo>> players() {
        return players;
    }

    /** Поточна фаза року; {@code null} — гра ще не почалася. */
    public ReadOnlyObjectProperty<ServerMessage.Phase> phase() {
        return phase;
    }

    /** Чи є зв'язок із сервером. */
    public ReadOnlyBooleanProperty connected() {
        return connected;
    }

    /** Обробник початку гри (і повернення в неї); викликається в потоці UI. */
    public void setOnGameStarted(Consumer<GameStart> handler) {
        onGameStarted = Objects.requireNonNull(handler, "handler");
    }

    /** Обробник помилок сервера без запиту; викликається в потоці UI. */
    public void setOnError(Consumer<ServerMessage.Error> handler) {
        onError = Objects.requireNonNull(handler, "handler");
    }

    /** Забуває сесію: клієнт її полишив. Викликати з потоку UI. */
    public void reset() {
        lobby.set(null);
        players.set(List.of());
        phase.set(null);
        connected.set(true);
    }

    // ---- Події з'єднання ----

    @Override
    public void lobby(ServerMessage.Lobby update) {
        ui.execute(() -> {
            connected.set(true);
            lobby.set(update);
        });
    }

    @Override
    public void gameStarted(GameStart start) {
        ui.execute(() -> {
            connected.set(true);
            lobby.set(null);
            phase.set(new ServerMessage.Phase(start.turn(), start.phase()));
            onGameStarted.accept(start);
        });
    }

    @Override
    public void players(List<PlayerInfo> update) {
        ui.execute(() -> players.set(List.copyOf(update)));
    }

    @Override
    public void phase(ServerMessage.Phase update) {
        ui.execute(() -> phase.set(update));
    }

    @Override
    public void error(ServerMessage.Error error) {
        ui.execute(() -> onError.accept(error));
    }

    @Override
    public void disconnected() {
        ui.execute(() -> connected.set(false));
    }
}
