package kolo.server.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import kolo.engine.content.ContentPack;
import kolo.engine.error.ContentException;
import kolo.engine.error.ErrorCode;
import kolo.engine.state.NpcShare;
import kolo.engine.view.MapView;
import kolo.protocol.Protocol;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.Handshake;
import kolo.protocol.message.MapAssembler;
import kolo.protocol.message.ServerMessage;
import kolo.server.TestServers;
import org.junit.jupiter.api.Test;

/** Правила розмови сервера з клієнтом без мережі: рукостискання, створення світу, помилки й закриття. */
class ClientSessionTest {

    private static final String HASH = TestServers.CONTENT.hash();

    private final ClientSession session = new ClientSession(() -> TestServers.CONTENT);

    @Test
    void helloIsWelcomed() {
        Reply reply = session.handle(Handshake.hello(HASH));

        assertThat(reply.close()).isFalse();
        assertThat(reply.messages()).containsExactly(new ServerMessage.Welcome(Protocol.VERSION, HASH));
        assertThat(session.welcomed()).isTrue();
    }

    @Test
    void otherProtocolVersionIsRejectedAndClosed() {
        Reply reply = session.handle(new ClientMessage.Hello(Protocol.VERSION + 1, HASH));

        assertThat(reply.close()).isTrue();
        ServerMessage.Error error = single(reply);
        assertThat(error.code()).isEqualTo(ErrorCode.VERSION_MISMATCH);
        assertThat(error.details()).containsEntry("part", Handshake.PROTOCOL);
        assertThat(session.welcomed()).isFalse();
    }

    @Test
    void otherContentIsRejectedAndClosed() {
        Reply reply = session.handle(Handshake.hello("b".repeat(64)));

        assertThat(reply.close()).isTrue();
        assertThat(single(reply).details()).containsEntry("part", Handshake.CONTENT);
    }

    @Test
    void requestBeforeHelloClosesTheConnection() {
        Reply reply = session.handle(new ClientMessage.CreateWorld(1, 1, NpcShare.FEW));

        assertThat(reply.close()).isTrue();
        assertThat(single(reply).code()).isEqualTo(ErrorCode.PROTOCOL_ERROR);
        assertThat(single(reply).details()).containsEntry("problem", "hello_expected");
    }

    @Test
    void repeatedHelloClosesTheConnection() {
        session.handle(Handshake.hello(HASH));

        Reply reply = session.handle(Handshake.hello(HASH));

        assertThat(reply.close()).isTrue();
        assertThat(single(reply).details()).containsEntry("problem", "hello_repeated");
    }

    @Test
    void createWorldSendsTheMapInChunks() {
        session.handle(Handshake.hello(HASH));

        Reply reply = session.handle(new ClientMessage.CreateWorld(42, 2, NpcShare.NORMAL));

        assertThat(reply.close()).isFalse();
        assertThat(reply.messages()).hasSizeGreaterThan(2);
        assertThat(assemble(reply.messages())).isEqualTo(TestServers.map(42, 2, NpcShare.NORMAL));
    }

    @Test
    void invalidWorldIsAnErrorButTheConnectionStaysOpen() {
        session.handle(Handshake.hello(HASH));

        Reply reply = session.handle(new ClientMessage.CreateWorld(1, 0, NpcShare.FEW));

        assertThat(reply.close()).isFalse();
        assertThat(single(reply).code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE);
        assertThat(single(reply).details()).containsEntry("field", "players");
        // Після помилки запиту той самий клієнт може спробувати знову.
        assertThat(session.handle(new ClientMessage.CreateWorld(1, 1, NpcShare.FEW))
                        .messages())
                .first()
                .isInstanceOf(ServerMessage.MapStart.class);
    }

    @Test
    void invalidServerContentIsReportedAndClosed() {
        Supplier<ContentPack> broken = () -> {
            throw new ContentException(ErrorCode.INVALID_CONTENT, Map.of("file", "map.yaml"));
        };

        Reply reply = new ClientSession(broken).handle(Handshake.hello(HASH));

        assertThat(reply.close()).isTrue();
        assertThat(single(reply).code()).isEqualTo(ErrorCode.INVALID_CONTENT);
        assertThat(single(reply).details()).containsEntry("file", "map.yaml");
    }

    @Test
    void serverBugsAreNotHiddenAsErrors() {
        Supplier<ContentPack> buggy = () -> {
            throw new IllegalStateException("баг");
        };

        assertThatThrownBy(() -> new ClientSession(buggy).handle(Handshake.hello(HASH)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static ServerMessage.Error single(Reply reply) {
        assertThat(reply.messages()).hasSize(1);
        return (ServerMessage.Error) reply.messages().getFirst();
    }

    private static MapView assemble(List<ServerMessage> messages) {
        MapAssembler assembler = new MapAssembler();
        assembler.start((ServerMessage.MapStart) messages.getFirst());
        Optional<MapView> map = Optional.empty();
        for (ServerMessage message : messages.subList(1, messages.size())) {
            map = assembler.add((ServerMessage.MapCells) message);
        }
        return map.orElseThrow();
    }
}
