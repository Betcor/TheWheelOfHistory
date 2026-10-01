package kolo.server.discovery;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import kolo.engine.error.Checks;
import kolo.protocol.Protocol;
import kolo.protocol.discovery.DiscoveryPacket;
import kolo.protocol.discovery.DiscoveryPackets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Відповідає на запит пошуку ({@link DiscoveryPacket.Query}) напряму відправникові: версія протоколу й TCP-порт гри.
 * Відповідає й клієнтам іншої версії — щоб вони показали «гра іншої версії», а не мовчання. Чуже й пошкоджене
 * мовчки пропускає: датаграми надсилає будь-хто в мережі. Без стану — працює просто в event loop.
 */
public final class DiscoveryResponder extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger LOG = LoggerFactory.getLogger(DiscoveryResponder.class);

    private final byte[] reply;

    /** @param gamePort TCP-порт гри, який називати клієнтам */
    public DiscoveryResponder(int gamePort) {
        Checks.inRange("gamePort", gamePort, 1, 65_535);
        this.reply = DiscoveryPackets.encode(new DiscoveryPacket.Reply(Protocol.VERSION, gamePort));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, DatagramPacket packet) {
        ByteBuf content = packet.content();
        if (content.readableBytes() > DiscoveryPackets.MAX_BYTES) {
            return;
        }
        byte[] bytes = ByteBufUtil.getBytes(content);
        if (DiscoveryPackets.decode(bytes, bytes.length).orElse(null) instanceof DiscoveryPacket.Query) {
            context.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(reply), packet.sender()));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        // Невдала відповідь одному клієнтові (мережа недосяжна) не зупиняє пошук для інших.
        LOG.debug("Помилка UDP-пошуку", cause);
    }
}
