package kolo.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kolo.protocol.Protocol;
import org.junit.jupiter.api.Test;

class DedicatedServerMainTest {

    @Test
    void defaultPort() {
        assertThat(DedicatedServerMain.port()).isEqualTo(Protocol.DEFAULT_PORT);
    }

    @Test
    void chosenPort() {
        assertThat(DedicatedServerMain.port("--port", "4000")).isEqualTo(4000);
        assertThat(DedicatedServerMain.port("--port", "0")).isZero();
    }

    @Test
    void invalidArguments() {
        assertThatThrownBy(() -> DedicatedServerMain.port("--port")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DedicatedServerMain.port("--port", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DedicatedServerMain.port("--port", "65536"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DedicatedServerMain.port("--port", "-1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DedicatedServerMain.port("--host", "x")).isInstanceOf(IllegalArgumentException.class);
    }
}
