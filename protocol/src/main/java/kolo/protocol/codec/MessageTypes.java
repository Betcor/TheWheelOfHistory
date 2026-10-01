package kolo.protocol.codec;

/** Значення поля {@code type} — частина протоколу. */
final class MessageTypes {

    static final String HELLO = "hello";
    static final String LIST_LOBBIES = "list_lobbies";
    static final String CREATE_LOBBY = "create_lobby";
    static final String JOIN_LOBBY = "join_lobby";
    static final String START_GAME = "start_game";
    static final String REJOIN = "rejoin";
    static final String LEAVE = "leave";
    static final String READY = "ready";
    static final String WELCOME = "welcome";
    static final String ERROR = "error";
    static final String LOBBIES = "lobbies";
    static final String JOINED = "joined";
    static final String LOBBY = "lobby";
    static final String PLAYERS = "players";
    static final String MAP_START = "map_start";
    static final String MAP_CELLS = "map_cells";
    static final String PHASE = "phase";

    private MessageTypes() {}
}
