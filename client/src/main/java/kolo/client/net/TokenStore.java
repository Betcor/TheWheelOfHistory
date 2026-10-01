package kolo.client.net;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import kolo.engine.error.ValidationException;
import kolo.protocol.message.PlayerToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Токени гравця на диску клієнта: ключ світу → номер гравця й токен. З ними гравець повертається до своєї держави в
 * завантаженому світі — на вбудованому сервері, в LAN чи на окремому сервері, куди перенесли файл світу. Один світ —
 * один токен: новий запис замінює старий.
 *
 * <p>Файл — {@code tokens.properties} у домашній теці гри, рядок {@code <ключ світу>=<номер>:<токен>}. Токен — секрет
 * гравця: у POSIX файл читає лише власник. Збій читання чи запису — не привід зупиняти гру: токен лише не
 * запам'ятається (запис у лог). Потокобезпечний.
 */
public final class TokenStore {

    /** Ім'я файлу в домашній теці гри. */
    public static final String FILE_NAME = "tokens.properties";

    private static final Logger LOG = LoggerFactory.getLogger(TokenStore.class);

    private final Path file;

    /** @param file файл токенів; теку буде створено при першому записі */
    public TokenStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    public Path file() {
        return file;
    }

    /** Токен гравця для світу з цим ключем. */
    public synchronized Optional<PlayerToken> find(String world) {
        String value = load().getProperty(world);
        if (value == null) {
            return Optional.empty();
        }
        int colon = value.indexOf(':');
        try {
            return Optional.of(
                    new PlayerToken(Integer.parseInt(value.substring(0, colon)), value.substring(colon + 1)));
        } catch (IndexOutOfBoundsException | NumberFormatException | ValidationException e) {
            LOG.warn("Пошкоджений токен світу {} у {}", world, file);
            return Optional.empty();
        }
    }

    /** Запам'ятовує токен гравця для світу; попередній токен цього світу забувається. */
    public synchronized void save(String world, PlayerToken token) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(token, "token");
        Properties tokens = load();
        tokens.setProperty(world, token.player() + ":" + token.token());
        try {
            Path directory = file.toAbsolutePath().getParent();
            Files.createDirectories(directory);
            // Запис через тимчасовий файл: обірваний запис не зіпсує токенів інших світів. Тимчасовий файл у POSIX
            // створюється з правами rw------- — з ними файл токенів і лишиться.
            Path temporary = Files.createTempFile(directory, "." + FILE_NAME, ".tmp");
            try {
                try (Writer out = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                    tokens.store(out, null);
                }
                move(temporary);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException | UncheckedIOException e) {
            LOG.warn("Токен світу {} не збережено у {}", world, file, e);
        }
    }

    private Properties load() {
        Properties tokens = new Properties();
        if (!Files.isRegularFile(file)) {
            return tokens;
        }
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            tokens.load(in);
        } catch (IOException | IllegalArgumentException e) {
            LOG.warn("Файл токенів {} не прочитано", file, e);
            return new Properties();
        }
        return tokens;
    }

    private void move(Path temporary) throws IOException {
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
