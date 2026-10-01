package kolo.server.transport;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import kolo.engine.content.ContentPack;
import kolo.engine.error.ErrorCode;
import kolo.engine.state.NpcShare;
import kolo.protocol.Protocol;
import kolo.protocol.codec.MessageJson;
import kolo.protocol.codec.ProtocolPipeline;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.Handshake;
import kolo.protocol.message.ServerMessage;
import kolo.server.TestServers;
import kolo.server.session.ClientSession;
import org.junit.jupiter.api.Test;

/**
 * Обробник з'єднання в {@link EmbeddedChannel} зі справжніми фреймами; сесія виконується в потоці тесту, тож
 * відповіді готові одразу.
 */
class ConnectionHandlerTest {

    private static final String HASH = TestServers.CONTENT.hash();

    @Test
    void helloAndWorldOverFrames() {
        EmbeddedChannel channel = channel(() -> TestServers.CONTENT);

        channel.writeInbound(frame(MessageJson.write(Handshake.hello(HASH))));
        channel.writeInbound(frame(MessageJson.write(new ClientMessage.CreateWorld(3, 1, NpcShare.FEW))));

        List<ServerMessage> replies = replies(channel);
        assertThat(replies.getFirst()).isEqualTo(new ServerMessage.Welcome(Protocol.VERSION, HASH));
        assertThat(replies.get(1)).isInstanceOf(ServerMessage.MapStart.class);
        assertThat(replies.getLast()).isInstanceOf(ServerMessage.MapCells.class);
        assertThat(channel.isOpen()).isTrue();
        channel.finishAndReleaseAll();
    }

    @Test
    void malformedMessageIsReportedAndClosed() {
        EmbeddedChannel channel = channel(() -> TestServers.CONTENT);

        channel.writeInbound(frame("{\"type\":\"hello\"}".getBytes(StandardCharsets.UTF_8)));

        ServerMessage.Error error = onlyError(channel);
        assertThat(error.code()).isEqualTo(ErrorCode.PROTOCOL_ERROR);
        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void tooLongFrameIsReportedAndClosed() {
        EmbeddedChannel channel = channel(() -> TestServers.CONTENT);

        channel.writeInbound(
                Unpooled.buffer().writeInt(Protocol.MAX_FRAME_BYTES + 1).writeByte('{'));

        ServerMessage.Error error = onlyError(channel);
        assertThat(error.details()).containsEntry("location", "frame").containsEntry("problem", "frame_too_large");
        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void handshakeFailureIsReportedAndClosed() {
        EmbeddedChannel channel = channel(() -> TestServers.CONTENT);

        channel.writeInbound(frame(MessageJson.write(Handshake.hello("c".repeat(64)))));

        assertThat(onlyError(channel).code()).isEqualTo(ErrorCode.VERSION_MISMATCH);
        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void serverBugClosesWithoutReply() {
        EmbeddedChannel channel = channel(() -> {
            throw new IllegalStateException("баг сервера");
        });

        channel.writeInbound(frame(MessageJson.write(Handshake.hello(HASH))));

        assertThat(replies(channel)).isEmpty();
        assertThat(channel.isOpen()).isFalse();
    }

    private static EmbeddedChannel channel(Supplier<ContentPack> content) {
        EmbeddedChannel channel = new EmbeddedChannel();
        ProtocolPipeline.server(channel.pipeline());
        channel.pipeline().addLast(new ConnectionHandler(new ClientSession(content), Runnable::run));
        return channel;
    }

    private static ByteBuf frame(byte[] json) {
        return Unpooled.buffer().writeInt(json.length).writeBytes(json);
    }

    private static ServerMessage.Error onlyError(EmbeddedChannel channel) {
        List<ServerMessage> replies = replies(channel);
        assertThat(replies).hasSize(1);
        return (ServerMessage.Error) replies.getFirst();
    }

    /** Вихідні фрейми сервера, прочитані клієнтським кодеком. */
    private static List<ServerMessage> replies(EmbeddedChannel server) {
        server.runPendingTasks();
        EmbeddedChannel client = new EmbeddedChannel();
        ProtocolPipeline.client(client.pipeline());
        for (Object out = server.readOutbound(); out != null; out = server.readOutbound()) {
            client.writeInbound(out);
        }
        List<ServerMessage> result = new ArrayList<>();
        for (Object in = client.readInbound(); in != null; in = client.readInbound()) {
            result.add((ServerMessage) in);
        }
        client.finishAndReleaseAll();
        return result;
    }
}
