package kolo.content.loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Звідки читати файли контенту. */
@FunctionalInterface
public interface ContentSource {

    /**
     * @param fileName ім'я файлу, напр. {@code ideologies.yaml}
     * @return вміст файлу або {@link Optional#empty()}, якщо файлу немає
     */
    Optional<byte[]> read(String fileName) throws IOException;

    /** Контент, вбудований у гру: ресурси класпасу {@code /content/}. */
    static ContentSource bundled() {
        return fileName -> {
            try (InputStream in = ContentSource.class.getResourceAsStream("/content/" + fileName)) {
                return in == null ? Optional.empty() : Optional.of(in.readAllBytes());
            }
        };
    }

    /** Контент з каталогу на диску (моди, інструменти балансу). */
    static ContentSource directory(Path dir) {
        Objects.requireNonNull(dir, "dir");
        return fileName -> {
            Path file = dir.resolve(fileName);
            return Files.isRegularFile(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
        };
    }
}
