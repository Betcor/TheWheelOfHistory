package kolo.server.transport;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.TooLongFrameException;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import kolo.engine.error.GameException;
import kolo.protocol.ProtocolErrors;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.ServerMessage;
import kolo.server.session.ClientSession;
import kolo.server.session.Reply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Останній обробник серверного каналу: передає повідомлення клієнта в {@link ClientSession} і пише відповіді.
 *
 * <p>Сесія працює не в event loop, а в окремому виконавці: генерація світу триває секунди, а event loop тим часом
 * обслуговує інші з'єднання. Виконавець однопотоковий, тож повідомлення з'єднання обробляються по черзі, а сесія не
 * потребує блокувань. Запис у канал Netty потокобезпечний і зберігає порядок.
 *
 * <p>Пошкоджене вхідне повідомлення чи завеликий фрейм — {@link ServerMessage.Error} з {@code PROTOCOL_ERROR} і
 * закриття: після них межі фреймів у потоці вже ненадійні.
 */
final class ConnectionHandler extends SimpleChannelInboundHandler<ClientMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionHandler.class);

    private final ClientSession session;
    private final Executor worker;

    ConnectionHandler(ClientSession session, Executor worker) {
        this.session = Objects.requireNonNull(session, "session");
        this.worker = Objects.requireNonNull(worker, "worker");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ClientMessage message) {
        Channel channel = ctx.channel();
        try {
            worker.execute(() -> respond(channel, message));
        } catch (RejectedExecutionException e) {
            // Сервер зупиняється: нових запитів не приймаємо.
            channel.close();
        }
    }

    private void respond(Channel channel, ClientMessage message) {
        if (!channel.isActive()) {
            return;
        }
        Reply reply;
        try {
            reply = session.handle(message);
        } catch (RuntimeException e) {
            // Межа сесії: баг сервера не має вбити потік сесій; клієнт побачить розрив з'єднання.
            LOG.error("Збій обробки повідомлення {} від {}", message.getClass().getSimpleName(), channel, e);
            channel.close();
            return;
        }
        send(channel, reply.messages(), reply.close());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Channel channel = ctx.channel();
        if (cause instanceof TooLongFrameException) {
            send(channel, List.of(error(ProtocolErrors.malformed("frame", "frame_too_large"))), true);
            return;
        }
        GameException game = gameCause(cause);
        if (game != null) {
            send(channel, List.of(error(game)), true);
            return;
        }
        if (cause instanceof IOException) {
            // Клієнт обірвав з'єднання — звичайна подія мережі.
            LOG.debug("З'єднання {} обірвано", channel, cause);
        } else {
            LOG.warn("Помилка в каналі {}", channel, cause);
        }
        channel.close();
    }

    private static void send(Channel channel, List<ServerMessage> messages, boolean close) {
        ChannelFuture last = null;
        for (ServerMessage message : messages) {
            last = channel.write(message);
            last.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        }
        channel.flush();
        if (close) {
            // Закриття — після запису: клієнт має отримати причину.
            if (last == null) {
                channel.close();
            } else {
                last.addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

    private static ServerMessage.Error error(GameException exception) {
        return ServerMessage.Error.of(exception);
    }

    /** Помилка гри в ланцюжку причин (кодек загортає її в {@code DecoderException}). */
    private static GameException gameCause(Throwable cause) {
        for (Throwable current = cause; current != null; current = current.getCause()) {
            if (current instanceof GameException game) {
                return game;
            }
        }
        return null;
    }
}
