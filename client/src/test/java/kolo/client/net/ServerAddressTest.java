package kolo.client.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import kolo.protocol.Protocol;
import org.junit.jupiter.api.Test;

class ServerAddressTest {

    @Test
    void hostWithoutPortUsesTheDefault() {
        assertThat(ServerAddress.parse(" 192.168.0.5 "))
                .isEqualTo(InetSocketAddress.createUnresolved("192.168.0.5", Protocol.DEFAULT_PORT));
    }

    @Test
    void hostAndPort() {
        assertThat(ServerAddress.parse("kolo.example:2000"))
                .isEqualTo(InetSocketAddress.createUnresolved("kolo.example", 2000));
    }

    @Test
    void ipv6WithAndWithoutPort() {
        assertThat(ServerAddress.parse("[::1]:2000")).isEqualTo(InetSocketAddress.createUnresolved("::1", 2000));
        assertThat(ServerAddress.parse("[::1]"))
                .isEqualTo(InetSocketAddress.createUnresolved("::1", Protocol.DEFAULT_PORT));
        assertThat(ServerAddress.parse("fe80::1"))
                .isEqualTo(InetSocketAddress.createUnresolved("fe80::1", Protocol.DEFAULT_PORT));
    }

    @Test
    void rejectsBadAddresses() {
        for (String bad :
                new String[] {"", "  ", null, ":2000", "host:", "host:abc", "host:0", "host:65536", "[::1", "[::1]x"}) {
            assertThatThrownBy(() -> ServerAddress.parse(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
