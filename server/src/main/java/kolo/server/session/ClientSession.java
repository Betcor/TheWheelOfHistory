package kolo.server.session;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import kolo.engine.content.ContentPack;
import kolo.engine.error.GameException;
import kolo.engine.error.ProtocolException;
import kolo.engine.state.WorldState;
import kolo.engine.view.MapViews;
import kolo.protocol.ProtocolErrors;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.Handshake;
import kolo.protocol.message.MapChunks;
import kolo.protocol.message.ServerMessage;

/**
 * Розмова сервера з одним клієнтом без мережі: повідомлення клієнта → {@link Reply}. Транспорт лише доставляє
 * повідомлення сюди й відповіді назад, тож правила розмови перевіряються без Netty.
 *
 * <p>Спершу — рукостискання ({@link Handshake}): будь-яка помилка до нього (інше перше повідомлення, інша версія чи
 * контент, невалідний контент сервера) — {@link ServerMessage.Error} і закриття з'єднання, бо решті повідомлень не
 * можна вірити. Після нього помилка запиту (наприклад, параметри світу поза межами) — лише {@link
 * ServerMessage.Error}: гравець виправить і спробує знову; порушення порядку розмови (повторне привітання) — знову
 * помилка й закриття.
 *
 * <p>Не потокобезпечний: повідомлення одного з'єднання обробляються по черзі в одному потоці.
 */
public final class ClientSession {

    /** Місце помилок порядку розмови. */
    static final String HANDSHAKE = "handshake";

    private final Supplier<ContentPack> content;
    private boolean welcomed;

    public ClientSession(Supplier<ContentPack> content) {
        this.content = Objects.requireNonNull(content, "content");
    }

    /** Чи пройдено рукостискання. */
    public boolean welcomed() {
        return welcomed;
    }

    /**
     * Обробляє повідомлення клієнта. Помилки гри стають {@link ServerMessage.Error}; інші винятки — баг сервера, вони
     * летять далі.
     */
    public Reply handle(ClientMessage message) {
        try {
            return switch (message) {
                case ClientMessage.Hello hello -> hello(hello);
                case ClientMessage.CreateWorld create -> createWorld(create);
            };
        } catch (GameException e) {
            ServerMessage.Error error = ServerMessage.Error.of(e);
            // Порушена розмова (не той порядок повідомлень) далі не має сенсу, як і будь-яка помилка до привітання.
            return welcomed && !(e instanceof ProtocolException) ? Reply.of(List.of(error)) : Reply.closing(error);
        }
    }

    private Reply hello(ClientMessage.Hello hello) {
        if (welcomed) {
            throw ProtocolErrors.malformed(HANDSHAKE, "hello_repeated");
        }
        ServerMessage.Welcome welcome = Handshake.accept(hello, content.get().hash());
        welcomed = true;
        return Reply.of(List.of(welcome));
    }

    private Reply createWorld(ClientMessage.CreateWorld create) {
        requireWelcomed();
        ContentPack pack = content.get();
        WorldState state = NewWorlds.generate(pack, create.seed(), create.players(), create.npcShare());
        return Reply.of(MapChunks.split(MapViews.of(state)));
    }

    private void requireWelcomed() {
        if (!welcomed) {
            throw ProtocolErrors.malformed(HANDSHAKE, "hello_expected");
        }
    }
}
