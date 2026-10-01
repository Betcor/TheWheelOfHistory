package kolo.client.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import kolo.client.map.TestMaps;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ProtocolException;
import kolo.engine.error.VersionMismatchException;
import kolo.engine.state.NpcShare;
import kolo.engine.state.TurnTimer;
import kolo.protocol.Protocol;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.LobbyInfo;
import kolo.protocol.message.LobbySetup;
import kolo.protocol.message.MapChunks;
import kolo.protocol.message.PlayerInfo;
import kolo.protocol.message.ServerMessage;
import kolo.protocol.message.YearPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Клієнтський бік розмови в {@link EmbeddedChannel} без кодека: повідомлення — об'єкти. */
class ConnectionHandlerTest {

    private static final String HASH = "e".repeat(64);
    private static final String WORLD = "0123456789abcdef0123456789abcdef";
    private static final ClientMessage.CreateLobby CREATE = new ClientMessage.CreateLobby("Оля", 1, NpcShare.FEW);
    private static final ServerMessage.Joined JOINED = new ServerMessage.Joined(3, WORLD, 1, "token");
    private static final List<PlayerInfo> PLAYERS =
            List.of(new PlayerInfo(1, "Оля", true, true, false, OptionalInt.of(0)));

    private final RecordingListener listener = new RecordingListener();
    private final ConnectionHandler handler = new ConnectionHandler(HASH, listener);
    private final EmbeddedChannel channel = new EmbeddedChannel(handler);

    @BeforeEach
    void welcome() {
        channel.writeInbound(new ServerMessage.Welcome(Protocol.VERSION, HASH));
    }

