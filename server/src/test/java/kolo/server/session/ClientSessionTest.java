package kolo.server.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import kolo.engine.content.ContentPack;
import kolo.engine.error.ContentException;
import kolo.engine.error.ErrorCode;
import kolo.engine.state.NpcShare;
import kolo.protocol.Protocol;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.Handshake;
import kolo.protocol.message.ServerMessage;
import kolo.server.TestServers;
import kolo.server.persistence.WorldDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Правила розмови сервера з клієнтом без мережі: рукостискання, створення світу, «Готово», помилки й закриття. */
@Timeout(60)
class ClientSessionTest {

    private static final String HASH = TestServers.CONTENT.hash();

    @TempDir
    Path worlds;

    private Sessions sessions;
    private final RecordingPeer peer = new RecordingPeer();
    private ClientSession session;

    @BeforeEach
    void start() {
        sessions = new Sessions(new WorldDirectory(worlds));
        session = new ClientSession(() -> TestServers.CONTENT, sessions, peer);
    }

    @AfterEach
    void close() {
        sessions.closeAll(30, TimeUnit.SECONDS);
    }

    @Test
    void helloIsWelcomed() throws Exception {
        session.handle(Handshake.hello(HASH));

        assertThat(peer.next()).isEqualTo(new ServerMessage.Welcome(Protocol.VERSION, HASH));
        assertThat(peer.closed()).isFalse();
        assertThat(session.welcomed()).isTrue();
    }

    @Test
    void otherProtocolVersionIsRejectedAndClosed() throws Exception {
        session.handle(new ClientMessage.Hello(Protocol.VERSION + 1, HASH));

        ServerMessage.Error error = peer.error();
        assertThat(error.code()).isEqualTo(ErrorCode.VERSION_MISMATCH);
        assertThat(error.details()).containsEntry("part", Handshake.PROTOCOL);
        assertThat(peer.closed()).isTrue();
        assertThat(session.welcomed()).isFalse();
    }

    @Test
    void otherContentIsRejectedAndClosed() throws Exception {
        session.handle(Handshake.hello("b".repeat(64)));

        assertThat(peer.error().details()).containsEntry("part", Handshake.CONTENT);
        assertThat(peer.closed()).isTrue();
    }

    @Test
    void requestBeforeHelloClosesTheConnection() throws Exception {
        session.handle(new ClientMessage.CreateWorld(1, 1, NpcShare.FEW));

        ServerMessage.Error error = peer.error();
        assertThat(error.code()).isEqualTo(ErrorCode.PROTOCOL_ERROR);
        assertThat(error.details()).containsEntry("problem", "hello_expected");
        assertThat(peer.closed()).isTrue();
        assertThat(sessions.active()).isEmpty();
    }

    @Test
    void repeatedHelloClosesTheConnection() throws Exception {
        welcome();

        session.handle(Handshake.hello(HASH));

        assertThat(peer.error().details()).containsEntry("problem", "hello_repeated");
        assertThat(peer.closed()).isTrue();
    }

    @Test
    void createWorldSendsTheMapAndOpensTheFirstYear() throws Exception {
        welcome();

        session.handle(new ClientMessage.CreateWorld(42, 2, NpcShare.NORMAL));

        assertThat(peer.map()).isEqualTo(TestServers.map(42, 2, NpcShare.NORMAL));
        peer.expectOrders(0);
        assertThat(session.session()).isPresent();
        assertThat(peer.closed()).isFalse();
    }

    @Test
    void readyIsForwardedToTheSession() throws Exception {
        welcome();
        session.handle(new ClientMessage.CreateWorld(3, 1, NpcShare.FEW));
        peer.map();
        peer.expectOrders(0);

        session.handle(new ClientMessage.Ready(0));

        peer.expectYear(0);
    }

    @Test
    void readyWithoutSessionIsAnErrorButTheConnectionStaysOpen() throws Exception {
        welcome();

        session.handle(new ClientMessage.Ready(0));

        ServerMessage.Error error = peer.error();
        assertThat(error.code()).isEqualTo(ErrorCode.PHASE_CLOSED);
        assertThat(error.details()).containsEntry("turn", 0L);
        assertThat(peer.closed()).isFalse();
    }

    @Test
    void invalidWorldIsAnErrorButTheConnectionStaysOpen() throws Exception {
        welcome();

        session.handle(new ClientMessage.CreateWorld(1, 0, NpcShare.FEW));

        ServerMessage.Error error = peer.error();
        assertThat(error.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE);
        assertThat(error.details()).containsEntry("field", "players");
        assertThat(peer.closed()).isFalse();
        // Після помилки запиту той самий клієнт може спробувати знову.
        session.handle(new ClientMessage.CreateWorld(1, 1, NpcShare.FEW));
        assertThat(peer.next()).isInstanceOf(ServerMessage.MapStart.class);
    }

    @Test
    void newWorldLeavesThePreviousSession() throws Exception {
        welcome();
        session.handle(new ClientMessage.CreateWorld(1, 1, NpcShare.FEW));
        peer.map();
        peer.expectOrders(0);
        SessionActor first = session.session().orElseThrow();

        session.handle(new ClientMessage.CreateWorld(2, 1, NpcShare.FEW));
        peer.map();
        peer.expectOrders(0);

        assertThat(first.awaitClosed(30, TimeUnit.SECONDS)).isTrue();
        assertThat(first.state()).isEqualTo(SessionState.CLOSED);
        assertThat(sessions.active()).containsExactly(session.session().orElseThrow());
    }

    @Test
    void disconnectLeavesTheSession() throws Exception {
        welcome();
        session.handle(new ClientMessage.CreateWorld(1, 1, NpcShare.FEW));
        peer.map();
        peer.expectOrders(0);
        SessionActor actor = session.session().orElseThrow();

        session.disconnected();

        assertThat(actor.awaitClosed(30, TimeUnit.SECONDS)).isTrue();
        assertThat(session.session()).isEmpty();
        assertThat(sessions.active()).isEmpty();
    }

    @Test
    void invalidServerContentIsReportedAndClosed() throws Exception {
        Supplier<ContentPack> broken = () -> {
            throw new ContentException(ErrorCode.INVALID_CONTENT, Map.of("file", "map.yaml"));
        };

        new ClientSession(broken, sessions, peer).handle(Handshake.hello(HASH));

        ServerMessage.Error error = peer.error();
        assertThat(error.code()).isEqualTo(ErrorCode.INVALID_CONTENT);
        assertThat(error.details()).containsEntry("file", "map.yaml");
        assertThat(peer.closed()).isTrue();
    }

    @Test
    void serverBugsAreNotHiddenAsErrors() {
        Supplier<ContentPack> buggy = () -> {
            throw new IllegalStateException("баг");
        };

        assertThatThrownBy(() -> new ClientSession(buggy, sessions, peer).handle(Handshake.hello(HASH)))
                .isInstanceOf(IllegalStateException.class);
    }

    private void welcome() throws InterruptedException {
        session.handle(Handshake.hello(HASH));
        assertThat(peer.next()).isInstanceOf(ServerMessage.Welcome.class);
    }
}
