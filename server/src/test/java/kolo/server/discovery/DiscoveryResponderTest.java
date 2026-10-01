package kolo.server.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import kolo.protocol.Protocol;
import kolo.protocol.discovery.DiscoveryPacket;
import kolo.protocol.discovery.DiscoveryPackets;
import kolo.server.TestDiscovery;
import kolo.server.TestServers;
import kolo.server.persistence.WorldDirectory;
import kolo.server.transport.GameServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Відповіді на UDP-пошук через справжній сокет на петлі. */
@Timeout(30)
class DiscoveryResponderTest {

    private static final InetSocketAddress ANY_LOOPBACK = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);

    @TempDir
    Path worlds;

    @Test
    void answersQueryWithGamePort() throws Exception {
        try (GameServer server = GameServer.start(() -> TestServers.CONTENT, new WorldDirectory(worlds))) {
            InetSocketAddress udp = server.bindDiscovery(ANY_LOOPBACK, 4567);

            assertThat(TestDiscovery.ask(udp, Protocol.VERSION))
                    .contains(new DiscoveryPacket.Reply(Protocol.VERSION, 4567));
            // Повторний запит — та сама відповідь: стану немає.
            assertThat(TestDiscovery.ask(udp, Protocol.VERSION))
                    .contains(new DiscoveryPacket.Reply(Protocol.VERSION, 4567));
        }
    }

    @Test
    void answersOtherVersionsSoTheyKnowTheGameIsIncompatible() throws Exception {
        try (GameServer server = GameServer.start(() -> TestServers.CONTENT, new WorldDirectory(worlds))) {
            InetSocketAddress udp = server.bindDiscovery(ANY_LOOPBACK, 4567);

            assertThat(TestDiscovery.ask(udp, Protocol.VERSION + 1))
                    .contains(new DiscoveryPacket.Reply(Protocol.VERSION, 4567));
        }
    }

    @Test
    void ignoresForeignAndReplyDatagrams() throws Exception {
        try (GameServer server = GameServer.start(() -> TestServers.CONTENT, new WorldDirectory(worlds))) {
            InetSocketAddress udp = server.bindDiscovery(ANY_LOOPBACK, 4567);

            assertThat(TestDiscovery.send(udp, "hello".getBytes(), TestDiscovery.SILENCE_MILLIS))
                    .isEmpty();
            assertThat(TestDiscovery.send(udp, new byte[2048], TestDiscovery.SILENCE_MILLIS))
                    .isEmpty();
            // Відповідь на відповідь — шлях до нескінченного пінг-понгу двох серверів.
            byte[] reply = DiscoveryPackets.encode(new DiscoveryPacket.Reply(Protocol.VERSION, 1));
            assertThat(TestDiscovery.send(udp, reply, TestDiscovery.SILENCE_MILLIS))
                    .isEmpty();
            // Після сміття сервер відповідає далі.
            assertThat(TestDiscovery.ask(udp, Protocol.VERSION)).isPresent();
        }
    }

    @Test
    void closedServerStopsAnswering() throws Exception {
        GameServer server = GameServer.start(() -> TestServers.CONTENT, new WorldDirectory(worlds));
        InetSocketAddress udp = server.bindDiscovery(ANY_LOOPBACK, 4567);
        server.close();

        assertThat(TestDiscovery.send(
                        udp,
                        DiscoveryPackets.encode(new DiscoveryPacket.Query(Protocol.VERSION)),
                        TestDiscovery.SILENCE_MILLIS))
                .isEmpty();
    }
}
