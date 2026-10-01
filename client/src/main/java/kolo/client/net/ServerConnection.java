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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import kolo.engine.state.NpcShare;
import kolo.engine.state.TurnTimer;
import kolo.protocol.codec.ProtocolPipeline;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.Handshake;
import kolo.protocol.message.LobbyInfo;
import kolo.protocol.message.PlayerToken;
import kolo.protocol.message.ServerMessage;
import kolo.protocol.message.WorldInfo;

/**
 * З'єднання клієнта з сервером — вбудованим ({@code LocalAddress}) чи віддаленим (TCP): той самий протокол і ті самі
 * повідомлення. Має власний event loop (один потік-демон), не спільний із сервером.
 *
 * <p>Запити з відповіддю повертають {@link CompletableFuture}: UI не чекає мережі у своєму потоці. Помилки запиту —
 * {@link ServerErrorException} (сервер відповів помилкою), {@link kolo.engine.error.GameException} (пошкоджене
 * повідомлення, інша версія), {@link ConnectionClosedException} (розрив). Решта — події сесії для {@link
 * SessionListener}: початок гри, гравці, фази року, помилки без запиту.
 */
public final class ServerConnection implements AutoCloseable {

    private final SocketAddress address;
    private final Channel channel;
    private final EventLoopGroup group;
    private final ConnectionHandler handler;

    private ServerConnection(SocketAddress address, Channel channel, EventLoopGroup group, ConnectionHandler handler) {
        this.address = address;
        this.channel = channel;
        this.group = group;
        this.handler = handler;
    }

    /**
     * З'єднується й вітається.
     *
     * @param address адреса вбудованого ({@code LocalAddress}) чи віддаленого (TCP) сервера
     * @param contentHash хеш контенту клієнта
     * @param listener події сесії цього з'єднання
     * @return з'єднання після привітання сервера; з різними версіями чи контентом — помилка
     */
    public static CompletableFuture<ServerConnection> connect(
            SocketAddress address, String contentHash, SessionListener listener) {
        boolean local = address instanceof LocalAddress;
        IoHandlerFactory io = local ? LocalIoHandler.newFactory() : NioIoHandler.newFactory();
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, new DefaultThreadFactory("kolo-client", true), io);
        ConnectionHandler handler = new ConnectionHandler(contentHash, listener);
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
            ServerConnection connection = new ServerConnection(address, connecting.channel(), group, handler);
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

    /** Адреса сервера, з яким з'єднано. */
    public SocketAddress address() {
        return address;
    }

    /** Відкриті лобі сервера. */
    public CompletableFuture<List<LobbyInfo>> lobbies() {
        return request(new ClientMessage.ListLobbies(), ServerMessage.Lobbies.class)
                .thenApply(ServerMessage.Lobbies::lobbies);
    }

    /**
     * Створює лобі з цим клієнтом-хостом; стан лобі прийде слухачеві.
     *
     * @return номер сесії, номер гравця й токен для повернення
     */
    public CompletableFuture<ServerMessage.Joined> createLobby(String nickname, long seed, NpcShare npcShare) {
        return request(new ClientMessage.CreateLobby(nickname, seed, npcShare), ServerMessage.Joined.class);
    }

    /** Приєднується до лобі; стан лобі прийде слухачеві. */
    public CompletableFuture<ServerMessage.Joined> joinLobby(long session, String nickname) {
        return request(new ClientMessage.JoinLobby(session, nickname), ServerMessage.Joined.class);
    }

    /**
     * Повертається до своєї держави з токеном, отриманим раніше; карту й поточну фазу отримає слухач ({@link
     * SessionListener#gameStarted}).
     */
    public CompletableFuture<ServerMessage.Joined> rejoin(ServerMessage.Joined credentials) {
        return request(
                new ClientMessage.Rejoin(credentials.session(), credentials.player(), credentials.token()),
                ServerMessage.Joined.class);
    }

    /** Повертається на місце гравця {@code seat} у сесії {@code session} (з токеном, збереженим на диску). */
    public CompletableFuture<ServerMessage.Joined> rejoin(long session, PlayerToken seat) {
        return request(new ClientMessage.Rejoin(session, seat.player(), seat.token()), ServerMessage.Joined.class);
    }

    /** Збережені світи в теці сервера. */
    public CompletableFuture<List<WorldInfo>> worlds() {
        return request(new ClientMessage.ListWorlds(), ServerMessage.Worlds.class)
                .thenApply(ServerMessage.Worlds::worlds);
    }

    /**
     * Завантажує світ у нове лобі з цим клієнтом-хостом; стан лобі прийде слухачеві.
     *
     * @param seat місце в цьому світі, якщо клієнт зберіг його токен; інакше клієнт зайде гостем під нікнеймом
     */
    public CompletableFuture<ServerMessage.Joined> loadWorld(
            String world, String nickname, Optional<PlayerToken> seat) {
        return request(new ClientMessage.LoadWorld(world, nickname, seat), ServerMessage.Joined.class);
    }

    /** Хост віддає гостеві вільне місце; помилку отримає слухач цього з'єднання. */
    public void assignSeat(int guest, int seat) {
        send(new ClientMessage.AssignSeat(guest, seat));
    }

    /** Хост починає гру; карту отримають слухачі всіх гравців, помилку — слухач цього з'єднання. */
    public void startGame() {
        send(new ClientMessage.StartGame());
    }

    /** «Готово» для року {@code turn}; новий рік прийде слухачеві фазами. */
    public void ready(int turn) {
        send(new ClientMessage.Ready(turn));
    }

    /** Хост обирає таймер ходу в лобі; новий стан лобі отримають слухачі, помилку — слухач цього з'єднання. */
    public void setTimer(TurnTimer timer) {
        send(new ClientMessage.SetTimer(timer));
    }

    /** Хост завершує рік {@code turn}, не чекаючи «Готово» всіх; новий рік прийде слухачеві фазами. */
    public void endYear(int turn) {
        send(new ClientMessage.EndYear(turn));
    }

    /** Хост відновлює рік {@code turn}, на якому сесію призупинено; фазу наказів отримає слухач. */
    public void resume(int turn) {
        send(new ClientMessage.Resume(turn));
    }

    /** Полишає сесію. */
    public void leave() {
        send(new ClientMessage.Leave());
    }

    private <T extends ServerMessage> CompletableFuture<T> request(ClientMessage request, Class<T> reply) {
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            channel.eventLoop().execute(() -> handler.request(channel, request, reply, result));
        } catch (RejectedExecutionException e) {
            result.completeExceptionally(new ConnectionClosedException("з'єднання закрито", e));
        }
        return result;
    }

    private void send(ClientMessage message) {
        try {
            channel.eventLoop().execute(() -> handler.send(channel, message));
        } catch (RejectedExecutionException e) {
            // З'єднання закрито: слухач уже отримав розрив.
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
