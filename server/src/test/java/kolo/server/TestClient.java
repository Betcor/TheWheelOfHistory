package kolo.server;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.SocketAddress;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import kolo.engine.view.MapView;
import kolo.protocol.codec.ProtocolPipeline;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.Handshake;
import kolo.protocol.message.MapAssembler;
import kolo.protocol.message.ServerMessage;

/**
 * Простий клієнт для тестів сервера через справжній транспорт: шле повідомлення й складає відповіді в чергу. Сервер
 * не може залежати від клієнта гри, тож це — його мінімальна копія без логіки запитів.
 */
public final class TestClient implements AutoCloseable {

    private static final long TIMEOUT_SECONDS = 30;

    private final EventLoopGroup group;
    private final Channel channel;
    private final BlockingQueue<ServerMessage> received = new LinkedBlockingQueue<>();

    private TestClient(SocketAddress address) {
        boolean local = address instanceof LocalAddress;
        group = new MultiThreadIoEventLoopGroup(1, local ? LocalIoHandler.newFactory() : NioIoHandler.newFactory());
        channel = new Bootstrap()
                .group(group)
                .channel(local ? LocalChannel.class : NioSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ProtocolPipeline.client(ch.pipeline());
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<ServerMessage>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, ServerMessage message) {
                                received.add(message);
                            }
                        });
                    }
                })
                .connect(address)
                .syncUninterruptibly()
                .channel();
    }

    public static TestClient connect(SocketAddress address) {
        return new TestClient(address);
    }

    /** З'єднання з привітанням. */
    public static TestClient welcomed(SocketAddress address, String contentHash) throws InterruptedException {
        TestClient client = connect(address);
        client.send(Handshake.hello(contentHash));
        ServerMessage reply = client.next();
        if (!(reply instanceof ServerMessage.Welcome welcome)) {
            client.close();
            throw new AssertionError("очікувалося привітання, а прийшло " + reply);
        }
        Handshake.confirm(welcome, contentHash);
        return client;
    }

    public void send(ClientMessage message) {
        channel.writeAndFlush(message).syncUninterruptibly();
    }

    /** Наступне повідомлення сервера. */
    public ServerMessage next() throws InterruptedException {
        ServerMessage message = received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (message == null) {
            throw new AssertionError("сервер не відповів за " + TIMEOUT_SECONDS + " с");
        }
        return message;
    }

    /** Збирає карту з наступних повідомлень. */
    public MapView map() throws InterruptedException {
        MapAssembler assembler = new MapAssembler();
        assembler.start((ServerMessage.MapStart) next());
        Optional<MapView> map = Optional.empty();
        while (map.isEmpty()) {
            map = assembler.add((ServerMessage.MapCells) next());
        }
        return map.orElseThrow();
    }

    /** Чекає, доки сервер закриє з'єднання. */
    public boolean awaitClosed() {
        return channel.closeFuture().awaitUninterruptibly(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public boolean isOpen() {
        return channel.isActive();
    }

    @Override
    public void close() {
        channel.close().syncUninterruptibly();
        group.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
    }
}
