package kolo.protocol.message;

import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.state.NpcShare;

/**
 * Повідомлення клієнта серверу.
 *
 * <p>Клієнт буває щонайбільше в одній сесії: запит, що приводить в іншу ({@link CreateLobby}, {@link JoinLobby},
 * {@link Rejoin}), спершу полишає попередню, як і {@link Leave}. Після виходу повідомлень попередньої сесії клієнт
 * більше не отримує.
 */
public sealed interface ClientMessage
        permits ClientMessage.Hello,
                ClientMessage.ListLobbies,
                ClientMessage.CreateLobby,
                ClientMessage.JoinLobby,
                ClientMessage.StartGame,
                ClientMessage.Rejoin,
                ClientMessage.Leave,
                ClientMessage.Ready {

    /** Найдовший токен гравця, символів. */
    int MAX_TOKEN_LENGTH = 128;

    /**
     * Перше повідомлення з'єднання: клієнт називає свою версію протоколу й хеш контенту. Сервер відповідає {@link
     * ServerMessage.Welcome} або {@link ServerMessage.Error} з {@code VERSION_MISMATCH} ({@link Handshake}).
     *
     * @param protocolVersion версія протоколу клієнта
     * @param contentHash хеш контенту клієнта ({@code ContentPack.hash()})
     */
    record Hello(int protocolVersion, String contentHash) implements ClientMessage {

        public Hello {
            Checks.inRange("protocol_version", protocolVersion, 1, Integer.MAX_VALUE);
            Checks.notBlank("content_hash", contentHash);
        }
    }

    /** Список відкритих лобі сервера; відповідь — {@link ServerMessage.Lobbies}. */
    record ListLobbies() implements ClientMessage {}

    /**
     * Створити нову сесію з цим клієнтом-хостом. Відповідь — {@link ServerMessage.Joined}, потім {@link
     * ServerMessage.Lobby}; світ генерується, коли хост почне гру ({@link StartGame}).
     *
     * @param nickname нікнейм хоста
     * @param seed seed світу (GD §3.4)
     * @param npcShare частка NPC-держав
     */
    record CreateLobby(String nickname, long seed, NpcShare npcShare) implements ClientMessage {

        public CreateLobby {
            Nicknames.check("nickname", nickname);
            Objects.requireNonNull(npcShare, "npcShare");
        }
    }

    /**
     * Приєднатися до лобі. Відповідь — {@link ServerMessage.Joined}, потім усім у лобі — {@link ServerMessage.Lobby};
     * або {@link ServerMessage.Error}: сесії немає ({@code NOT_FOUND}), гра вже почалася ({@code LOBBY_CLOSED}), лобі
     * повне ({@code LOBBY_FULL}), нікнейм зайнятий ({@code NICKNAME_TAKEN}).
     *
     * @param session номер сесії з {@link LobbyInfo}
     */
    record JoinLobby(long session, String nickname) implements ClientMessage {

        public JoinLobby {
            Checks.inRange("session", session, 1, Long.MAX_VALUE);
            Nicknames.check("nickname", nickname);
        }
    }

    /**
     * Хост починає гру: гравці — усі, хто зараз у лобі, у порядку приєднання; гравець {@code n}-м за порядком отримує
     * державу {@code n}. Сервер генерує світ і надсилає всім гравцям карту, {@link ServerMessage.Players} і фази
     * першого року. Не хост — {@code FORBIDDEN}, гру вже почато — {@code LOBBY_CLOSED}.
     */
    record StartGame() implements ClientMessage {}

    /**
     * Повернутися до своєї держави в сесії, що вже йде (після розриву з'єднання). Відповідь — {@link
     * ServerMessage.Joined}, карта, {@link ServerMessage.Players} і поточна фаза; невірний гравець чи токен —
     * {@code UNAUTHORIZED}, сесії немає — {@code NOT_FOUND}.
     *
     * @param session номер сесії з {@link ServerMessage.Joined}
     * @param player номер гравця з {@link ServerMessage.Joined}
     * @param token токен гравця з {@link ServerMessage.Joined}
     */
    record Rejoin(long session, int player, String token) implements ClientMessage {

        public Rejoin {
            Checks.inRange("session", session, 1, Long.MAX_VALUE);
            Checks.inRange("player", player, 1, Integer.MAX_VALUE);
            Checks.notBlank("token", token);
            Checks.inRange("token", token.length(), 1, MAX_TOKEN_LENGTH);
        }
    }

    /**
     * Полишити сесію. З лобі гравець іде зовсім (якщо пішов хост, хостом стає наступний за порядком); з гри, що йде,
     * — лише від'єднується: держава лишається його, повернутися можна з токеном ({@link Rejoin}).
     */
    record Leave() implements ClientMessage {}

    /**
     * Гравець закінчив накази року й натиснув «Готово». Коли готові всі гравці сесії на зв'язку, сервер розв'язує рік.
     * Не той рік або не фаза наказів — {@link ServerMessage.Error} з {@code PHASE_CLOSED}.
     *
     * @param turn рік, накази якого закінчено (хід, не календарний рік)
     */
    record Ready(int turn) implements ClientMessage {

        public Ready {
            Checks.inRange("turn", turn, 0, Integer.MAX_VALUE);
        }
    }
}
