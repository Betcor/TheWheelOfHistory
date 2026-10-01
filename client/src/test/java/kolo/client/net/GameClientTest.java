package kolo.client.net;

import static kolo.client.TestWorlds.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import kolo.client.TestWorlds;
import kolo.engine.state.NpcShare;
import kolo.protocol.message.LobbyInfo;
import kolo.protocol.message.PlayerInfo;
import kolo.protocol.message.ServerMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Гра клієнта: лобі на вбудованому сервері, LAN-хост і гість через TCP, повернення після розриву. */
@Timeout(90)
class GameClientTest {

    @TempDir
    Path worlds;

    private final ExecutorService background = Executors.newSingleThreadExecutor();

    @AfterEach
    void stop() {
        background.shutdownNow();
    }

    @Test
    void soloLobbyStartsTheGame() throws Exception {
        RecordingListener events = new RecordingListener();
        try (GameClient game = GameClient.start(worlds, 0, events, background)) {
            ServerMessage.Joined joined = await(game.hostLobby("Оля", 11, NpcShare.FEW, false));

            assertThat(game.credentials()).contains(joined);
            assertThat(game.lanAddress()).isEmpty();
            PlayerInfo me =
                    events.next(RecordingListener.Lobby.class).lobby().players().getFirst();
            assertThat(game.isMe(me)).isTrue();
            game.startGame();
            assertThat(TestWorlds.started(events).map().seed()).isEqualTo(11);
            game.ready(0);
            events.awaitOrders(1);

            game.leave();
            assertThat(game.credentials()).isEmpty();
        }
    }

    @Test
    void lanGuestJoinsAndBothPlay() throws Exception {
        RecordingListener hostEvents = new RecordingListener();
        RecordingListener guestEvents = new RecordingListener();
        try (GameClient host = GameClient.start(worlds, 0, hostEvents, background);
                GameClient guest = GameClient.start(worlds.resolve("guest"), 0, guestEvents, background)) {
            long session = await(host.hostLobby("Оля", 12, NpcShare.FEW, true)).session();
            InetSocketAddress lan = new InetSocketAddress(
                    InetAddress.getLoopbackAddress(),
                    host.lanAddress().orElseThrow().getPort());

            List<LobbyInfo> lobbies = await(guest.findLobbies(lan));
            assertThat(lobbies).extracting(LobbyInfo::session).containsExactly(session);
            await(guest.joinLobby(session, "Ігор"));
            host.startGame();

            GameStart hostStart = TestWorlds.started(hostEvents);
            assertThat(TestWorlds.started(guestEvents).map()).isEqualTo(hostStart.map());
            host.ready(0);
            guest.ready(0);
            hostEvents.awaitOrders(1);
            guestEvents.awaitOrders(1);
        }
    }

    @Test
    void reconnectReturnsToTheSameCountry() throws Exception {
        RecordingListener hostEvents = new RecordingListener();
        RecordingListener guestEvents = new RecordingListener();
        try (GameClient host = GameClient.start(worlds, 0, hostEvents, background);
                GameClient guest = GameClient.start(worlds.resolve("guest"), 0, guestEvents, background)) {
            long session = await(host.hostLobby("Оля", 13, NpcShare.FEW, true)).session();
            InetSocketAddress lan = new InetSocketAddress(
                    InetAddress.getLoopbackAddress(),
                    host.lanAddress().orElseThrow().getPort());
            await(guest.findLobbies(lan));
            ServerMessage.Joined joined = await(guest.joinLobby(session, "Ігор"));
            host.startGame();
            TestWorlds.started(guestEvents);

            assertThat(await(guest.reconnect())).isEqualTo(joined);

            // Події старого з'єднання (його закрито) до слухача вже не доходять: далі — карта з нового.
            GameStart back = guestEvents.next(RecordingListener.Started.class).start();
            assertThat(back.map().seed()).isEqualTo(13);
            assertThat(guestEvents.next(RecordingListener.Players.class).players())
                    .extracting(PlayerInfo::connected)
                    .containsExactly(true, true);
        }
    }

    @Test
    void joinWithoutConnectionFails() {
        try (GameClient game = GameClient.start(worlds, 0, SessionListener.NONE, background)) {
            assertThatThrownBy(() -> await(game.joinLobby(1, "Оля"))).isInstanceOf(ConnectionClosedException.class);
            assertThatThrownBy(() -> await(game.reconnect())).isInstanceOf(ConnectionClosedException.class);
        }
    }

    @Test
    void busyLanPortIsReported() throws Exception {
        try (GameClient first = GameClient.start(worlds, 0, SessionListener.NONE, background)) {
            await(first.hostLobby("Оля", 14, NpcShare.FEW, true));
            int port = first.lanAddress().orElseThrow().getPort();
            try (GameClient second =
                    GameClient.start(worlds.resolve("second"), port, SessionListener.NONE, background)) {
                assertThatThrownBy(() -> await(second.hostLobby("Ігор", 15, NpcShare.FEW, true)))
                        .isInstanceOfSatisfying(
                                LanUnavailableException.class,
                                e -> assertThat(e.port()).isEqualTo(port));
            }
        }
    }
}
