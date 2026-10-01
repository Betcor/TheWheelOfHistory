package kolo.client.net;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import kolo.engine.state.NpcShare;
import kolo.protocol.codec.ProtocolPipeline;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.Handshake;

/**
 * З'єднання клієнта з сервером — вбудованим ({@code LocalAddress}) чи віддаленим (TCP): той самий протокол і ті самі
 * повідомлення. Має власний event loop (один потік-демон), не спільний із сервером.
 *
 * <p>Методи повертають {@link CompletableFuture}: UI не чекає мережі у своєму потоці. Помилки запиту —
 * {@link ServerErrorException} (сервер відповів помилкою), {@link kolo.engine.error.GameException} (пошкоджене
 * повідомлення, інша версія), {@link ConnectionClosedException} (розрив).
 */
public final class ServerConnection implements AutoCloseable {

    private final Channel channel;
    private final EventLoopGroup group;
    private final ConnectionHandler handler;

    private ServerConnection(Channel channel, EventLoopGroup group, ConnectionHandler handler) {
        this.channel = channel;
        this.group = group;
        this.handler = handler;
    }

    /**
     * З'єднується й вітається.
     *
     * @param address адреса вбудованого ({@code LocalAddress}) чи віддаленого (TCP) сервера
     * @param contentHash хеш контенту клієнта
     * @return з'єднання після привітання сервера; з різними версіями чи контентом — помилка
     */
    public static CompletableFuture<ServerConnection> connect(SocketAddress address, String contentHash) {
        boolean local = address instanceof LocalAddress;
        IoHandlerFactory io = local ? LocalIoHandler.newFactory() : NioIoHandler.newFactory();
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, new DefaultThreadFactory("kolo-client", true), io);
        ConnectionHandler handler = new ConnectionHandler(contentHash);
        CompletableFuture<ServerConnection> result = new CompletableFuture<>();
        ChannelFuture connecting = new Bootstrap()
                .group(group)
                .channel(local ? LocalChannel.class : NioSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel channel) {
                        ProtocolPipeline.client(channel.pipeline());
                        channel.pipeline().addLast("conversation", handler);
                    }
                })
                .connect(address);
        connecting.addListener(future -> {
            if (!future.isSuccess()) {
                shutdown(group);
                result.completeExceptionally(new ConnectionClosedException("не вдалося з'єднатися", future.cause()));
                return;
            }
            ServerConnection connection = new ServerConnection(connecting.channel(), group, handler);
            handler.welcomed().whenComplete((ok, error) -> {
                if (error == null) {
                    result.complete(connection);
                } else {
                    connection.close();
                    result.completeExceptionally(error);
                }
            });
            connecting.channel().writeAndFlush(Handshake.hello(contentHash));
        });
        return result;
    }

    /**
     * Просить сервер створити світ, отримує його карту частинами й чекає прийому наказів першого року.
     *
     * @return карта й поточний рік; поки запит не завершено, новий завершується {@link IllegalStateException}
     */
    public CompletableFuture<GameStart> createWorld(long seed, int players, NpcShare npcShare) {
        ClientMessage.CreateWorld request = new ClientMessage.CreateWorld(seed, players, npcShare);
        CompletableFuture<GameStart> result = new CompletableFuture<>();
        execute(result, () -> handler.createWorld(channel, request, result));
        return result;
    }

    /**
     * «Готово» для року {@code turn}: чекає, доки сервер розв'яже рік і почне прийом наказів наступного.
     *
     * @return новий поточний рік
     */
    public CompletableFuture<Integer> endYear(int turn) {
        ClientMessage.Ready request = new ClientMessage.Ready(turn);
        CompletableFuture<Integer> result = new CompletableFuture<>();
        execute(result, () -> handler.endYear(channel, request, result));
        return result;
    }

    private void execute(CompletableFuture<?> result, Runnable task) {
        try {
            channel.eventLoop().execute(task);
        } catch (RejectedExecutionException e) {
            result.completeExceptionally(new ConnectionClosedException("з'єднання закрито", e));
        }
    }

    /** Чи відкрите з'єднання. */
    public boolean isOpen() {
        return channel.isActive();
    }

    /** Закриває з'єднання й зупиняє його потік; запити, що чекають, завершуються {@link ConnectionClosedException}. */
    @Override
    public void close() {
        // Без очікування: close() викликається й з event loop цього ж з'єднання. Плавна зупинка групи
        // дозакриває канал, тож запити, що чекають, отримають розрив.
        channel.close();
        shutdown(group);
    }

    private static void shutdown(EventLoopGroup group) {
        group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
    }
}
