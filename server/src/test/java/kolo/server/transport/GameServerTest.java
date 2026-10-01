package kolo.server.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
import kolo.protocol.message.ServerMessage;
import kolo.server.TestClient;
import kolo.server.TestServers;
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
            client.send(new ClientMessage.CreateWorld(5, 2, NpcShare.FEW));

            assertThat(client.world()).isEqualTo(TestServers.map(5, 2, NpcShare.FEW));
        }
    }

    @Test
    void yearsOverLocalChannelAreSaved() throws Exception {
        try (TestClient client = TestClient.welcomed(server.bindLocal(), HASH)) {
            client.send(new ClientMessage.CreateWorld(6, 1, NpcShare.FEW));
            client.world();

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
            client.send(new ClientMessage.CreateWorld(5, 2, NpcShare.FEW));

            assertThat(client.world()).isEqualTo(TestServers.map(5, 2, NpcShare.FEW));
            client.endYear(0);
        }
    }

    @Test
    void bothTransportsAtOnce() throws Exception {
        try (TestClient local = TestClient.welcomed(server.bindLocal(), HASH);
                TestClient tcp = TestClient.welcomed(server.bindTcp(ANY_LOOPBACK), HASH)) {
            local.send(new ClientMessage.CreateWorld(8, 1, NpcShare.FEW));
            tcp.send(new ClientMessage.CreateWorld(9, 1, NpcShare.FEW));

            assertThat(tcp.world()).isEqualTo(TestServers.map(9, 1, NpcShare.FEW));
            assertThat(local.world()).isEqualTo(TestServers.map(8, 1, NpcShare.FEW));
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
                        client.send(new ClientMessage.CreateWorld(chosen, 1, NpcShare.FEW));
                        MapView map = client.world();
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
            client.send(new ClientMessage.CreateWorld(7, 1, NpcShare.FEW));
            client.world();
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
            client.send(new ClientMessage.CreateWorld(7, 1, NpcShare.FEW));
            client.world();
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
