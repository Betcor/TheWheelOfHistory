package kolo.content.loader;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.SortedMap;

/**
 * SHA-256 файлів контенту. Однаковий для однакових файлів на будь-якій ОС: кінці рядків {@code CRLF}
 * нормалізуються до {@code LF}, бо git на Windows може видати файли з {@code CRLF}.
 */
final class ContentHash {

    private ContentHash() {}

    /** @param files ім'я файлу → вміст, у порядку імен */
    static String of(SortedMap<String, byte[]> files) {
        MessageDigest digest = sha256();
        files.forEach((name, content) -> {
            byte[] normalized = withoutCarriageReturns(content);
            // Ім'я й довжина — щоб межі між файлами були однозначними.
            digest.update((name + "\n" + normalized.length + "\n").getBytes(StandardCharsets.UTF_8));
            digest.update(normalized);
        });
        return HexFormat.of().formatHex(digest.digest());
    }

    private static byte[] withoutCarriageReturns(byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(content.length);
        for (int i = 0; i < content.length; i++) {
            boolean crlf = content[i] == '\r' && i + 1 < content.length && content[i + 1] == '\n';
            if (!crlf) {
                out.write(content[i]);
            }
        }
        return out.toByteArray();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 — обов'язковий алгоритм кожної JVM.
            throw new IllegalStateException(e);
        }
    }
}
