package kolo.protocol.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import kolo.engine.error.VersionMismatchException;
import kolo.protocol.Protocol;
import org.junit.jupiter.api.Test;

class HandshakeTest {

    private static final String HASH = "hash";

    @Test
    void matchingHelloIsWelcomed() {
        ServerMessage.Welcome welcome = Handshake.accept(Handshake.hello(HASH), HASH);

        assertThat(welcome).isEqualTo(new ServerMessage.Welcome(Protocol.VERSION, HASH));
        Handshake.confirm(welcome, HASH);
    }

    @Test
    void protocolVersionIsCheckedFirst() {
        // Обидві частини інші — повідомлення про протокол: з іншою версією хешу не можна вірити.
        assertThatThrownBy(() -> Handshake.accept(new ClientMessage.Hello(Protocol.VERSION + 1, "other"), HASH))
                .isInstanceOfSatisfying(
                        VersionMismatchException.class,
                        e -> assertThat(e.details())
                                .containsExactly(
                                        entry("client", Protocol.VERSION + 1),
                                        entry("part", Handshake.PROTOCOL),
                                        entry("server", Protocol.VERSION)));
    }

    @Test
    void contentHashMustMatch() {
        assertThatThrownBy(() -> Handshake.accept(Handshake.hello("other"), HASH))
                .isInstanceOfSatisfying(
                        VersionMismatchException.class,
                        e -> assertThat(e.details())
                                .containsEntry("part", Handshake.CONTENT)
                                .containsEntry("client", "other")
                                .containsEntry("server", HASH));
    }

    @Test
    void clientChecksServerToo() {
        assertThatThrownBy(() -> Handshake.confirm(new ServerMessage.Welcome(Protocol.VERSION + 1, HASH), HASH))
                .isInstanceOfSatisfying(
                        VersionMismatchException.class,
                        e -> assertThat(e.details()).containsEntry("part", Handshake.PROTOCOL));
        assertThatThrownBy(() -> Handshake.confirm(new ServerMessage.Welcome(Protocol.VERSION, "other"), HASH))
                .isInstanceOfSatisfying(
                        VersionMismatchException.class,
                        e -> assertThat(e.details())
                                .containsEntry("part", Handshake.CONTENT)
                                .containsEntry("client", HASH)
                                .containsEntry("server", "other"));
    }
}
