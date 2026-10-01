package kolo.client.net;

import static kolo.client.TestWorlds.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.channel.local.LocalAddress;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import kolo.client.TestWorlds;
import kolo.engine.error.ErrorCode;
import kolo.engine.state.NpcShare;
import kolo.protocol.message.Handshake;
import kolo.protocol.message.LobbyInfo;
import kolo.protocol.message.LobbySetup;
import kolo.protocol.message.PlayerInfo;
import kolo.protocol.message.ServerMessage;
import kolo.protocol.message.YearPhase;
import kolo.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Клієнт ↔ вбудований сервер через {@code LocalChannel}: рукостискання, лобі, приєднання, старт, роки, повернення,
 * помилки сервера й розрив.
 */
@Timeout(60)
class ServerConnectionTest {

    @TempDir
    static Path worlds;

    private static EmbeddedServer SERVER;

    @BeforeAll
    static void start() {
        SERVER = EmbeddedServer.startWithBundledContent(worlds);
    }

    @AfterAll
    static void stop() {
        SERVER.close();
    }

    @Test
    void soloGameStartsAndYearsFollow() throws Exception {
        RecordingListener events = new RecordingListener();
        try (ServerConnection connection = connect(events)) {
            ServerMessage.Joined joined = await(connection.createLobby("Оля", 5, NpcShare.FEW));
            ServerMessage.Lobby lobby =
                    events.next(RecordingListener.Lobby.class).lobby();
            assertThat(lobby.session()).isEqualTo(joined.session());
            assertThat(lobby.players()).extracting(PlayerInfo::nickname).containsExactly("Оля");

            connection.startGame();

            GameStart start = TestWorlds.started(events);
            assertThat(start.turn()).isZero();
            assertThat(start.map().seed()).isEqualTo(5);
            events.awaitOrders(0);
            connection.ready(0);
            events.awaitOrders(1);
            connection.ready(1);
            events.awaitOrders(2);

            connection.ready(0);
            assertThat(events.next(RecordingListener.Error.class).error().code())
                    .isEqualTo(ErrorCode.PHASE_CLOSED);
            assertThat(connection.isOpen()).isTrue();
        }
    }

    @Test
    void sameSeedSameMapAsTheTestWorlds() throws Exception {
        assertThat(TestWorlds.DEFAULT.seed()).isEqualTo(1970);
        RecordingListener events = new RecordingListener();
        try (ServerConnection connection = connect(events)) {
            await(connection.createLobby("Оля", 1970, NpcShare.NORMAL));
            connection.startGame();

            assertThat(TestWorlds.started(events).map()).isEqualTo(TestWorlds.DEFAULT);
        }
    }

    @Test
    void secondClientFindsAndJoinsTheLobby() throws Exception {
        RecordingListener hostEvents = new RecordingListener();
        RecordingListener guestEvents = new RecordingListener();
        try (ServerConnection host = connect(hostEvents);
                ServerConnection guest = connect(guestEvents)) {
            ServerMessage.Joined created = await(host.createLobby("Оля", 6, NpcShare.FEW));
            long session = created.session();

            assertThat(await(guest.lobbies()))
                    .contains(new LobbyInfo(
                            session, created.world(), "Оля", 1, new LobbySetup.NewWorld(6, NpcShare.FEW)));
            ServerMessage.Joined joined = await(guest.joinLobby(session, "Ігор"));
            assertThat(joined.player()).isEqualTo(2);
            assertThat(guestEvents.next(RecordingListener.Lobby.class).lobby().players())
                    .hasSize(2);
            host.startGame();

            GameStart hostStart = TestWorlds.started(hostEvents);
            GameStart guestStart = TestWorlds.started(guestEvents);
            assertThat(guestStart.map()).isEqualTo(hostStart.map());
            assertThat(hostStart.map().countries().stream().filter(country -> country.player()))
                    .hasSize(2);
        }
    }

