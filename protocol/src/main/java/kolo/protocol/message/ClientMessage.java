package kolo.protocol.message;

import java.util.Objects;
import java.util.Optional;
import kolo.engine.error.Checks;
import kolo.engine.state.NpcShare;
import kolo.engine.state.TurnTimer;

/**
 * Повідомлення клієнта серверу.
 *
 * <p>Клієнт буває щонайбільше в одній сесії: запит, що приводить в іншу ({@link CreateLobby}, {@link JoinLobby},
 * {@link Rejoin}, {@link LoadWorld}), спершу полишає попередню, як і {@link Leave}. Після виходу повідомлень попередньої сесії клієнт
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
                ClientMessage.Ready,
                ClientMessage.ListWorlds,
                ClientMessage.LoadWorld,
                ClientMessage.AssignSeat,
                ClientMessage.SetTimer,
                ClientMessage.EndYear,
                ClientMessage.Resume {

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
     * Повернутися до своєї держави в сесії, що вже йде (після розриву з'єднання), або на своє місце в лобі. Відповідь —
     * {@link ServerMessage.Joined}, далі в грі — карта, {@link ServerMessage.Players} і поточна фаза, у лобі — {@link
     * ServerMessage.Lobby}; невірний гравець чи токен — {@code UNAUTHORIZED}, сесії немає — {@code NOT_FOUND}.
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
     * Гравець закінчив накази року й натиснув «Готово». Коли готові всі гравці сесії на зв'язку, сервер розв'язує рік
     * (з таймером — і коли вийшов час, {@link ServerMessage.Phase#timeLeftMillis()}). Не той рік або не фаза наказів —
     * {@link ServerMessage.Error} з {@code PHASE_CLOSED}.
     *
     * @param turn рік, накази якого закінчено (хід, не календарний рік)
     */
    record Ready(int turn) implements ClientMessage {

        public Ready {
            Checks.inRange("turn", turn, 0, Integer.MAX_VALUE);
        }
    }

    /** Збережені світи в теці сервера; відповідь — {@link ServerMessage.Worlds}. */
    record ListWorlds() implements ClientMessage {}

    /**
     * Завантажити світ із теки сервера в нову сесію з цим клієнтом-хостом. Гра продовжиться з останнього збереженого
     * року, коли хост почне її ({@link StartGame}). Відповідь — {@link ServerMessage.Joined}, потім {@link
     * ServerMessage.Lobby}; або {@link ServerMessage.Error}: світу немає ({@code NOT_FOUND}), його вже відкрито ({@code
     * WORLD_IN_USE}), файл не прочитати, світ іншого контенту ({@code SAVE_CONTENT_MISMATCH}), токен не цього світу
     * ({@code UNAUTHORIZED}).
     *
     * <p>З токеном свого місця ({@code seat}) хост одразу сідає на нього; без токена — заходить гостем під нікнеймом і
     * віддає місце собі сам ({@link AssignSeat}). Без токена світ завантажує лише клієнт того самого процесу, що й
     * сервер (вбудований сервер): з мережі чужий світ не відкрити.
     *
     * @param world ім'я світу з {@link WorldInfo}
     * @param nickname нікнейм — якщо клієнт зайде гостем
     * @param seat місце в цьому світі, якщо клієнт зберіг його токен
     */
    record LoadWorld(String world, String nickname, Optional<PlayerToken> seat) implements ClientMessage {

        public LoadWorld {
            WorldInfo.checkName(world);
            Nicknames.check("nickname", nickname);
            Objects.requireNonNull(seat, "seat");
        }
    }

    /**
     * Хост віддає гостеві лобі завантаженого світу вільне місце гравця. Гість отримує {@link ServerMessage.Joined} з
     * номером місця й новим токеном, усі — {@link ServerMessage.Lobby}. Не хост — {@code FORBIDDEN}, гостя чи місця немає
     * — {@code NOT_FOUND}, місце зайняте — {@code SEAT_TAKEN}, нікнейм гостя вже має інший гравець світу — {@code
     * NICKNAME_TAKEN}.
     *
     * @param guest номер гостя з {@link ServerMessage.Lobby}
     * @param seat номер гравця світу, чиє місце вільне
     */
    record AssignSeat(int guest, int seat) implements ClientMessage {

        public AssignSeat {
            Checks.inRange("guest", guest, 1, Integer.MAX_VALUE);
            Checks.inRange("seat", seat, 1, Integer.MAX_VALUE);
        }
    }

    /**
     * Хост обирає таймер ходу в лобі (GD §6.1) — один із {@link ServerMessage.Lobby#timers()}. Усі в лобі отримують
     * {@link ServerMessage.Lobby} з новим {@link LobbySetup#timer()}. Не хост — {@code FORBIDDEN}, гру вже почато —
     * {@code LOBBY_CLOSED}, такого варіанта немає — {@code VALUE_OUT_OF_RANGE}.
     */
    record SetTimer(TurnTimer timer) implements ClientMessage {

        public SetTimer {
            Objects.requireNonNull(timer, "timer");
        }
    }

    /**
     * Хост завершує рік, не чекаючи «Готово» всіх (GD §6.1): рік розв'язується одразу, за гравців без «Готово» діє
     * автопілот (GD §6.3). Не хост — {@code FORBIDDEN}, не той рік або не фаза наказів — {@code PHASE_CLOSED}.
     *
     * @param turn рік, який завершити (хід, не календарний рік)
     */
    record EndYear(int turn) implements ClientMessage {

        public EndYear {
            Checks.inRange("turn", turn, 0, Integer.MAX_VALUE);
        }
    }

    /**
     * Хост відновлює сесію, призупинену через помилку року ({@link YearPhase#PAUSED}): той самий рік знову у фазі
     * наказів, «Готово» скинуто, таймер ходу (якщо є) рахує повний час від цієї миті. Не хост — {@code FORBIDDEN},
     * сесія не на паузі або не той рік — {@code PHASE_CLOSED}.
     *
     * @param turn рік на паузі (хід, не календарний рік)
     */
    record Resume(int turn) implements ClientMessage {

        public Resume {
            Checks.inRange("turn", turn, 0, Integer.MAX_VALUE);
        }
    }
}
