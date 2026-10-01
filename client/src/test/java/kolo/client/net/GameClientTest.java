package kolo.client.net;

import static kolo.client.TestWorlds.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import kolo.client.TestWorlds;
import kolo.engine.state.NpcShare;
import kolo.engine.state.TurnTimer;
import kolo.protocol.Protocol;
import kolo.protocol.discovery.DiscoveryPacket;
import kolo.protocol.discovery.DiscoveryPackets;
import kolo.protocol.message.LobbyInfo;
import kolo.protocol.message.LobbySetup;
import kolo.protocol.message.PlayerInfo;
import kolo.protocol.message.PlayerToken;
import kolo.protocol.message.ServerMessage;
import kolo.protocol.message.WorldInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Гра клієнта: лобі на вбудованому сервері, LAN-хост і гість через TCP, повернення після розриву, hot-seat. */
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
        try (GameClient game = GameClient.start(worlds, LanPorts.ANY, events, background)) {
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
        try (GameClient host = GameClient.start(worlds, LanPorts.ANY, hostEvents, background);
                GameClient guest = GameClient.start(worlds.resolve("guest"), LanPorts.ANY, guestEvents, background)) {
            long session = await(host.hostLobby("Оля", 12, NpcShare.FEW, true)).session();
            InetSocketAddress lan = new InetSocketAddress(
                    InetAddress.getLoopbackAddress(),
                    host.lanAddress().orElseThrow().game().getPort());

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
    void lanGuestFindsHostBySearchAndJoins() throws Exception {
        RecordingListener hostEvents = new RecordingListener();
        try (GameClient host = GameClient.start(worlds, LanPorts.ANY, hostEvents, background);
                GameClient guest =
                        GameClient.start(worlds.resolve("guest"), LanPorts.ANY, SessionListener.NONE, background)) {
            long session = await(host.hostLobby("Оля", 16, NpcShare.FEW, true)).session();
            LanAddresses lan = host.lanAddress().orElseThrow();
            LanSearch search = new LanSearch(lan.discovery().orElseThrow().getPort(), Duration.ofMillis(500));

            LanLobbies found = await(guest.findLanLobbies(search));

            assertThat(found.incompatible()).isZero();
            RemoteLobby lobby = found.lobbies().getFirst();
            assertThat(found.lobbies()).hasSize(1);
            assertThat(lobby.lobby().session()).isEqualTo(session);
            assertThat(lobby.server().getPort()).isEqualTo(lan.game().getPort());
            assertThat(lobby.server().getAddress().isLoopbackAddress()).isTrue();

            ServerMessage.Joined joined = await(guest.joinLobby(lobby, "Ігор"));

            assertThat(joined.session()).isEqualTo(session);
            // Перше лобі хоста — лише він сам; друге — з гостем.
            assertThat(hostEvents.next(RecordingListener.Lobby.class).lobby().players())
                    .hasSize(1);
            assertThat(hostEvents.next(RecordingListener.Lobby.class).lobby().players())
                    .hasSize(2);
        }
    }

    @Test
    void lanSearchCountsIncompatibleGamesAndSkipsGoneOnes() throws Exception {
        try (FakeResponder other = new FakeResponder(List.of(
                        DiscoveryPackets.encode(new DiscoveryPacket.Reply(Protocol.VERSION + 1, 4000)),
                        // Гра відповіла на пошук, але вже не слухає — пропускається.
                        DiscoveryPackets.encode(new DiscoveryPacket.Reply(Protocol.VERSION, freePort()))));
                GameClient guest = GameClient.start(worlds, LanPorts.ANY, SessionListener.NONE, background)) {
            LanLobbies found = await(guest.findLanLobbies(new LanSearch(other.port(), Duration.ofMillis(300))));

            assertThat(found).isEqualTo(new LanLobbies(List.of(), 1));
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    @Test
    void reconnectReturnsToTheSameCountry() throws Exception {
        RecordingListener hostEvents = new RecordingListener();
        RecordingListener guestEvents = new RecordingListener();
        try (GameClient host = GameClient.start(worlds, LanPorts.ANY, hostEvents, background);
                GameClient guest = GameClient.start(worlds.resolve("guest"), LanPorts.ANY, guestEvents, background)) {
            long session = await(host.hostLobby("Оля", 13, NpcShare.FEW, true)).session();
            InetSocketAddress lan = new InetSocketAddress(
                    InetAddress.getLoopbackAddress(),
                    host.lanAddress().orElseThrow().game().getPort());
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
    void savedWorldIsLoadedOnTheSeatOfTheStoredToken() throws Exception {
        RecordingListener events = new RecordingListener();
        try (GameClient game = GameClient.start(worlds, LanPorts.ANY, events, background)) {
            ServerMessage.Joined first = playOneYearAndLeave(game, events, 16);
            WorldInfo world = savedWorld(game);
            assertThat(world.key()).contains(first.world());

            ServerMessage.Joined joined = await(game.loadLocalWorld(world, "Інший", false));

            assertThat(joined.player()).isEqualTo(first.player());
            assertThat(joined.token()).isEqualTo(first.token());
            assertThat(events.next(RecordingListener.Lobby.class).lobby().setup())
                    .isEqualTo(new LobbySetup.SavedWorld(world.name(), 16, 1, TurnTimer.MANUAL));
            game.startGame();
            GameStart resumed = TestWorlds.started(events);
            assertThat(resumed.turn()).isEqualTo(1);
            assertThat(resumed.map().seed()).isEqualTo(16);
        }
    }

    @Test
    void worldCopiedToAnotherComputerIsLoadedByAGuestWhoTakesTheSeat(@TempDir Path other) throws Exception {
        WorldInfo world;
        RecordingListener firstEvents = new RecordingListener();
        try (GameClient first = GameClient.start(worlds, LanPorts.ANY, firstEvents, background)) {
            playOneYearAndLeave(first, firstEvents, 17);
            world = savedWorld(first);
        }
        Path copy = Files.createDirectories(other.resolve("worlds"));
        try (Stream<Path> files = Files.list(worlds.resolve("worlds"))) {
            for (Path file : files.toList()) {
                Files.copy(file, copy.resolve(file.getFileName()));
            }
        }
        RecordingListener events = new RecordingListener();
        try (GameClient game = GameClient.start(other, LanPorts.ANY, events, background)) {
            ServerMessage.Joined guest = await(game.loadLocalWorld(world, "Марко", false));
            assertThat(guest.player()).isEqualTo(2);
            events.next(RecordingListener.Lobby.class);

            game.assignSeat(guest.player(), 1);

            ServerMessage.Joined seated =
                    events.next(RecordingListener.Joined.class).joined();
            assertThat(seated.player()).isEqualTo(1);
            assertThat(game.credentials()).contains(seated);
            assertThat(new TokenStore(other.resolve(TokenStore.FILE_NAME)).find(seated.world()))
                    .contains(new PlayerToken(1, seated.token()));
            PlayerInfo me =
                    events.next(RecordingListener.Lobby.class).lobby().players().getFirst();
            assertThat(me.nickname()).isEqualTo("Марко");
            assertThat(game.isMe(me)).isTrue();
            game.startGame();
            assertThat(TestWorlds.started(events).turn()).isEqualTo(1);
        }
    }

    @Test
    void returningPlayerJoinsTheLoadedLobbyOnTheirSeat() throws Exception {
        RecordingListener hostEvents = new RecordingListener();
        RecordingListener guestEvents = new RecordingListener();
        try (GameClient host = GameClient.start(worlds, LanPorts.ANY, hostEvents, background);
                GameClient guest = GameClient.start(worlds.resolve("guest"), LanPorts.ANY, guestEvents, background)) {
            long session = await(host.hostLobby("Оля", 18, NpcShare.FEW, true)).session();
            InetSocketAddress lan = new InetSocketAddress(
                    InetAddress.getLoopbackAddress(),
                    host.lanAddress().orElseThrow().game().getPort());
            await(guest.findLobbies(lan));
            ServerMessage.Joined ihor = await(guest.joinLobby(session, "Ігор"));
            host.startGame();
            TestWorlds.started(hostEvents);
            TestWorlds.started(guestEvents);
            guest.leave();
            host.leave();
            WorldInfo world = savedWorld(host);
            await(host.loadLocalWorld(world, "Оля", true));

            LobbyInfo lobby = await(guest.findLobbies(lan)).getFirst();
            ServerMessage.Joined back = await(guest.joinLobby(lobby, "Хтось"));

            assertThat(back.player()).isEqualTo(ihor.player());
            assertThat(back.token()).isEqualTo(ihor.token());
        }
    }

    @Test
    void hotSeatPlayersTakeTurnsOnOneComputer() throws Exception {
        RecordingListener events = new RecordingListener();
        try (GameClient game = GameClient.start(worlds, LanPorts.ANY, events, background)) {
            ServerMessage.Joined olia = await(game.hostLobby("Оля", 19, NpcShare.FEW, false));
            ServerMessage.Lobby lobby =
                    events.next(RecordingListener.Lobby.class).lobby();
            TurnTimer timed =
                    lobby.timers().stream().filter(TurnTimer::timed).findFirst().orElseThrow();
            game.setTimer(timed);
            awaitLobby(events, l -> l.setup().timer().equals(timed));
            assertThat(game.canAddLocalPlayers()).isTrue();
            assertThat(game.hotSeat()).isFalse();

            ServerMessage.Joined ihor = await(game.addLocalPlayer("Ігор"));

            assertThat(ihor.session()).isEqualTo(olia.session());
            assertThat(game.hotSeat()).isTrue();
            assertThat(game.localPlayers()).containsExactly(olia.player(), ihor.player());
            // Hot-seat — без таймера: гра клієнта скинула таймер хоста.
            ServerMessage.Lobby both = awaitLobby(
                    events, l -> l.players().size() == 2 && !l.setup().timer().timed());
            assertThat(both.players()).allMatch(game::isLocal);
            assertThat(game.credentials()).contains(olia);
            assertThat(new TokenStore(worlds.resolve(TokenStore.FILE_NAME)).find(olia.world()))
                    .containsExactly(
                            new PlayerToken(olia.player(), olia.token()), new PlayerToken(ihor.player(), ihor.token()));

            game.startGame();
            assertThat(TestWorlds.started(events).turn()).isZero();
            assertThat(game.handoffAtYearStart()).isEmpty();
            game.ready(0);
            assertThat(game.handoffAfterReady()).hasValue(ihor.player());

            // Комп'ютер передали Ігореві: слухач бачить його місце, рік ще не розв'язано.
            assertThat(game.showPlayer(ihor.player())).isTrue();
            assertThat(events.next(RecordingListener.Started.class).start().turn())
                    .isZero();
            assertThat(game.credentials()).contains(ihor);
            assertThat(game.handoffAfterReady()).isEmpty();
            game.ready(0);
            events.awaitOrders(1);

            // Новий рік — знову першому в черзі.
            assertThat(game.handoffAtYearStart()).hasValue(olia.player());
            assertThat(game.showPlayer(olia.player())).isTrue();
            GameStart back = events.next(RecordingListener.Started.class).start();
            if (back.turn() < 1) {
                // Місце Олі ще не отримало нового року — отримає слідом.
                events.awaitOrders(1);
            }
            assertThat(game.credentials()).contains(olia);
            assertThat(game.handoffAtYearStart()).isEmpty();

            game.leave();
            assertThat(game.hotSeat()).isFalse();
            assertThat(game.localPlayers()).isEmpty();
            assertThat(game.showPlayer(ihor.player())).isFalse();
        }
    }

    @Test
    void savedHotSeatWorldSeatsEveryPlayerOfThisComputer() throws Exception {
        RecordingListener events = new RecordingListener();
        try (GameClient game = GameClient.start(worlds, LanPorts.ANY, events, background)) {
            ServerMessage.Joined olia = await(game.hostLobby("Оля", 20, NpcShare.FEW, false));
            ServerMessage.Joined ihor = await(game.addLocalPlayer("Ігор"));
            game.startGame();
            TestWorlds.started(events);
            game.leave();
            WorldInfo world = savedWorld(game);

            ServerMessage.Joined back = await(game.loadLocalWorld(world, "Хтось", false));

            assertThat(back.player()).isEqualTo(olia.player());
            assertThat(game.localPlayers()).containsExactly(olia.player(), ihor.player());
            ServerMessage.Lobby lobby =
                    awaitLobby(events, l -> l.players().stream().allMatch(PlayerInfo::connected));
            assertThat(lobby.players()).extracting(PlayerInfo::nickname).containsExactlyInAnyOrder("Оля", "Ігор");
            game.startGame();
            assertThat(TestWorlds.started(events).turn()).isZero();
            game.leave();

            // Відкрита для мережі гра — кожен за своїм комп'ютером: сідає лише перший, інші місця вільні.
            await(game.loadLocalWorld(savedWorld(game), "Хтось", true));
            assertThat(game.localPlayers()).containsExactly(olia.player());
            assertThat(game.canAddLocalPlayers()).isFalse();
            assertThatThrownBy(() -> await(game.addLocalPlayer("Марко"))).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void hostRemovesAPlayerOfThisComputerFromTheLobby() throws Exception {
        RecordingListener events = new RecordingListener();
        try (GameClient game = GameClient.start(worlds, LanPorts.ANY, events, background)) {
            ServerMessage.Joined olia = await(game.hostLobby("Оля", 21, NpcShare.FEW, false));
            ServerMessage.Joined ihor = await(game.addLocalPlayer("Ігор"));
            awaitLobby(events, l -> l.players().size() == 2);

            game.removeLocalPlayer(ihor.player());

            assertThat(game.hotSeat()).isFalse();
            assertThat(game.localPlayers()).containsExactly(olia.player());
            awaitLobby(events, l -> l.players().size() == 1);
            assertThat(new TokenStore(worlds.resolve(TokenStore.FILE_NAME)).find(olia.world()))
                    .containsExactly(new PlayerToken(olia.player(), olia.token()));
        }
    }

    /** Пропускає стани лобі, доки не прийде такий. */
    private static ServerMessage.Lobby awaitLobby(
            RecordingListener events, java.util.function.Predicate<ServerMessage.Lobby> wanted)
            throws InterruptedException {
        while (true) {
            ServerMessage.Lobby lobby =
                    events.next(RecordingListener.Lobby.class).lobby();
            if (wanted.test(lobby)) {
                return lobby;
            }
        }
    }

    /** Одиночна гра: лобі, рік 0 і вихід; файл світу лишається в теці. */
    private static ServerMessage.Joined playOneYearAndLeave(GameClient game, RecordingListener events, long seed)
            throws Exception {
        ServerMessage.Joined joined = await(game.hostLobby("Оля", seed, NpcShare.FEW, false));
        game.startGame();
        TestWorlds.started(events);
        game.ready(0);
        events.awaitOrders(1);
        game.leave();
        return joined;
    }

    /** Єдиний збережений світ теки, щойно сесія його відпустить. */
    private static WorldInfo savedWorld(GameClient game) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            List<WorldInfo> worlds = await(game.localWorlds());
            if (!worlds.isEmpty()) {
                return worlds.getFirst();
            }
            Thread.sleep(50);
        }
        throw new AssertionError("світ не з'явився в списку");
    }

    @Test
    void joinWithoutConnectionFails() {
        try (GameClient game = GameClient.start(worlds, LanPorts.ANY, SessionListener.NONE, background)) {
            assertThatThrownBy(() -> await(game.joinLobby(1, "Оля"))).isInstanceOf(ConnectionClosedException.class);
            assertThatThrownBy(() -> await(game.reconnect())).isInstanceOf(ConnectionClosedException.class);
        }
    }

    @Test
    void busyLanPortIsReported() throws Exception {
        try (GameClient first = GameClient.start(worlds, LanPorts.ANY, SessionListener.NONE, background)) {
            await(first.hostLobby("Оля", 14, NpcShare.FEW, true));
            int port = first.lanAddress().orElseThrow().game().getPort();
            try (GameClient second = GameClient.start(
                    worlds.resolve("second"), new LanPorts(port, 0), SessionListener.NONE, background)) {
                assertThatThrownBy(() -> await(second.hostLobby("Ігор", 15, NpcShare.FEW, true)))
                        .isInstanceOfSatisfying(
                                LanUnavailableException.class,
                                e -> assertThat(e.port()).isEqualTo(port));
            }
        }
    }
}
