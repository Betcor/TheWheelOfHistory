package kolo.client.net;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import kolo.engine.error.GameException;
import kolo.engine.view.CountryCard;
import kolo.engine.view.MapView;
import kolo.protocol.ProtocolErrors;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.Handshake;
import kolo.protocol.message.MapAssembler;
import kolo.protocol.message.ServerMessage;

/**
 * Клієнтський бік розмови. Працює лише в event loop свого каналу, тож стан без блокувань.
 *
 * <p>Запити з відповіддю — списки лобі ({@link ServerMessage.Lobbies}) і світів ({@link ServerMessage.Worlds}) та вхід
 * у сесію ({@link ServerMessage.Joined}); одночасно — щонайбільше один. {@link ServerMessage.Joined} без запиту — хост
 * віддав цьому гостеві місце: подія {@link SessionListener#joined}. Решта повідомлень — події сесії для {@link SessionListener}: стан лобі, гравці, карта
 * (коли зібрано карту й прийшла перша фаза — {@link SessionListener#gameStarted}), фази року.
 *
 * <p>Помилка сервера ({@link ServerMessage.Error}) завершує запит, що чекає, {@link ServerErrorException}, а без
 * запиту — подія {@link SessionListener#error}; пошкоджене повідомлення чи порушений порядок — {@link
 * kolo.engine.error.ProtocolException} і закриття; розрив — {@link ConnectionClosedException} для запиту й подія
 * {@link SessionListener#disconnected}.
 */
final class ConnectionHandler extends SimpleChannelInboundHandler<ServerMessage> {

    /** Місце помилок порядку розмови. */
    static final String CONVERSATION = "conversation";

    private final String contentHash;
    private final SessionListener listener;
    private final CompletableFuture<Void> welcomed = new CompletableFuture<>();
    private Pending<?> pending;
    private MapAssembler assembler;
    private MapView map;
    /** Картка своєї держави: приходить після карти й перед фазою. */
    private CountryCard card;

    private boolean inGame;

    ConnectionHandler(String contentHash, SessionListener listener) {
        this.contentHash = Objects.requireNonNull(contentHash, "contentHash");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    /** Завершується, коли сервер привітав клієнта й версії збіглися. */
    CompletableFuture<Void> welcomed() {
        return welcomed;
    }

    /** Надсилає запит і чекає відповіді типу {@code reply}; викликати з event loop каналу. */
    <T extends ServerMessage> void request(
            Channel channel, ClientMessage request, Class<T> reply, CompletableFuture<T> result) {
        if (!channel.isActive()) {
            result.completeExceptionally(new ConnectionClosedException("з'єднання закрито"));
            return;
        }
        if (pending != null) {
            result.completeExceptionally(new IllegalStateException("попередній запит ще не завершено"));
            return;
        }
        pending = new Pending<>(reply, result);
        send(channel, request);
    }

    /** Надсилає повідомлення без відповіді; помилку сервера отримає слухач. Викликати з event loop каналу. */
    void send(Channel channel, ClientMessage message) {
        channel.writeAndFlush(message).addListener(future -> {
            if (!future.isSuccess()) {
                failPending(new ConnectionClosedException("запит не надіслано", future.cause()));
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
            case ServerMessage.Error error -> {
                if (!welcomed.isDone()) {
                    welcomed.completeExceptionally(new ServerErrorException(error));
                } else if (pending != null) {
                    failPending(new ServerErrorException(error));
                } else {
                    listener.error(error);
                }
            }
            case ServerMessage.Lobbies lobbies -> reply(lobbies);
            case ServerMessage.Worlds worlds -> reply(worlds);
            case ServerMessage.Joined joined -> {
                if (pending == null || !pending.type().isInstance(joined)) {
                    // Без запиту — хост лобі віддав гостеві місце гравця: та сама сесія, новий номер і токен.
                    listener.joined(joined);
                    return;
                }
                // Нова сесія: що було з попередньою, вже не важить.
                inGame = false;
                assembler = null;
                map = null;
                card = null;
                reply(joined);
            }
            case ServerMessage.Lobby lobby -> listener.lobby(lobby);
            case ServerMessage.Players players -> listener.players(players.players());
            case ServerMessage.MapStart start -> {
                assembler = new MapAssembler();
                map = null;
                card = null;
                assembler.start(start);
            }
            case ServerMessage.MapCells cells -> {
                if (assembler == null) {
                    throw ProtocolErrors.malformed(CONVERSATION, "map_not_started");
                }
                assembler.add(cells).ifPresent(done -> {
                    map = done;
                    assembler = null;
                });
            }
            case ServerMessage.OwnCountry own -> {
                if (map == null) {
                    throw ProtocolErrors.malformed(CONVERSATION, "card_without_map");
                }
                card = own.card();
            }
            case ServerMessage.Phase phase -> phase(phase);
        }
    }

    private void phase(ServerMessage.Phase phase) {
        if (map != null) {
            if (card == null) {
                throw ProtocolErrors.malformed(CONVERSATION, "phase_without_card");
            }
            MapView started = map;
            CountryCard own = card;
            map = null;
            card = null;
            inGame = true;
            listener.gameStarted(new GameStart(started, own, phase));
        } else if (inGame) {
            listener.phase(phase);
        } else {
            throw ProtocolErrors.malformed(CONVERSATION, "phase_without_map");
        }
    }

    private <T extends ServerMessage> void reply(T message) {
        if (pending == null || !pending.type().isInstance(message)) {
            throw ProtocolErrors.malformed(CONVERSATION, "unexpected_reply");
        }
        Pending<?> done = pending;
        pending = null;
        done.complete(message);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Throwable reason = gameCause(cause);
        if (!welcomed.isDone()) {
            welcomed.completeExceptionally(reason);
        }
        failPending(reason);
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ConnectionClosedException closed = new ConnectionClosedException("сервер закрив з'єднання");
        boolean wasWelcomed = welcomed.isDone() && !welcomed.isCompletedExceptionally();
        welcomed.completeExceptionally(closed);
        failPending(closed);
        if (wasWelcomed) {
            listener.disconnected();
        }
        super.channelInactive(ctx);
    }

    private void failPending(Throwable cause) {
        if (pending != null) {
            Pending<?> failed = pending;
            pending = null;
            failed.result().completeExceptionally(cause);
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

    /** Запит, що чекає відповіді свого типу. */
    private record Pending<T extends ServerMessage>(Class<T> type, CompletableFuture<T> result) {

        void complete(ServerMessage message) {
            result.complete(type.cast(message));
        }
    }
}
