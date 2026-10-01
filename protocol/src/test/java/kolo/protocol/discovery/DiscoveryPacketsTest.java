package kolo.protocol.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;
import kolo.protocol.Protocol;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class DiscoveryPacketsTest {

    @Test
    void queryFormat() {
        byte[] bytes = DiscoveryPackets.encode(new DiscoveryPacket.Query(6));

        assertThat(HexFormat.of().formatHex(bytes)).isEqualTo("4b4f4c4f" + "01" + "00000006");
    }

    @Test
    void replyFormat() {
        byte[] bytes = DiscoveryPackets.encode(new DiscoveryPacket.Reply(6, 19_700));

        assertThat(HexFormat.of().formatHex(bytes)).isEqualTo("4b4f4c4f" + "02" + "00000006" + "4cf4");
        assertThat(bytes).hasSize(DiscoveryPackets.MAX_BYTES);
    }

    @Test
    void readsOnlyReceivedBytesOfLargerBuffer() {
        byte[] buffer = new byte[64];
        byte[] query = DiscoveryPackets.encode(new DiscoveryPacket.Query(Protocol.VERSION));
        System.arraycopy(query, 0, buffer, 0, query.length);

        assertThat(DiscoveryPackets.decode(buffer, query.length)).contains(new DiscoveryPacket.Query(Protocol.VERSION));
    }

    @Test
    void foreignOrDamagedDatagramsAreIgnored() {
        byte[] reply = DiscoveryPackets.encode(new DiscoveryPacket.Reply(Protocol.VERSION, 19_700));

        assertThat(DiscoveryPackets.decode(new byte[0], 0)).isEmpty();
        assertThat(DiscoveryPackets.decode(reply, reply.length - 1)).isEmpty();
        assertThat(DiscoveryPackets.decode(Arrays.copyOf(reply, 12), 12)).isEmpty();
        assertThat(DiscoveryPackets.decode(reply, reply.length + 1)).isEmpty();
        byte[] otherGame = reply.clone();
        otherGame[0] = 'X';
        assertThat(DiscoveryPackets.decode(otherGame, otherGame.length)).isEmpty();
        byte[] unknownType = reply.clone();
        unknownType[4] = 3;
        assertThat(DiscoveryPackets.decode(unknownType, unknownType.length)).isEmpty();
        byte[] zeroPort = reply.clone();
        zeroPort[9] = 0;
        zeroPort[10] = 0;
        assertThat(DiscoveryPackets.decode(zeroPort, zeroPort.length)).isEmpty();
        byte[] text = "KOLO?????????".getBytes(StandardCharsets.US_ASCII);
        assertThat(DiscoveryPackets.decode(text, text.length)).isEmpty();
    }

    @Property
    void queryRoundTrip(@ForAll int version) {
        byte[] bytes = DiscoveryPackets.encode(new DiscoveryPacket.Query(version));

        assertThat(DiscoveryPackets.decode(bytes, bytes.length))
                .isEqualTo(Optional.of(new DiscoveryPacket.Query(version)));
    }

    @Property
    void replyRoundTrip(@ForAll int version, @ForAll @IntRange(min = 1, max = 65_535) int port) {
        DiscoveryPacket.Reply reply = new DiscoveryPacket.Reply(version, port);
        byte[] bytes = DiscoveryPackets.encode(reply);

        assertThat(DiscoveryPackets.decode(bytes, bytes.length)).contains(reply);
    }

    @Property
    void arbitraryBytesNeverThrow(@ForAll byte[] bytes) {
        Optional<DiscoveryPacket> packet = DiscoveryPackets.decode(bytes, bytes.length);

        packet.ifPresent(read -> assertThat(DiscoveryPackets.encode(read)).isEqualTo(bytes));
    }
}
