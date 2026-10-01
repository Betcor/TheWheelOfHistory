package kolo.client.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import kolo.client.map.TestMaps;
import kolo.client.net.GameStart;
import kolo.engine.error.ErrorCode;
import kolo.engine.state.NpcShare;
import kolo.protocol.message.PlayerInfo;
import kolo.protocol.message.ServerMessage;
import kolo.protocol.message.YearPhase;
import org.junit.jupiter.api.Test;

/** Події з'єднання → властивості для екранів; потік UI підмінено виконавцем, що збирає завдання. */
class SessionModelTest {

    private final List<Runnable> queued = new ArrayList<>();
    private final SessionModel model = new SessionModel(queued::add);

    @Test
    void eventsChangeNothingUntilTheUiThreadRunsThem() {
        model.lobby(lobby());

        assertThat(model.lobby().get()).isNull();
        runQueued();
        assertThat(model.lobby().get()).isEqualTo(lobby());
    }

    @Test
    void gameStartLeavesTheLobbyAndSetsThePhase() {
        List<GameStart> started = new ArrayList<>();
        model.setOnGameStarted(started::add);
        model.lobby(lobby());
        GameStart start = new GameStart(TestMaps.MAP, 3, YearPhase.ORDERS);

        model.gameStarted(start);
        runQueued();

        assertThat(model.lobby().get()).isNull();
        assertThat(model.phase().get()).isEqualTo(new ServerMessage.Phase(3, YearPhase.ORDERS));
        assertThat(started).containsExactly(start);
    }

    @Test
    void playersPhasesAndDisconnect() {
        List<PlayerInfo> players = List.of(new PlayerInfo(1, "Оля", true, true, true, OptionalInt.of(0)));

        model.players(players);
        model.phase(new ServerMessage.Phase(1, YearPhase.RESOLVING));
        model.disconnected();
        runQueued();

        assertThat(model.players().get()).isEqualTo(players);
        assertThat(model.phase().get()).isEqualTo(new ServerMessage.Phase(1, YearPhase.RESOLVING));
        assertThat(model.connected().get()).isFalse();

        model.reset();
        assertThat(model.players().get()).isEmpty();
        assertThat(model.phase().get()).isNull();
        assertThat(model.connected().get()).isTrue();
    }

    @Test
    void errorsGoToTheHandler() {
        List<ServerMessage.Error> errors = new ArrayList<>();
        model.setOnError(errors::add);
        ServerMessage.Error error = new ServerMessage.Error(ErrorCode.FORBIDDEN, new java.util.TreeMap<>());

        model.error(error);
        runQueued();

        assertThat(errors).containsExactly(error);
    }

    private void runQueued() {
        List<Runnable> tasks = new ArrayList<>(queued);
        queued.clear();
        tasks.forEach(Runnable::run);
    }

    private static ServerMessage.Lobby lobby() {
        return new ServerMessage.Lobby(
                1, 2, NpcShare.FEW, List.of(new PlayerInfo(1, "Оля", true, true, false, OptionalInt.empty())));
    }
}
