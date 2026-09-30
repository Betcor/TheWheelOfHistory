package kolo.protocol.codec;

import kolo.engine.error.ProtocolException;
import kolo.protocol.message.ClientMessage;
import kolo.protocol.message.ServerMessage;

/**
 * Повідомлення протоколу в JSON і назад. Читання суворе: невідомий {@code type} чи поле, відсутнє поле, дублікат
 * ключа, не той тип чи невалідне значення — {@link ProtocolException} ({@code PROTOCOL_ERROR}) з місцем усередині
 * повідомлення; прочитане повідомлення дорівнює записаному.
 */
public final class MessageJson {

    private MessageJson() {}

    /** UTF-8 JSON повідомлення клієнта. */
    public static byte[] write(ClientMessage message) {
        return MessageWriter.write(message);
    }

    /** UTF-8 JSON повідомлення сервера. */
    public static byte[] write(ServerMessage message) {
        return MessageWriter.write(message);
    }

    /** @throws ProtocolException якщо це не валідне повідомлення клієнта */
    public static ClientMessage readClient(byte[] json) {
        return MessageReader.client(json);
    }

    /** @throws ProtocolException якщо це не валідне повідомлення сервера */
    public static ServerMessage readServer(byte[] json) {
        return MessageReader.server(json);
    }
}
