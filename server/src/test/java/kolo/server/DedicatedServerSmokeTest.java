package kolo.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import kolo.engine.state.NpcShare;
import kolo.protocol.message.ClientMessage;
import kolo.server.transport.GameServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Смок: окремий сервер піднімається на TCP і віддає світ клієнтові по мережі. */
class DedicatedServerSmokeTest {

    @Test
    @Timeout(60)
    void servesWorldOverTcp() throws Exception {
        try (GameServer server = DedicatedServerMain.start(TestServers.CONTENT, 0)) {
            InetSocketAddress address = server.bindTcp(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            try (TestClient client = TestClient.welcomed(address, TestServers.CONTENT.hash())) {
                client.send(new ClientMessage.CreateWorld(1970, 1, NpcShare.NORMAL));

                assertThat(client.map().cells()).isNotEmpty();
            }
        }
    }
}