    @Test
    void welcomeWithOtherContentFailsHandshakeAndCloses() {
        ConnectionHandler other = new ConnectionHandler(HASH, listener);
        EmbeddedChannel otherChannel = new EmbeddedChannel(other);

        otherChannel.writeInbound(new ServerMessage.Welcome(Protocol.VERSION, "f".repeat(64)));

        assertThatThrownBy(() -> other.welcomed().get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(VersionMismatchException.class);
        assertThat(otherChannel.isOpen()).isFalse();
    }

    @Test
    void serverErrorBeforeWelcomeFailsHandshake() {
        ConnectionHandler other = new ConnectionHandler(HASH, listener);
        new EmbeddedChannel(other).writeInbound(error(ErrorCode.PROTOCOL_ERROR));

        assertThatThrownBy(() -> other.welcomed().get()).hasCauseInstanceOf(ServerErrorException.class);
    }

    @Test
    void welcomeCompletesHandshake() {
        assertThat(handler.welcomed()).isCompleted();
    }

    @Test
    void joinedAnswersTheRequest() throws Exception {
        CompletableFuture<ServerMessage.Joined> joined = join();

        assertThat(channel.<ClientMessage>readOutbound()).isEqualTo(CREATE);
        channel.writeInbound(JOINED);

        assertThat(joined.get()).isEqualTo(JOINED);
    }

    @Test
    void lobbiesAnswerTheRequest() throws Exception {
        CompletableFuture<ServerMessage.Lobbies> lobbies = new CompletableFuture<>();
        handler.request(channel, new ClientMessage.ListLobbies(), ServerMessage.Lobbies.class, lobbies);
        ServerMessage.Lobbies reply = new ServerMessage.Lobbies(
                List.of(new LobbyInfo(3, WORLD, "Оля", 2, new LobbySetup.NewWorld(1, NpcShare.FEW, TurnTimer.MANUAL))));

        channel.writeInbound(reply);

        assertThat(lobbies.get()).isEqualTo(reply);
    }

    @Test
    void secondRequestWhileTheFirstWaitsIsRefused() {
        join();

        assertThat(join()).isCompletedExceptionally();
    }

    @Test
    void lobbyAndPlayersGoToTheListener() throws Exception {
        ServerMessage.Lobby lobby = new ServerMessage.Lobby(
                3,
                WORLD,
                new LobbySetup.NewWorld(1, NpcShare.FEW, TurnTimer.MANUAL),
                List.of(new PlayerInfo(1, "Оля", true, true, false, OptionalInt.empty())),
                List.of(TurnTimer.MANUAL));

        channel.writeInbound(lobby);
        channel.writeInbound(new ServerMessage.Players(PLAYERS));

        assertThat(listener.next()).isEqualTo(new RecordingListener.Lobby(lobby));
        assertThat(listener.next()).isEqualTo(new RecordingListener.Players(PLAYERS));
    }

    @Test
    void gameStartsOnTheFirstPhaseAfterTheMap() throws Exception {
        for (ServerMessage part : MapChunks.split(TestMaps.MAP, 2)) {
            channel.writeInbound(part);
        }
        channel.writeInbound(new ServerMessage.Players(PLAYERS));
        assertThat(listener.next()).isInstanceOf(RecordingListener.Players.class);
        assertThat(listener.quiet()).isTrue();

        channel.writeInbound(new ServerMessage.Phase(0, YearPhase.START_OF_YEAR));
        channel.writeInbound(new ServerMessage.Phase(0, YearPhase.ORDERS));

        assertThat(listener.next())
                .isEqualTo(new RecordingListener.Started(
                        new GameStart(TestMaps.MAP, new ServerMessage.Phase(0, YearPhase.START_OF_YEAR))));
        assertThat(listener.next())
                .isEqualTo(new RecordingListener.Phase(new ServerMessage.Phase(0, YearPhase.ORDERS)));
    }

    @Test
    void phaseWithoutAMapIsAProtocolErrorAndCloses() {
        channel.writeInbound(new ServerMessage.Phase(0, YearPhase.ORDERS));

        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void cellsWithoutAMapStartAreAProtocolError() {
        CompletableFuture<ServerMessage.Joined> joined = join();

        channel.writeInbound(MapChunks.split(TestMaps.MAP, 2).get(1));

        assertThatThrownBy(joined::get).hasCauseInstanceOf(ProtocolException.class);
        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void unexpectedReplyIsAProtocolError() {
        channel.writeInbound(new ServerMessage.Lobbies(List.of()));

        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void joinedWithoutARequestIsASeatFromTheHost() throws Exception {
        channel.writeInbound(JOINED);

        assertThat(channel.isOpen()).isTrue();
        assertThat(listener.next()).isEqualTo(new RecordingListener.Joined(JOINED));
    }

    @Test
    void joinedForgetsThePreviousGame() throws Exception {
        MapChunks.split(TestMaps.MAP).forEach(channel::writeInbound);
        channel.writeInbound(new ServerMessage.Phase(0, YearPhase.ORDERS));
        listener.next(RecordingListener.Started.class);
        join();
        channel.writeInbound(JOINED);

        // Нова сесія: фаза без її карти — порушення порядку.
        channel.writeInbound(new ServerMessage.Phase(5, YearPhase.ORDERS));

        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void errorFailsThePendingRequest() {
        CompletableFuture<ServerMessage.Joined> joined = join();

        channel.writeInbound(error(ErrorCode.NICKNAME_TAKEN, "nickname", "Оля"));

        assertThat(joined).isCompletedExceptionally();
        assertThatThrownBy(joined::join)
                .cause()
                .isInstanceOfSatisfying(
                        ServerErrorException.class,
                        e -> assertThat(e.error().code()).isEqualTo(ErrorCode.NICKNAME_TAKEN));
        assertThat(channel.isOpen()).isTrue();
    }

    @Test
    void errorWithoutRequestGoesToTheListener() throws Exception {
        ServerMessage.Error error = error(ErrorCode.FORBIDDEN);

        channel.writeInbound(error);

        assertThat(listener.next()).isEqualTo(new RecordingListener.Error(error));
    }

    @Test
    void disconnectFailsTheRequestAndTellsTheListener() throws Exception {
        CompletableFuture<ServerMessage.Joined> joined = join();

        channel.close();

        assertThatThrownBy(joined::get).hasCauseInstanceOf(ConnectionClosedException.class);
        assertThat(listener.next()).isEqualTo(new RecordingListener.Disconnected());
    }

    @Test
    void sendWritesWithoutWaiting() {
        handler.send(channel, new ClientMessage.Ready(4));

        assertThat(channel.<ClientMessage>readOutbound()).isEqualTo(new ClientMessage.Ready(4));
    }

    private CompletableFuture<ServerMessage.Joined> join() {
        CompletableFuture<ServerMessage.Joined> result = new CompletableFuture<>();
        handler.request(channel, CREATE, ServerMessage.Joined.class, result);
        return result;
    }

    private static ServerMessage.Error error(ErrorCode code, String... pairs) {
        Map<String, Object> details = new TreeMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            details.put(pairs[i], pairs[i + 1]);
        }
        return new ServerMessage.Error(code, new TreeMap<>(details));
    }
}
