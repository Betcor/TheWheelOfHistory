package kolo.client.net;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import kolo.engine.error.GameException;
import kolo.engine.view.MapView;
import kolo.protocol.ProtocolErrors;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.Handshake;
import kolo.protocol.message.MapAssembler;
import kolo.protocol.message.ServerMessage;
import kolo.protocol.message.YearPhase;

/**
 * Клієнтський бік розмови: рукостискання, новий світ і кінець року. Працює лише в event loop свого каналу, тож стан
 * без блокувань. Одночасно — щонайбільше один запит.
 *
 * <p>Новий світ завершується, коли зібрано карту й сервер почав прийом наказів ({@link YearPhase#ORDERS}); кінець
 * року — коли почався прийом наказів наступного року. Фази, на які ніхто не чекає (до карти нового світу — ще від
 * попередньої сесії), пропускаються.
 *
 * <p>Помилка сервера ({@link ServerMessage.Error}) завершує запит, що чекає, {@link ServerErrorException}; пошкоджене
 * повідомлення чи порушений порядок — {@link kolo.engine.error.ProtocolException} і закриття; розрив — {@link
 * ConnectionClosedException} для всього, що чекає.
 */
final class ConnectionHandler extends SimpleChannelInboundHandler<ServerMessage> {

    /** Місце помилок порядку розмови. */
    static final String CONVERSATION = "conversation";

    private final String contentHash;
    private final CompletableFuture<Void> welcomed = new CompletableFuture<>();
    private CompletableFuture<GameStart> world;
    private MapAssembler assembler;
    private MapView received;
    private CompletableFuture<Integer> year;
    private int yearTurn;

    ConnectionHandler(String contentHash) {
        this.contentHash = Objects.requireNonNull(contentHash, "contentHash");
    }

    /** Завершується, коли сервер привітав клієнта й версії збіглися. */
    CompletableFuture<Void> welcomed() {
        return welcomed;
    }

    /** Надсилає запит світу; викликати з event loop каналу. */
    void createWorld(Channel channel, ClientMessage.CreateWorld request, CompletableFuture<GameStart> result) {
        if (refused(channel, result)) {
            return;
        }
        world = result;
        assembler = new MapAssembler();
        received = null;
        send(channel, request);
    }

    /** Надсилає «Готово»; викликати з event loop каналу. */
    void endYear(Channel channel, ClientMessage.Ready request, CompletableFuture<Integer> result) {
        if (refused(channel, result)) {
            return;
        }
        year = result;
        yearTurn = request.turn();
        send(channel, request);
    }

    private boolean refused(Channel channel, CompletableFuture<?> result) {
        if (!channel.isActive()) {
            result.completeExceptionally(new ConnectionClosedException("з'єднання закрито"));
            return true;
        }
        if (world != null || year != null) {
            result.completeExceptionally(new IllegalStateException("попередній запит ще не завершено"));
            return true;
        }
        return false;
    }

    private void send(Channel channel, ClientMessage request) {
        channel.writeAndFlush(request).addListener(future -> {
            if (!future.isSuccess()) {
                fail(new ConnectionClosedException("запит не надіслано", future.cause()));
            }
        });
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ServerMessage message) {
        switch (message) {
            case ServerMessage.Welcome welcome -> {
                if (welcomed.isDone()) {
                    throw ProtocolErrors.malformed(CONVERSATION, "welcome_repeated");
                }
                Handshake.confirm(welcome, contentHash);
                welcomed.complete(null);
            }
            case ServerMessage.Error error -> fail(new ServerErrorException(error));
            case ServerMessage.MapStart start -> requireMap().start(start);
            case ServerMessage.MapCells cells ->
                requireMap().add(cells).ifPresent(map -> {
                    received = map;
                    assembler = null;
                });
            case ServerMessage.Phase phase -> phase(phase);
        }
    }

    private void phase(ServerMessage.Phase phase) {
        if (phase.phase() != YearPhase.ORDERS) {
            return;
        }
        if (world != null && received != null) {
            CompletableFuture<GameStart> done = world;
            GameStart start = new GameStart(received, phase.turn());
            clearWorld();
            done.complete(start);
        } else if (year != null && phase.turn() > yearTurn) {
            CompletableFuture<Integer> done = year;
            year = null;
            done.complete(phase.turn());
        }
    }

    private void clearWorld() {
        world = null;
        assembler = null;
        received = null;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        fail(gameCause(cause));
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        fail(new ConnectionClosedException("сервер закрив з'єднання"));
        super.channelInactive(ctx);
    }

    private MapAssembler requireMap() {
        if (assembler == null) {
            throw ProtocolErrors.malformed(CONVERSATION, "map_not_requested");
        }
        return assembler;
    }

    /** Завершує помилкою те, що чекає: до привітання — привітання, після — запит. */
    private void fail(Throwable cause) {
        if (!welcomed.isDone()) {
            welcomed.completeExceptionally(cause);
        } else if (world != null) {
            CompletableFuture<GameStart> pending = world;
            clearWorld();
            pending.completeExceptionally(cause);
        } else if (year != null) {
            CompletableFuture<Integer> pending = year;
            year = null;
            pending.completeExceptionally(cause);
        }
    }

    /** Помилка гри з ланцюжка причин (кодек загортає її в {@code DecoderException}), інакше — сама причина. */
    private static Throwable gameCause(Throwable cause) {
        for (Throwable current = cause; current != null; current = current.getCause()) {
            if (current instanceof GameException) {
                return current;
            }
        }
        return cause;
    }
}
