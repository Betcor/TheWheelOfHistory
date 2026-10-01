package kolo.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.channel.local.LocalAddress;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import kolo.engine.state.NpcShare;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.ServerMessage;
import kolo.server.persistence.WorldDirectory;
import kolo.server.persistence.WorldStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Вбудований сервер: адреса {@code LocalChannel}, хеш вбудованого контенту, світ повідомленнями й зупинка. */
@Timeout(60)
class EmbeddedServerTest {

    @TempDir
    Path worlds;

    @Test
    void servesBundledContentOverLocalChannel() throws Exception {
        try (EmbeddedServer server = EmbeddedServer.startWithBundledContent(worlds)) {
            assertThat(server.address()).isInstanceOf(LocalAddress.class);
            assertThat(server.contentHash()).isEqualTo(TestServers.CONTENT.hash());

            try (TestClient client = TestClient.welcomed(server.address(), server.contentHash())) {
                assertThat(client.solo(42, NpcShare.NORMAL)).isEqualTo(TestServers.map(42, 1, NpcShare.NORMAL));
            }
        }
        assertThat(worlds.resolve("world-42" + WorldStore.EXTENSION)).exists();
    }

    @Test
    void defaultWorldsAreInTheGameHome() throws Exception {
        try (EmbeddedServer server = EmbeddedServer.startWithBundledContent();
                TestClient client = TestClient.welcomed(server.address(), server.contentHash())) {
            client.solo(-77, NpcShare.FEW);
        }
        assertThat(WorldDirectory.defaultLocation().resolve("world--77" + WorldStore.EXTENSION))
                .exists();
    }

    @Test
    void lanGameIsReachableOverTcp() throws Exception {
        try (EmbeddedServer server = EmbeddedServer.startWithBundledContent(worlds)) {
            InetSocketAddress lan = server.openLan(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            try (TestClient host = TestClient.welcomed(server.address(), server.contentHash());
                    TestClient guest = TestClient.welcomed(lan, server.contentHash())) {
                long session = host.createLobby("Оля", 43, NpcShare.FEW).session();

                guest.send(new ClientMessage.JoinLobby(session, "Ігор"));

                assertThat(guest.next(ServerMessage.Joined.class).session()).isEqualTo(session);
                assertThat(guest.next(ServerMessage.Lobby.class).players()).hasSize(2);
            }
        }
    }

    @Test
    void eachServerHasItsOwnAddress() {
        try (EmbeddedServer first = EmbeddedServer.startWithBundledContent();
                EmbeddedServer second = EmbeddedServer.startWithBundledContent()) {
            assertThat(first.address()).isNotEqualTo(second.address());
        }
    }

    @Test
    void closedServerRefusesConnections() {
        EmbeddedServer server = EmbeddedServer.startWithBundledContent();
        server.close();
        server.close();

        assertThatThrownBy(() -> TestClient.connect(server.address())).isInstanceOf(Exception.class);
    }
}
