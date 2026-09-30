package kolo.server.persistence;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Стискання снапшотів у файлі світу. Байти стиснення не мусять бути однаковими на різних JVM: хеш рахується від
 * канонічного JSON, а не від стисненого.
 */
final class Gzip {

    private Gzip() {}

    static byte[] compress(byte[] bytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length / 8 + 64);
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(bytes);
        } catch (IOException e) {
            // Запис у пам'ять не має вводу-виводу.
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /**
     * Розпаковує стиснений знімок.
     *
     * @param location місце у файлі світу для подробиць помилки
     * @throws kolo.engine.error.SaveFileException з {@code SAVE_MALFORMED} ({@code bad_gzip}), якщо дані пошкоджено
     */
    static byte[] decompress(byte[] bytes, String part, String location) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return gzip.readAllBytes();
        } catch (IOException e) {
            throw SaveErrors.malformed(part, location, "bad_gzip", e);
        }
    }
}
