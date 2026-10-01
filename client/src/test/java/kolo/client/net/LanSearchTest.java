package kolo.client.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import kolo.protocol.Protocol;
import kolo.protocol.discovery.DiscoveryPacket;
import kolo.protocol.discovery.DiscoveryPackets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Пошук у локальній мережі проти відповідача на петлі. */
@Timeout(30)
class LanSearchTest {

    private static final Duration WAIT = Duration.ofMillis(400);
    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

    @Test
    void findsGameOnThisComputerOnceByLoopback() throws Exception {
        byte[] reply = DiscoveryPackets.encode(new DiscoveryPacket.Reply(Protocol.VERSION, 4567));
        // Відповідь двічі на кожен запит (а запитів — кілька: на петлю, широкомовні адреси й повтор) — гра одна.
        try (FakeResponder responder = new FakeResponder(List.of(reply, reply))) {
            List<LanHost> hosts = new LanSearch(responder.port(), WAIT).find();

            assertThat(hosts).containsExactly(new LanHost(new InetSocketAddress(LOOPBACK, 4567), Protocol.VERSION));
            // Запит повторюється посередині очікування.
            assertThat(responder.received()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void reportsOtherVersionsAndDistinctPorts() throws Exception {
        try (FakeResponder responder = new FakeResponder(List.of(
                DiscoveryPackets.encode(new DiscoveryPacket.Reply(Protocol.VERSION + 1, 5000)),
                DiscoveryPackets.encode(new DiscoveryPacket.Reply(Protocol.VERSION, 4000))))) {
            List<LanHost> hosts = new LanSearch(responder.port(), WAIT).find();

            assertThat(hosts)
                    .containsExactly(
                            new LanHost(new InetSocketAddress(LOOPBACK, 4000), Protocol.VERSION),
                            new LanHost(new InetSocketAddress(LOOPBACK, 5000), Protocol.VERSION + 1));
        }
    }

    @Test
    void ignoresForeignDatagrams() throws Exception {
        try (FakeResponder responder = new FakeResponder(List.of(
                "hello".getBytes(StandardCharsets.US_ASCII),
                DiscoveryPackets.encode(new DiscoveryPacket.Query(Protocol.VERSION))))) {
            assertThat(new LanSearch(responder.port(), WAIT).find()).isEmpty();
        }
    }

    @Test
    void silenceIsNoGames() throws Exception {
        try (FakeResponder responder = new FakeResponder(List.of())) {
            assertThat(new LanSearch(responder.port(), WAIT).find()).isEmpty();
        }
    }

    @Test
    void searchesLoopbackAndBroadcast() {
        assertThat(LanSearch.targets()).first().isEqualTo(LOOPBACK);
        assertThat(LanSearch.targets())
                .last()
                .extracting(InetAddress::getHostAddress)
                .isEqualTo("255.255.255.255");
        assertThat(LanSearch.targets()).doesNotHaveDuplicates();
    }

    @Test
    void invalidSettingsAreRejected() {
        assertThatThrownBy(() -> new LanSearch(0, WAIT)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new LanSearch(1, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
}
