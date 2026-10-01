package kolo.server.transport;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import kolo.engine.content.ContentPack;
import kolo.protocol.codec.ProtocolPipeline;
import kolo.server.session.ClientSession;

/**
 * Мережевий сервер гри: приймає з'єднання через {@code LocalChannel} (вбудований сервер — одиночна гра, hot-seat) і
 * TCP (LAN-хост, окремий сервер) з тими самими обробниками протоколу й тією самою {@link ClientSession}.
 *
 * <p>Потоки: свій event loop на кожен транспорт (Local і TCP — різні {@link io.netty.channel.IoHandler}) і один потік
 * сесій ({@code kolo-session}) для обробки повідомлень. Клієнт має власний event loop: у спільному потоці {@code
 * LocalChannel} доставляє повідомлення співрозмовнику синхронно всередині {@code flush} відправника, і відповідь,
 * записана з {@code channelRead}, впирається в захист {@code flush} від повторного входу — лежить у буфері до
 * наступного {@code flush} (ADR 0044). Усі потоки — демони: вбудований сервер не тримає процес клієнта.
 */
public final class GameServer implements AutoCloseable {

    private final Supplier<ContentPack> content;
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(new DefaultThreadFactory("kolo-session", true));
    private final ChannelGroup channels = new DefaultChannelGroup("kolo-server", GlobalEventExecutor.INSTANCE);
    private final List<EventLoopGroup> groups = new ArrayList<>();
    private final CountDownLatch closed = new CountDownLatch(1);
    private EventLoopGroup localGroup;
    private EventLoopGroup tcpGroup;
    private boolean closing;

    private GameServer(Supplier<ContentPack> content) {
        this.content = Objects.requireNonNull(content, "content");
    }

    /**
     * Сервер без жодної адреси; адреси — {@link #bindLocal()} і {@link #bindTcp(InetSocketAddress)}.
     *
     * @param content контент гри; викликається з потоку сесій при першому рукостисканні
     */
    public static GameServer start(Supplier<ContentPack> content) {
        return new GameServer(content);
    }

    /** Слухає нову адресу {@code LocalChannel} у межах процесу. */
    public LocalAddress bindLocal() {
        EventLoopGroup group;
        synchronized (this) {
            requireOpen();
            if (localGroup == null) {
                localGroup = group("kolo-server-local", LocalIoHandler.newFactory());
            }
            group = localGroup;
        }
        return (LocalAddress) bind(group, LocalServerChannel.class, LocalAddress.ANY);
    }

    /**
     * Слухає TCP.
     *
     * @param address адреса й порт; порт 0 — будь-який вільний
     * @return справжня адреса, зокрема обраний порт
     * @throws io.netty.channel.ChannelException якщо адреса зайнята чи недоступна
     */
    public InetSocketAddress bindTcp(InetSocketAddress address) {
        EventLoopGroup group;
        synchronized (this) {
            requireOpen();
            if (tcpGroup == null) {
                tcpGroup = group("kolo-server-tcp", NioIoHandler.newFactory());
            }
            group = tcpGroup;
        }
        return (InetSocketAddress) bind(group, NioServerSocketChannel.class, address);
    }

    /** Чекає {@link #close()} — для окремого сервера, чий головний потік живе, доки живе сервер. */
    public void awaitClose() throws InterruptedException {
        closed.await();
    }

    /** Закриває всі адреси й з'єднання й зупиняє потоки. Повторний виклик нічого не робить. */
    @Override
    public void close() {
        List<EventLoopGroup> toStop;
        synchronized (this) {
            if (closing) {
                return;
            }
            closing = true;
            toStop = List.copyOf(groups);
        }
        channels.close().awaitUninterruptibly();
        worker.shutdownNow();
        for (EventLoopGroup group : toStop) {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).awaitUninterruptibly();
        }
        closed.countDown();
    }

    private SocketAddress bind(EventLoopGroup group, Class<? extends ServerChannel> type, SocketAddress address) {
        Channel channel = new ServerBootstrap()
                .group(group)
                .channel(type)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel child) {
                        channels.add(child);
                        ProtocolPipeline.server(child.pipeline());
                        child.pipeline().addLast("session", new ConnectionHandler(new ClientSession(content), worker));
                    }
                })
                .bind(address)
                .syncUninterruptibly()
                .channel();
        channels.add(channel);
        return channel.localAddress();
    }

    private EventLoopGroup group(String name, IoHandlerFactory factory) {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, new DefaultThreadFactory(name, true), factory);
        groups.add(group);
        return group;
    }

    private void requireOpen() {
        if (closing) {
            throw new IllegalStateException("сервер закрито");
        }
    }
}
