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
import java.util.function.Function;
import kolo.engine.error.GameException;
import kolo.protocol.ProtocolErrors;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.ServerMessage;
import kolo.server.session.ClientSession;
import kolo.server.session.Peer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Останній обробник серверного каналу: передає повідомлення клієнта в {@link ClientSession} і пише відповіді, свої й
 * сесії гри ({@link Peer} цього каналу).
 *
 * <p>Розмова працює не в event loop, а в окремому однопотоковому виконавці з'єднань: перше привітання може чекати
 * завантаження контенту, а event loop тим часом обслуговує інші з'єднання. Один потік — повідомлення з'єднання
 * обробляються по черзі, і розмова не потребує блокувань. Важка робота (генерація світу, рік) — у потоці сесії гри.
 * Запис у канал Netty потокобезпечний і зберігає порядок.
 *
 * <p>Пошкоджене вхідне повідомлення чи завеликий фрейм — {@link ServerMessage.Error} з {@code PROTOCOL_ERROR} і
 * закриття: після них межі фреймів у потоці вже ненадійні.
 */
final class ConnectionHandler extends SimpleChannelInboundHandler<ClientMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionHandler.class);

    private final Function<Peer, ClientSession> sessions;
    private final Executor worker;
    private ClientSession session;

    /**
     * @param sessions розмова для співрозмовника-каналу
     * @param worker однопотоковий виконавець розмов
     */
    ConnectionHandler(Function<Peer, ClientSession> sessions, Executor worker) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.worker = Objects.requireNonNull(worker, "worker");
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        session = sessions.apply((messages, close) -> send(channel, messages, close));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        try {
            worker.execute(session::disconnected);
        } catch (RejectedExecutionException e) {
            // Сервер зупиняється й сам закриває сесії.
        }
        super.channelInactive(ctx);
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
        try {
            session.handle(message);
        } catch (RuntimeException e) {
            // Межа розмови: баг сервера не має вбити потік з'єднань; клієнт побачить розрив з'єднання.
            LOG.error("Збій обробки повідомлення {} від {}", message.getClass().getSimpleName(), channel, e);
            channel.close();
        }
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
