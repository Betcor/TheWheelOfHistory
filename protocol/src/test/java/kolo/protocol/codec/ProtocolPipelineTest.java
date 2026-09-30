package kolo.protocol.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.TooLongFrameException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ProtocolException;
import kolo.engine.state.NpcShare;
import kolo.engine.view.MapView;
import kolo.protocol.Protocol;
import kolo.protocol.TestMessages;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.MapChunks;
import kolo.protocol.message.ServerMessage;
import org.junit.jupiter.api.Test;

/** Обидві сторони протоколу в каналах Netty без мережі: байти з одного каналу передаються в інший вручну. */
class ProtocolPipelineTest {

    private final EmbeddedChannel server = channel(true);
    private final EmbeddedChannel client = channel(false);

    @Test
    void clientMessagesReachServer() {
        List<ClientMessage> sent = List.of(
                new ClientMessage.Hello(Protocol.VERSION, TestMessages.HASH),
                new ClientMessage.CreateWorld(3, 2, NpcShare.NORMAL));

        for (ClientMessage message : sent) {
            client.writeOutbound(message);
        }
        transfer(client, server);

        assertThat(inbound(server)).isEqualTo(sent);
    }

    @Test
    void serverMessagesReachClient() {
        List<ServerMessage> sent = new ArrayList<>(MapChunks.split(TestMessages.map(9, 25), 7));
        sent.add(new ServerMessage.Error(ErrorCode.NOT_FOUND, ErrorDetails.of("id", "cty_1")));

        for (ServerMessage message : sent) {
            server.writeOutbound(message);
        }
        transfer(server, client);

        assertThat(inbound(client)).isEqualTo(sent);
    }

    @Test
    void framesSurviveArbitrarySplitting() {
        server.writeOutbound(MapChunks.split(TestMessages.map(1, 5), 5).toArray());
        ByteBuf all = Unpooled.buffer();
        for (ByteBuf part = server.readOutbound(); part != null; part = server.readOutbound()) {
            all.writeBytes(part);
            part.release();
        }

        // По байту: декодер фреймів мусить дочекатися повного повідомлення.
        while (all.isReadable()) {
            client.writeInbound(all.readRetainedSlice(1));
        }
        all.release();

        assertThat(inbound(client)).hasSize(2);
    }

    @Test
    void malformedMessageFailsWithProtocolError() {
        byte[] json = "{\"type\":\"nope\"}".getBytes(StandardCharsets.UTF_8);
        ByteBuf frame = Unpooled.buffer().writeInt(json.length).writeBytes(json);

        assertThatThrownBy(() -> server.writeInbound(frame))
                .isInstanceOf(DecoderException.class)
                .hasCauseInstanceOf(ProtocolException.class);
    }

    @Test
    void tooLongFrameIsRejectedBeforeReading() {
        ByteBuf header = Unpooled.buffer().writeInt(Protocol.MAX_FRAME_BYTES + 1);

        assertThatThrownBy(() -> server.writeInbound(header)).isInstanceOf(TooLongFrameException.class);
    }

    @Test
    void tooLargeOutgoingMessageFailsTheWrite() {
        // ~30 000 комірок — понад 4 МБ JSON: таку карту треба ділити на частини.
        MapView map = TestMessages.map(1, 30_000);

        assertThatThrownBy(() -> server.writeOutbound(new ServerMessage.MapCells(0, map.cells())))
                .isInstanceOf(EncoderException.class)
                .hasCauseInstanceOf(ProtocolException.class);
    }

    @Test
    void wrongDirectionIsNotEncoded() {
        // Сервер не пише повідомлень клієнта: обробник їх пропускає, і до каналу доходить не ByteBuf.
        server.writeOutbound(new ClientMessage.Hello(1, "a"));

        Object written = server.readOutbound();
        assertThat(written).isInstanceOf(ClientMessage.Hello.class);
    }

    private static EmbeddedChannel channel(boolean serverSide) {
        EmbeddedChannel channel = new EmbeddedChannel();
        if (serverSide) {
            ProtocolPipeline.server(channel.pipeline());
        } else {
            ProtocolPipeline.client(channel.pipeline());
        }
        return channel;
    }

    private static void transfer(EmbeddedChannel from, EmbeddedChannel to) {
        for (Object frame = from.readOutbound(); frame != null; frame = from.readOutbound()) {
            to.writeInbound(frame);
        }
    }

    private static List<Object> inbound(EmbeddedChannel channel) {
        List<Object> messages = new ArrayList<>();
        for (Object message = channel.readInbound(); message != null; message = channel.readInbound()) {
            messages.add(message);
        }
        return messages;
    }
}
