package kolo.client.net;

import java.util.List;
import kolo.protocol.message.PlayerInfo;
import kolo.protocol.message.ServerMessage;

/**
 * Події сесії, які сервер надсилає без запиту: зміни лобі, початок гри, гравці, фази року, помилки й розрив. Методи
 * викликаються в потоці з'єднання ({@code kolo-client}), по черзі й у порядку повідомлень; UI має перейти у свій
 * потік сам.
 */
public interface SessionListener {

    /** Слухач, що нічого не робить. */
    SessionListener NONE = new SessionListener() {};

    /**
     * Хост лобі завантаженого світу віддав цьому гостеві місце гравця: новий номер і токен у тій самій сесії.
     */
    default void joined(ServerMessage.Joined joined) {}

    /** Стан лобі змінився. */
    default void lobby(ServerMessage.Lobby lobby) {}

    /** Гра почалася або клієнт повернувся в неї: карта зібрана, рік і фаза — поточні. */
    default void gameStarted(GameStart start) {}

    /** Список гравців гри змінився. */
    default void players(List<PlayerInfo> players) {}

    /** Нова фаза року після початку гри. */
    default void phase(ServerMessage.Phase phase) {}

    /** Помилка, на яку не чекав жоден запит (не вдалося почати гру, рік не розв'язано, «Готово» запізнилося). */
    default void error(ServerMessage.Error error) {}

    /** З'єднання закрито. */
    default void disconnected() {}
}
