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

/**
 * Клієнтський бік розмови: рукостискання й запит карти. Працює лише в event loop свого каналу, тож стан без
 * блокувань. Одночасно — щонайбільше один запит карти.
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
    private CompletableFuture<MapView> map;
    private MapAssembler assembler;

    ConnectionHandler(String contentHash) {
        this.contentHash = Objects.requireNonNull(contentHash, "contentHash");
    }

    /** Завершується, коли сервер привітав клієнта й версії збіглися. */
    CompletableFuture<Void> welcomed() {
        return welcomed;
    }

    /** Надсилає запит світу; викликати з event loop каналу. */
    void createWorld(Channel channel, ClientMessage.CreateWorld request, CompletableFuture<MapView> result) {
        if (!channel.isActive()) {
            result.completeExceptionally(new ConnectionClosedException("з'єднання закрито"));
            return;
        }
        if (map != null) {
            result.completeExceptionally(new IllegalStateException("попередній світ ще не отримано"));
            return;
        }
        map = result;
        assembler = new MapAssembler();
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
                requireMap().add(cells).ifPresent(received -> {
                    CompletableFuture<MapView> done = map;
                    map = null;
                    assembler = null;
                    done.complete(received);
                });
        }
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

    /** Завершує помилкою те, що чекає: до привітання — привітання, після — запит карти. */
    private void fail(Throwable cause) {
        if (!welcomed.isDone()) {
            welcomed.completeExceptionally(cause);
        } else if (map != null) {
            CompletableFuture<MapView> pending = map;
            map = null;
            assembler = null;
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
