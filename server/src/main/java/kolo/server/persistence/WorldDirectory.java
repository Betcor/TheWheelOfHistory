package kolo.server.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.SaveFileException;

/**
 * Тека, де сервер створює файли світів ({@code *.koloworld}). Ім'я файлу — {@code world-<seed>}, а якщо такий уже є —
 * {@code world-<seed>-2}, {@code -3}…: наявні файли ніколи не перезаписуються. Те саме ім'я стає назвою світу.
 *
 * <p>Тека вбудованого сервера — {@link #defaultLocation()}, окремого — з аргументу запуску.
 */
public final class WorldDirectory {

    /** Системна властивість: домашня тека гри замість типової (тести, портативний запуск). */
    public static final String HOME_PROPERTY = "kolo.home";

    /** Скільки імен з номером пробувати, перш ніж здатися. */
    static final int MAX_NAMES = 10_000;

    private final Path directory;

    public WorldDirectory(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    /**
     * Типова тека світів вбудованого сервера: {@code <kolo.home>/worlds}, якщо задано властивість {@value
     * #HOME_PROPERTY}; у Windows — {@code %APPDATA%\Kolo\worlds}; інакше — {@code ~/.kolo/worlds}.
     */
    public static Path defaultLocation() {
        return defaultLocation(
                System.getProperty(HOME_PROPERTY),
                System.getProperty("os.name", ""),
                System.getenv("APPDATA"),
                System.getProperty("user.home"));
    }

    static Path defaultLocation(String home, String osName, String appData, String userHome) {
        if (home != null && !home.isBlank()) {
            return Path.of(home, "worlds");
        }
        if (osName.startsWith("Windows") && appData != null && !appData.isBlank()) {
            return Path.of(appData, "Kolo", "worlds");
        }
        return Path.of(userHome, ".kolo", "worlds");
    }

    public Path path() {
        return directory;
    }

    /**
     * Створює файл нового світу під першим вільним ім'ям і відкриває його. Тека створюється, якщо її немає.
     *
     * @param seed seed світу — основа імені файлу
     * @throws SaveFileException з {@link ErrorCode#SAVE_FILE_ERROR}, якщо тека недоступна, вільного імені немає або
     *     запис не вдався
     */
    public WorldStore create(long seed, MapSnapshot map, StateSnapshot initial) {
        return create(seed, map, initial, List.of());
    }

    /** Те саме з гравцями світу ({@link WorldStore#create(Path, String, MapSnapshot, StateSnapshot, List)}). */
    public WorldStore create(long seed, MapSnapshot map, StateSnapshot initial, List<PlayerRecord> players) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw SaveErrors.io(directory, "create_directory", e);
        }
        String base = "world-" + seed;
        for (int n = 1; n <= MAX_NAMES; n++) {
            String name = n == 1 ? base : base + "-" + n;
            Path file = directory.resolve(name + WorldStore.EXTENSION);
            if (Files.exists(file)) {
                continue;
            }
            try {
                return WorldStore.create(file, name, map, initial, players);
            } catch (SaveFileException e) {
                // Файл міг з'явитися між перевіркою й створенням — тоді беремо наступне ім'я.
                if (!"file_exists".equals(e.details().get("problem"))) {
                    throw e;
                }
            }
        }
        throw SaveErrors.file(directory, "create", "no_free_name");
    }

    @Override
    public String toString() {
        return "WorldDirectory[" + directory + "]";
    }
}