    @Test
    void takenNicknameFailsTheJoin() throws Exception {
        try (ServerConnection host = connect(SessionListener.NONE);
                ServerConnection guest = connect(SessionListener.NONE)) {
            long session = await(host.createLobby("Оля", 7, NpcShare.FEW)).session();

            assertThatThrownBy(() -> await(guest.joinLobby(session, "ОЛЯ")))
                    .isInstanceOfSatisfying(
                            ServerErrorException.class,
                            e -> assertThat(e.error().code()).isEqualTo(ErrorCode.NICKNAME_TAKEN));
            assertThat(guest.isOpen()).isTrue();
        }
    }

    @Test
    void playerRejoinsAfterTheConnectionDrops() throws Exception {
        RecordingListener hostEvents = new RecordingListener();
        try (ServerConnection host = connect(hostEvents)) {
            long session = await(host.createLobby("Оля", 8, NpcShare.FEW)).session();
            ServerConnection guest = connect(SessionListener.NONE);
            ServerMessage.Joined joined = await(guest.joinLobby(session, "Ігор"));
            host.startGame();
            TestWorlds.started(hostEvents);
            guest.close();
            // Хост бачить гостя не на зв'язку.
            while (hostEvents.next(RecordingListener.Players.class).players().stream()
                    .allMatch(PlayerInfo::connected)) {
                Thread.onSpinWait();
            }

            RecordingListener backEvents = new RecordingListener();
            try (ServerConnection back = connect(backEvents)) {
                assertThat(await(back.rejoin(joined))).isEqualTo(joined);

                GameStart start = TestWorlds.started(backEvents);
                assertThat(start.phase()).isEqualTo(YearPhase.ORDERS);
                assertThat(start.map().seed()).isEqualTo(8);
            }
        }
    }

    @Test
    void wrongTokenIsUnauthorized() {
        try (ServerConnection host = connect(SessionListener.NONE);
                ServerConnection other = connect(SessionListener.NONE)) {
            ServerMessage.Joined joined = await(host.createLobby("Оля", 9, NpcShare.FEW));

            assertThatThrownBy(() -> await(other.rejoin(
                            new ServerMessage.Joined(joined.session(), joined.world(), 1, "0".repeat(64)))))
                    .isInstanceOfSatisfying(
                            ServerErrorException.class,
                            e -> assertThat(e.error().code()).isEqualTo(ErrorCode.UNAUTHORIZED));
        }
    }

    @Test
    void otherContentIsRejectedByTheServer() {
        CompletableFuture<ServerConnection> connecting =
                ServerConnection.connect(SERVER.address(), "0".repeat(64), SessionListener.NONE);

        assertThatThrownBy(() -> await(connecting)).isInstanceOfSatisfying(ServerErrorException.class, e -> {
            assertThat(e.error().code()).isEqualTo(ErrorCode.VERSION_MISMATCH);
            assertThat(e.error().details()).containsEntry("part", Handshake.CONTENT);
        });
    }

    @Test
    void noServerAtAddress() {
        CompletableFuture<ServerConnection> connecting =
                ServerConnection.connect(new LocalAddress("kolo-nobody"), SERVER.contentHash(), SessionListener.NONE);

        assertThatThrownBy(() -> await(connecting)).isInstanceOf(ConnectionClosedException.class);
    }

    @Test
    void stoppedServerFailsRequestsAndTellsTheListener() throws Exception {
        EmbeddedServer server = EmbeddedServer.startWithBundledContent(worlds);
        RecordingListener events = new RecordingListener();
        ServerConnection connection = await(ServerConnection.connect(server.address(), server.contentHash(), events));

        server.close();

        assertThat(events.next()).isEqualTo(new RecordingListener.Disconnected());
        assertThatThrownBy(() -> await(connection.lobbies())).isInstanceOf(ConnectionClosedException.class);
        connection.close();
        assertThat(connection.isOpen()).isFalse();
    }

    @Test
    void closedConnectionFailsRequests() {
        ServerConnection connection = connect(SessionListener.NONE);
        connection.close();

        assertThatThrownBy(() -> await(connection.createLobby("Оля", 1, NpcShare.FEW)))
                .isInstanceOf(ConnectionClosedException.class);
    }

    private static ServerConnection connect(SessionListener listener) {
        return await(ServerConnection.connect(SERVER.address(), SERVER.contentHash(), listener));
    }
}
