package kolo.protocol.message;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.GameException;
import kolo.engine.error.ValidationException;
import kolo.engine.state.NpcShare;
import kolo.engine.view.CellView;
import kolo.engine.view.CountryView;

/** Повідомлення сервера клієнтові. */
public sealed interface ServerMessage
        permits ServerMessage.Welcome,
                ServerMessage.Error,
                ServerMessage.Lobbies,
                ServerMessage.Joined,
                ServerMessage.Lobby,
                ServerMessage.Players,
                ServerMessage.MapStart,
                ServerMessage.MapCells,
                ServerMessage.Phase {

    /**
     * Відповідь на {@link ClientMessage.Hello}: версії збіглися, з'єднання відкрите.
     *
     * @param protocolVersion версія протоколу сервера
     * @param contentHash хеш контенту сервера
     */
    record Welcome(int protocolVersion, String contentHash) implements ServerMessage {

        public Welcome {
            Checks.inRange("protocol_version", protocolVersion, 1, Integer.MAX_VALUE);
            Checks.notBlank("content_hash", contentHash);
        }
    }

    /**
     * Помилка: клієнт показує гравцеві текст за ключем {@link ErrorCode#key()} з подробицями.
     *
     * <p>Подробиці — як у {@link GameException#details()}, але цілі числа завжди {@link Long}: на дроті JSON не
     * розрізняє {@code int} і {@code long}, а повідомлення, прочитане назад, мусить дорівнювати надісланому. Нецілі
     * числа стають рядками.
     *
     * @param code код помилки
     * @param details подробиці; значення — {@link String}, {@link Long} або {@link Boolean}
     */
    record Error(ErrorCode code, SortedMap<String, Object> details) implements ServerMessage {

        /** @throws ValidationException якщо бракує обов'язкових подробиць коду ({@link ErrorCode#requiredDetails()}) */
        public Error {
            Objects.requireNonNull(code, "code");
            TreeMap<String, Object> normalized = new TreeMap<>();
            details.forEach((key, value) -> normalized.put(Objects.requireNonNull(key, "ключ подробиць"), wire(value)));
            for (String required : code.requiredDetails()) {
                if (!normalized.containsKey(required)) {
                    throw new ValidationException(
                            ErrorCode.MISSING_DEFINITION, ErrorDetails.of("field", "details", "value", required));
                }
            }
            details = Collections.unmodifiableSortedMap(normalized);
        }

        /** Помилка для клієнта з винятку гри. */
        public static Error of(GameException exception) {
            return new Error(exception.code(), exception.details());
        }

        private static Object wire(Object value) {
            return switch (value) {
                case String text -> text;
                case Boolean flag -> flag;
                case Long number -> number;
                case Integer number -> number.longValue();
                case Short number -> number.longValue();
                case Byte number -> number.longValue();
                case null -> "null";
                default -> String.valueOf(value);
            };
        }
    }

    /**
     * Відкриті лобі сервера — відповідь на {@link ClientMessage.ListLobbies}.
     *
     * @param lobbies лобі за номером сесії
     */
    record Lobbies(List<LobbyInfo> lobbies) implements ServerMessage {

        public Lobbies {
            lobbies = List.copyOf(lobbies);
        }
    }

    /**
     * Клієнт увійшов у сесію гравцем — відповідь на {@link ClientMessage.CreateLobby}, {@link ClientMessage.JoinLobby}
     * і {@link ClientMessage.Rejoin}. Токен — лише цьому клієнтові: з ним гравець повертається до своєї держави; сервер
     * зберігає лише його хеш.
     *
     * @param session номер сесії
     * @param player номер гравця в сесії
     * @param token токен гравця
     */
    record Joined(long session, int player, String token) implements ServerMessage {

        public Joined {
            Checks.inRange("session", session, 1, Long.MAX_VALUE);
            Checks.inRange("player", player, 1, Integer.MAX_VALUE);
            Checks.notBlank("token", token);
            Checks.inRange("token", token.length(), 1, ClientMessage.MAX_TOKEN_LENGTH);
        }
    }

    /**
     * Стан лобі — усім у лобі після кожної зміни (приєднався, пішов, змінився хост).
     *
     * @param session номер сесії
     * @param seed seed світу
     * @param npcShare частка NPC-держав
     * @param players гравці в порядку приєднання; хост — рівно один
     */
    record Lobby(long session, long seed, NpcShare npcShare, List<PlayerInfo> players) implements ServerMessage {

        public Lobby {
            Checks.inRange("session", session, 1, Long.MAX_VALUE);
            Objects.requireNonNull(npcShare, "npcShare");
            players = List.copyOf(players);
            Checks.inRange("players", players.size(), 1, Integer.MAX_VALUE);
            Checks.inRange(
                    "hosts", (int) players.stream().filter(PlayerInfo::host).count(), 1, 1);
        }
    }

    /**
     * Гравці гри, що йде, — усім гравцям на зв'язку після кожної зміни (від'єднався, повернувся, натиснув «Готово»)
     * і на початку кожного року.
     *
     * @param players гравці за номером
     */
    record Players(List<PlayerInfo> players) implements ServerMessage {

        public Players {
            players = List.copyOf(players);
            Checks.inRange("players", players.size(), 1, Integer.MAX_VALUE);
        }
    }

    /**
     * Початок карти світу: усе, крім комірок. Далі — частини {@link MapCells} по порядку, доки не прийдуть усі
     * {@code cellCount} комірок ({@link MapAssembler}).
     *
     * @param seed seed світу
     * @param width ширина карти в одиницях сітки
     * @param height висота карти в одиницях сітки
     * @param cellCount скільки комірок прийде частинами
     * @param countries держави за номером
     */
    record MapStart(long seed, int width, int height, int cellCount, List<CountryView> countries)
            implements ServerMessage {

        public MapStart {
            Checks.inRange("width", width, 1, Integer.MAX_VALUE);
            Checks.inRange("height", height, 1, Integer.MAX_VALUE);
            Checks.inRange("cell_count", cellCount, 1, Integer.MAX_VALUE);
            countries = List.copyOf(countries);
        }
    }

    /**
     * Частина комірок карти.
     *
     * @param first номер першої комірки частини; частини йдуть підряд без пропусків
     * @param cells комірки від {@code first}; щонайменше одна
     */
    record MapCells(int first, List<CellView> cells) implements ServerMessage {

        public MapCells {
            Checks.inRange("first", first, 0, Integer.MAX_VALUE);
            cells = List.copyOf(cells);
            Checks.inRange("cells", cells.size(), 1, Integer.MAX_VALUE);
        }
    }

    /**
     * Сесія перейшла до фази року. Порядок фаз: {@link YearPhase#START_OF_YEAR} і {@link YearPhase#ORDERS} року {@code
     * N}, потім {@link YearPhase#RESOLVING} і {@link YearPhase#REPORT} того самого року {@code N}, далі — рік {@code N +
     * 1}.
     *
     * @param turn рік фази (хід, не календарний рік)
     * @param phase фаза
     */
    record Phase(int turn, YearPhase phase) implements ServerMessage {

        public Phase {
            Checks.inRange("turn", turn, 0, Integer.MAX_VALUE);
            Objects.requireNonNull(phase, "phase");
        }
    }
}
