package kolo.protocol.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import java.util.List;
import java.util.function.Function;
import kolo.protocol.Protocol;
import kolo.protocol.ProtocolErrors;

/**
 * Фрейм ↔ повідомлення для однієї сторони: вхідні {@code I} читаються з фрейму, вихідні {@code O} пишуться у фрейм.
 *
 * @param <I> вхідні повідомлення
 * @param <O> вихідні повідомлення
 */
final class MessageCodec<I, O> extends MessageToMessageCodec<ByteBuf, O> {

    private final Function<byte[], I> read;
    private final Function<O, byte[]> write;

    MessageCodec(Class<O> outbound, Function<byte[], I> read, Function<O, byte[]> write) {
        super(ByteBuf.class, outbound);
        this.read = read;
        this.write = write;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, O message, List<Object> out) {
        byte[] json = write.apply(message);
        // Інакше отримувач закриє з'єднання на завеликому фреймі, а відправник не знатиме чому.
        if (json.length > Protocol.MAX_FRAME_BYTES) {
            throw ProtocolErrors.malformed("$", "frame_too_large");
        }
        out.add(Unpooled.wrappedBuffer(json));
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf frame, List<Object> out) {
        out.add(read.apply(ByteBufUtil.getBytes(frame)));
    }
}
