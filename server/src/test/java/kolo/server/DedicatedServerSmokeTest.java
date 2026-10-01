package kolo.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import kolo.engine.state.NpcShare;
import kolo.protocol.message.ClientMessage;
import kolo.server.persistence.WorldStore;
import kolo.server.transport.GameServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Смок: окремий сервер піднімається на TCP, віддає світ клієнтові по мережі й проводить рік у файл світу. */
class DedicatedServerSmokeTest {

    @TempDir
    Path worlds;

    @Test
    @Timeout(60)
    void servesWorldOverTcp() throws Exception {
        try (GameServer server =
                DedicatedServerMain.start(TestServers.CONTENT, new DedicatedServerMain.Options(0, worlds))) {
            InetSocketAddress address = server.bindTcp(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            try (TestClient client = TestClient.welcomed(address, TestServers.CONTENT.hash())) {
                client.send(new ClientMessage.CreateWorld(1970, 1, NpcShare.NORMAL));

                assertThat(client.world().cells()).isNotEmpty();
                client.endYear(0);
            }
        }
        assertThat(worlds.resolve("world-1970" + WorldStore.EXTENSION)).exists();
    }
}
