package kolo.protocol.codec;

/** Значення поля {@code type} — частина протоколу. */
final class MessageTypes {

    static final String HELLO = "hello";
    static final String CREATE_WORLD = "create_world";
    static final String WELCOME = "welcome";
    static final String ERROR = "error";
    static final String MAP_START = "map_start";
    static final String MAP_CELLS = "map_cells";

    private MessageTypes() {}
}
