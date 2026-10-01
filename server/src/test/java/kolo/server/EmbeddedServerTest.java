package kolo.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.channel.local.LocalAddress;
import kolo.engine.state.NpcShare;
import kolo.protocol.message.ClientMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Вбудований сервер: адреса {@code LocalChannel}, хеш вбудованого контенту, світ повідомленнями й зупинка. */
@Timeout(60)
class EmbeddedServerTest {

    @Test
    void servesBundledContentOverLocalChannel() throws Exception {
        try (EmbeddedServer server = EmbeddedServer.startWithBundledContent()) {
            assertThat(server.address()).isInstanceOf(LocalAddress.class);
            assertThat(server.contentHash()).isEqualTo(TestServers.CONTENT.hash());

            try (TestClient client = TestClient.welcomed(server.address(), server.contentHash())) {
                client.send(new ClientMessage.CreateWorld(42, 2, NpcShare.NORMAL));

                assertThat(client.map()).isEqualTo(TestServers.map(42, 2, NpcShare.NORMAL));
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
