package kolo.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import kolo.protocol.Protocol;
import org.junit.jupiter.api.Test;

class DedicatedServerMainTest {

    @Test
    void defaults() {
        assertThat(DedicatedServerMain.options())
                .isEqualTo(new DedicatedServerMain.Options(Protocol.DEFAULT_PORT, DedicatedServerMain.DEFAULT_WORLDS));
    }

    @Test
    void chosenPortAndWorlds() {
        assertThat(DedicatedServerMain.options("--port", "4000").port()).isEqualTo(4000);
        assertThat(DedicatedServerMain.options("--port", "0").port()).isZero();
        assertThat(DedicatedServerMain.options("--worlds", "/srv/kolo", "--port", "1"))
                .isEqualTo(new DedicatedServerMain.Options(1, Path.of("/srv/kolo")));
    }

    @Test
    void invalidArguments() {
        assertThatThrownBy(() -> DedicatedServerMain.options("--port")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DedicatedServerMain.options("--port", "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DedicatedServerMain.options("--port", "65536"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DedicatedServerMain.options("--port", "-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DedicatedServerMain.options("--host", "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DedicatedServerMain.options("--worlds")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DedicatedServerMain.options("--worlds", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
