package kolo.server.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import kolo.engine.error.ErrorCode;
import kolo.engine.state.NpcShare;
import kolo.engine.view.MapView;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.Handshake;
import kolo.protocol.message.LobbyInfo;
import kolo.protocol.message.PlayerToken;
import kolo.protocol.message.ServerMessage;
import kolo.protocol.message.WorldInfo;
import kolo.protocol.message.YearPhase;
import kolo.server.TestClient;
import kolo.server.TestServers;
import kolo.server.persistence.SavedPlayer;
import kolo.server.persistence.WorldDirectory;
import kolo.server.persistence.WorldStore;
import kolo.server.session.SessionActor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Сервер через справжні транспорти — {@code LocalChannel} і TCP на петлі — з вбудованим контентом. */
@Timeout(60)
class GameServerTest {

    private static final String HASH = TestServers.CONTENT.hash();
    private static final InetSocketAddress ANY_LOOPBACK = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);

    @TempDir
    Path worlds;

    private GameServer server;

    @BeforeEach
    void start() {
        server = GameServer.start(() -> TestServers.CONTENT, new WorldDirectory(worlds));
    }

    @AfterEach
    void close() {
        server.close();
    }

    @Test
    void worldOverLocalChannel() throws Exception {
        try (TestClient client = TestClient.welcomed(server.bindLocal(), HASH)) {
            assertThat(client.solo(5, NpcShare.FEW)).isEqualTo(TestServers.map(5, 1, NpcShare.FEW));
        }
    }

    @Test
    void yearsOverLocalChannelAreSaved() throws Exception {
        try (TestClient client = TestClient.welcomed(server.bindLocal(), HASH)) {
            client.solo(6, NpcShare.FEW);

            for (int turn = 0; turn < 5; turn++) {
                client.endYear(turn);
            }
            client.send(new ClientMessage.Ready(2));
            assertThat(((ServerMessage.Error) client.next()).code()).isEqualTo(ErrorCode.PHASE_CLOSED);
        }
        awaitNoSessions();
        try (WorldStore store = WorldStore.open(worlds.resolve("world-6" + WorldStore.EXTENSION))) {
            assertThat(store.lastTurn()).isEqualTo(5);
        }
    }

    @Test
    void worldOverTcp() throws Exception {
        InetSocketAddress address = server.bindTcp(ANY_LOOPBACK);
        assertThat(address.getPort()).isPositive();

        try (TestClient client = TestClient.welcomed(address, HASH)) {
            assertThat(client.solo(5, NpcShare.FEW)).isEqualTo(TestServers.map(5, 1, NpcShare.FEW));
            client.endYear(0);
        }
    }

    @Test
    void bothTransportsAtOnce() throws Exception {
        try (TestClient local = TestClient.welcomed(server.bindLocal(), HASH);
                TestClient tcp = TestClient.welcomed(server.bindTcp(ANY_LOOPBACK), HASH)) {
            assertThat(local.solo(8, NpcShare.FEW)).isEqualTo(TestServers.map(8, 1, NpcShare.FEW));
            assertThat(tcp.solo(9, NpcShare.FEW)).isEqualTo(TestServers.map(9, 1, NpcShare.FEW));
            assertThat(server.sessions().active()).hasSize(2);
        }
    }

    @Test
    void severalClientsGetTheirOwnWorlds() throws Exception {
        InetSocketAddress address = server.bindTcp(ANY_LOOPBACK);
        ExecutorService clients = Executors.newFixedThreadPool(3);
        try {
            List<Future<MapView>> maps = new ArrayList<>();
            for (long seed = 20; seed < 23; seed++) {
                long chosen = seed;
                Callable<MapView> request = () -> {
                    try (TestClient client = TestClient.welcomed(address, HASH)) {
                        MapView map = client.solo(chosen, NpcShare.FEW);
                        client.endYear(0);
                        return map;
                    }
                };
                maps.add(clients.submit(request));
            }
            for (int n = 0; n < maps.size(); n++) {
                assertThat(maps.get(n).get()).isEqualTo(TestServers.map(20 + n, 1, NpcShare.FEW));
            }
        } finally {
            clients.shutdownNow();
        }
    }

    @Test
    void threePlayersOverTcpPlayFiveYears() throws Exception {
        InetSocketAddress address = server.bindTcp(ANY_LOOPBACK);
        try (TestClient host = TestClient.welcomed(address, HASH);
                TestClient second = TestClient.welcomed(address, HASH);
                TestClient third = TestClient.welcomed(address, HASH)) {
            long session = host.createLobby("Оля", 30, NpcShare.FEW).session();
            List<TestClient> guests = List.of(second, third);
            second.send(new ClientMessage.ListLobbies());
            assertThat(second.next(ServerMessage.Lobbies.class).lobbies())
                    .extracting(lobby -> lobby.session())
                    .containsExactly(session);
            for (int n = 0; n < guests.size(); n++) {
                guests.get(n).send(new ClientMessage.JoinLobby(session, "Гість " + (n + 1)));
                guests.get(n).next(ServerMessage.Joined.class);
            }
            // Кожен бачить лобі після кожного приєднання: хост — двічі, другий — двічі, третій — раз.
            awaitLobbyOf(host, 3);
            awaitLobbyOf(second, 3);
            awaitLobbyOf(third, 3);

            host.send(new ClientMessage.StartGame());

            MapView map = TestServers.map(30, 3, NpcShare.FEW);
            List<TestClient> players = List.of(host, second, third);
            for (TestClient player : players) {
                assertThat(player.world()).isEqualTo(map);
            }
            TestClient.passGeneration(host, second, third);
            for (int turn = 0; turn < 4; turn++) {
                for (TestClient player : players) {
                    player.send(new ClientMessage.Ready(turn));
                }
                for (TestClient player : players) {
                    // «Готово» перших двох — оновлення списку гравців; потім фази року.
                    player.next(ServerMessage.Players.class);
                    player.next(ServerMessage.Players.class);
                    player.expectYearAfterReady(turn);
                }
            }
            // П'ятий рік хост завершує сам: за всіх, хто не натиснув «Готово», діє автопілот.
            host.send(new ClientMessage.EndYear(4));
            for (TestClient player : players) {
                player.expectYearAfterReady(4);
            }
        }
        awaitNoSessions();
        try (WorldStore store = WorldStore.open(worlds.resolve("world-30" + WorldStore.EXTENSION))) {
            assertThat(store.lastTurn()).isEqualTo(5);
            assertThat(store.players()).extracting(SavedPlayer::missedTurns).containsExactly(1, 1, 1);
        }
    }

    @Test
    void playerRejoinsOverTcpAfterADroppedConnection() throws Exception {
        InetSocketAddress address = server.bindTcp(ANY_LOOPBACK);
        try (TestClient host = TestClient.welcomed(address, HASH)) {
            long session = host.createLobby("Оля", 31, NpcShare.FEW).session();
            ServerMessage.Joined joined;
            try (TestClient guest = TestClient.welcomed(address, HASH)) {
                guest.send(new ClientMessage.JoinLobby(session, "Ігор"));
                joined = guest.next(ServerMessage.Joined.class);
                awaitLobbyOf(host, 2);
                host.send(new ClientMessage.StartGame());
                host.world();
                guest.next(ServerMessage.Lobby.class);
                guest.world();
                TestClient.passGeneration(host, guest);
            }
            // Гість обірвав з'єднання — хост бачить його не на зв'язку.
            assertThat(host.next(ServerMessage.Players.class).players())
                    .extracting(p -> p.connected())
                    .containsExactly(true, false);

            try (TestClient back = TestClient.welcomed(address, HASH)) {
                back.send(new ClientMessage.Rejoin(session, joined.player(), joined.token()));

                assertThat(back.next(ServerMessage.Joined.class)).isEqualTo(joined);
                assertThat(back.map()).isEqualTo(TestServers.map(31, 2, NpcShare.FEW));
                assertThat(back.card().number()).isEqualTo(1);
                assertThat(back.phase()).isEqualTo(new ServerMessage.Phase(0, YearPhase.ORDERS));
                back.next(ServerMessage.Players.class);
                host.next(ServerMessage.Players.class);
                host.send(new ClientMessage.Ready(0));
                back.send(new ClientMessage.Ready(0));
                back.next(ServerMessage.Players.class);
                back.expectYearAfterReady(0);
            }
        }
    }

    @Test
    void savedWorldMovesFromTheLanHostToAnotherServer(@TempDir Path other) throws Exception {
        // LAN: хост через LocalChannel, гість по TCP; рік — і всі пішли.
        InetSocketAddress lan = server.bindTcp(ANY_LOOPBACK);
        ServerMessage.Joined olya;
        ServerMessage.Joined ihor;
        try (TestClient host = TestClient.welcomed(server.bindLocal(), HASH);
                TestClient guest = TestClient.welcomed(lan, HASH)) {
            olya = host.createLobby("Оля", 40, NpcShare.FEW);
            guest.send(new ClientMessage.JoinLobby(olya.session(), "Ігор"));
            ihor = guest.next(ServerMessage.Joined.class);
            awaitLobbyOf(host, 2);
            guest.next(ServerMessage.Lobby.class);
            host.send(new ClientMessage.StartGame());
            host.world();
            guest.world();
            TestClient.passGeneration(host, guest);
            host.send(new ClientMessage.Ready(0));
            guest.send(new ClientMessage.Ready(0));
            for (TestClient player : List.of(host, guest)) {
                player.next(ServerMessage.Players.class);
                player.expectYearAfterReady(0);
            }
        }
        awaitNoSessions();
        Path file = worlds.resolve("world-40" + WorldStore.EXTENSION);
        Files.copy(file, other.resolve(file.getFileName()));

        try (GameServer dedicated = GameServer.start(() -> TestServers.CONTENT, new WorldDirectory(other))) {
            InetSocketAddress address = dedicated.bindTcp(ANY_LOOPBACK);
            try (TestClient stranger = TestClient.welcomed(address, HASH);
                    TestClient host = TestClient.welcomed(address, HASH);
                    TestClient guest = TestClient.welcomed(address, HASH)) {
                host.send(new ClientMessage.ListWorlds());
                WorldInfo world = host.next(ServerMessage.Worlds.class).worlds().getFirst();
                assertThat(world.key()).contains(olya.world());
                assertThat(world.turn()).isEqualTo(1);
                // Без токена з мережі світу не відкрити.
                stranger.send(new ClientMessage.LoadWorld(world.name(), "Чужий", Optional.empty()));
                assertThat(stranger.next(ServerMessage.Error.class).code()).isEqualTo(ErrorCode.UNAUTHORIZED);

                host.send(new ClientMessage.LoadWorld(
                        world.name(), "Оля", Optional.of(new PlayerToken(olya.player(), olya.token()))));
                ServerMessage.Joined hostJoined = host.next(ServerMessage.Joined.class);
                assertThat(hostJoined.player()).isEqualTo(olya.player());
                host.next(ServerMessage.Lobby.class);
                guest.send(new ClientMessage.ListLobbies());
                LobbyInfo lobby =
                        guest.next(ServerMessage.Lobbies.class).lobbies().getFirst();
                assertThat(lobby.world()).isEqualTo(ihor.world());
                guest.send(new ClientMessage.Rejoin(lobby.session(), ihor.player(), ihor.token()));
                guest.next(ServerMessage.Joined.class);
                guest.next(ServerMessage.Lobby.class);
                host.next(ServerMessage.Lobby.class);

                host.send(new ClientMessage.StartGame());

                for (TestClient player : List.of(host, guest)) {
                    assertThat(player.map()).isEqualTo(TestServers.map(40, 2, NpcShare.FEW));
                    // Завантажений світ — без генерації: картка й одразу рік.
                    player.card();
                    player.next(ServerMessage.Players.class);
                    assertThat(player.phase()).isEqualTo(new ServerMessage.Phase(1, YearPhase.START_OF_YEAR));
                    assertThat(player.phase()).isEqualTo(new ServerMessage.Phase(1, YearPhase.ORDERS));
                }
                host.send(new ClientMessage.Ready(1));
                guest.send(new ClientMessage.Ready(1));
                for (TestClient player : List.of(host, guest)) {
                    player.next(ServerMessage.Players.class);
                    player.expectYearAfterReady(1);
                }
            }
        }
        try (WorldStore moved = WorldStore.open(other.resolve(file.getFileName()))) {
            assertThat(moved.lastTurn()).isEqualTo(2);
            assertThat(moved.meta().key()).isEqualTo(olya.world());
        }
        try (WorldStore original = WorldStore.open(file)) {
            assertThat(original.lastTurn()).isEqualTo(1);
        }
    }

    /** Пропускає оновлення лобі, доки в ньому не буде {@code players} гравців. */
    private static void awaitLobbyOf(TestClient client, int players) throws InterruptedException {
        while (client.next(ServerMessage.Lobby.class).players().size() < players) {
            Thread.onSpinWait();
        }
    }

    @Test
    void mismatchedClientIsToldWhyAndDisconnected() throws Exception {
        try (TestClient client = TestClient.connect(server.bindTcp(ANY_LOOPBACK))) {
            client.send(Handshake.hello("d".repeat(64)));

            ServerMessage.Error error = (ServerMessage.Error) client.next();
            assertThat(error.code()).isEqualTo(ErrorCode.VERSION_MISMATCH);
            assertThat(client.awaitClosed()).isTrue();
        }
    }

    @Test
    void closeDisconnectsClients() throws Exception {
        try (TestClient client = TestClient.welcomed(server.bindLocal(), HASH)) {
            server.close();

            assertThat(client.awaitClosed()).isTrue();
        }
        assertThatThrownBy(server::bindLocal).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void closeClosesSessionsAndTheirFiles() throws Exception {
        try (TestClient client = TestClient.welcomed(server.bindLocal(), HASH)) {
            client.solo(7, NpcShare.FEW);
            client.endYear(0);

            server.close();

            assertThat(server.sessions().active()).isEmpty();
        }
        try (WorldStore store = WorldStore.open(worlds.resolve("world-7" + WorldStore.EXTENSION))) {
            assertThat(store.lastTurn()).isEqualTo(1);
        }
    }

    @Test
    void disconnectClosesTheSession() throws Exception {
        try (TestClient client = TestClient.welcomed(server.bindLocal(), HASH)) {
            client.solo(7, NpcShare.FEW);
            assertThat(server.sessions().active()).hasSize(1);
        }
        awaitNoSessions();
    }

    /** З'єднання закрито — сесія закривається у своєму потоці трохи згодом. */
    private void awaitNoSessions() throws InterruptedException {
        for (SessionActor session : server.sessions().active()) {
            assertThat(session.awaitClosed(30, TimeUnit.SECONDS)).isTrue();
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!server.sessions().active().isEmpty() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(server.sessions().active()).isEmpty();
    }

    @Test
    void busyPortIsAnError() {
        InetSocketAddress taken = server.bindTcp(ANY_LOOPBACK);

        try (GameServer other = GameServer.start(() -> TestServers.CONTENT, new WorldDirectory(worlds))) {
            assertThatThrownBy(() -> other.bindTcp(taken)).isInstanceOf(Exception.class);
        }
    }

    @Test
    void eachLocalBindIsANewAddress() {
        assertThat(server.bindLocal()).isNotEqualTo(server.bindLocal());
    }
}
