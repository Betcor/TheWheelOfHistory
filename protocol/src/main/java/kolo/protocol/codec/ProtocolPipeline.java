package kolo.protocol.codec;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import kolo.protocol.Protocol;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.ServerMessage;

/**
 * Обробники протоколу в каналі Netty — однакові для TCP і {@code LocalChannel}: фрейм — 4 байти довжини (big-endian)
 * і JSON повідомлення ({@link MessageJson}) не довший за {@value Protocol#MAX_FRAME_BYTES} байтів.
 *
 * <p>Після обробників канал на сервері читає {@link ClientMessage} і пише {@link ServerMessage}, на клієнті — навпаки.
 * Пошкоджене вхідне повідомлення — {@code DecoderException} з {@link kolo.engine.error.ProtocolException} усередині,
 * завеликий фрейм — {@code TooLongFrameException} (обидва — у {@code exceptionCaught}); завелике вихідне
 * повідомлення — невдалий запис з {@code EncoderException}.
 */
public final class ProtocolPipeline {

    /** Ім'я обробника повідомлень — щоб ставити свої обробники після нього. */
    public static final String MESSAGES = "messages";

    private static final int LENGTH_BYTES = 4;

    private ProtocolPipeline() {}

    /** Обробники серверного боку з'єднання. */
    public static void server(ChannelPipeline pipeline) {
        frames(pipeline);
        pipeline.addLast(
                MESSAGES, new MessageCodec<>(ServerMessage.class, MessageJson::readClient, MessageJson::write));
    }

    /** Обробники клієнтського боку з'єднання. */
    public static void client(ChannelPipeline pipeline) {
        frames(pipeline);
        pipeline.addLast(
                MESSAGES, new MessageCodec<>(ClientMessage.class, MessageJson::readServer, MessageJson::write));
    }

    private static void frames(ChannelPipeline pipeline) {
        pipeline.addLast(
                "frame-decoder",
                new LengthFieldBasedFrameDecoder(Protocol.MAX_FRAME_BYTES, 0, LENGTH_BYTES, 0, LENGTH_BYTES));
        pipeline.addLast("frame-encoder", new LengthFieldPrepender(LENGTH_BYTES));
    }
}
