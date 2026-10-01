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
    static final String LIST_WORLDS = "list_worlds";
    static final String LOAD_WORLD = "load_world";
    static final String ASSIGN_SEAT = "assign_seat";
    static final String SET_TIMER = "set_timer";
    static final String END_YEAR = "end_year";
    static final String RESUME = "resume";
    static final String WELCOME = "welcome";
    static final String ERROR = "error";
    static final String LOBBIES = "lobbies";
    static final String JOINED = "joined";
    static final String LOBBY = "lobby";
    static final String PLAYERS = "players";
    static final String MAP_START = "map_start";
    static final String MAP_CELLS = "map_cells";
    static final String PHASE = "phase";
    static final String WORLDS = "worlds";
    static final String OWN_COUNTRY = "own_country";

    /** Значення поля {@code kind} у {@code setup} лобі. */
    static final String NEW_WORLD = "new_world";

    static final String SAVED_WORLD = "saved_world";

    private MessageTypes() {}
}
