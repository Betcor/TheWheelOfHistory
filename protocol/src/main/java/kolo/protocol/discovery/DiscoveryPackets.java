package kolo.protocol.discovery;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * Байти датаграм пошуку ({@link DiscoveryPacket}). Формат (big-endian): {@code "KOLO"}, байт типу ({@code 1} — запит,
 * {@code 2} — відповідь), версія протоколу ({@code int}); у відповіді ще TCP-порт ({@code unsigned short}). Запит — 9
 * байтів, відповідь — 11: сервер майже не підсилює підроблені запити.
 */
public final class DiscoveryPackets {

    /** Найбільша датаграма пошуку, байтів: буфер для читання. */
    public static final int MAX_BYTES = 11;

    private static final byte[] MAGIC = "KOLO".getBytes(StandardCharsets.US_ASCII);
    private static final byte QUERY = 1;
    private static final byte REPLY = 2;
    private static final int QUERY_BYTES = 9;
    private static final int REPLY_BYTES = MAX_BYTES;

    private DiscoveryPackets() {}

    /** Байти датаграми. */
    public static byte[] encode(DiscoveryPacket packet) {
        return switch (packet) {
            case DiscoveryPacket.Query query ->
                ByteBuffer.allocate(QUERY_BYTES)
                        .put(MAGIC)
                        .put(QUERY)
                        .putInt(query.version())
                        .array();
            case DiscoveryPacket.Reply reply ->
                ByteBuffer.allocate(REPLY_BYTES)
                        .put(MAGIC)
                        .put(REPLY)
                        .putInt(reply.version())
                        .putShort((short) reply.port())
                        .array();
        };
    }

    /**
     * Читає датаграму. Її може надіслати будь-хто в мережі, тож чуже чи пошкоджене — не помилка, а порожньо.
     *
     * @param length скільки байтів {@code bytes} отримано, від початку
     */
    public static Optional<DiscoveryPacket> decode(byte[] bytes, int length) {
        if (length < QUERY_BYTES
                || length > bytes.length
                || !Arrays.equals(bytes, 0, MAGIC.length, MAGIC, 0, MAGIC.length)) {
            return Optional.empty();
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes, MAGIC.length, length - MAGIC.length);
        byte type = buffer.get();
        int version = buffer.getInt();
        if (type == QUERY && length == QUERY_BYTES) {
            return Optional.of(new DiscoveryPacket.Query(version));
        }
        if (type == REPLY && length == REPLY_BYTES) {
            int port = Short.toUnsignedInt(buffer.getShort());
            return port == 0 ? Optional.empty() : Optional.of(new DiscoveryPacket.Reply(version, port));
        }
        return Optional.empty();
    }
}
