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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import kolo.engine.error.ValidationException;
import kolo.protocol.message.PlayerToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Токени гравців на диску клієнта: ключ світу → місця (номер гравця й токен), які цей клієнт тримає в світі. З ними
 * гравець повертається до своєї держави в завантаженому світі — на вбудованому сервері, в LAN чи на окремому сервері,
 * куди перенесли файл світу. Зазвичай місце одне; у hot-seat — по одному на кожного гравця за цим комп'ютером, і
 * першим — місце того, хто відкриває світ. Новий запис замінює всі місця світу.
 *
 * <p>Файл — {@code tokens.properties} у домашній теці гри, рядок {@code <ключ світу>=<номер>:<токен>[,<номер>:<токен>…]}
 * (токен — шістнадцятковий, коми в ньому немає). Токен — секрет
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

    /** Місця клієнта у світі з цим ключем, першим — того, хто відкриває світ; пошкоджений запис — порожньо. */
    public synchronized List<PlayerToken> find(String world) {
        String value = load().getProperty(world);
        if (value == null) {
            return List.of();
        }
        List<PlayerToken> seats = new ArrayList<>();
        for (String seat : value.split(",", -1)) {
            int colon = seat.indexOf(':');
            try {
                seats.add(new PlayerToken(Integer.parseInt(seat.substring(0, colon)), seat.substring(colon + 1)));
            } catch (IndexOutOfBoundsException | NumberFormatException | ValidationException e) {
                LOG.warn("Пошкоджений токен світу {} у {}", world, file);
                return List.of();
            }
        }
        return List.copyOf(seats);
    }

    /** Запам'ятовує місця клієнта у світі замість попередніх; порожній список забуває світ. */
    public synchronized void save(String world, List<PlayerToken> seats) {
        Objects.requireNonNull(world, "world");
        Properties tokens = load();
        if (seats.isEmpty()) {
            tokens.remove(world);
        } else {
            tokens.setProperty(
                    world,
                    String.join(
                            ",",
                            seats.stream()
                                    .map(seat -> seat.player() + ":" + seat.token())
                                    .toList()));
        }
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
