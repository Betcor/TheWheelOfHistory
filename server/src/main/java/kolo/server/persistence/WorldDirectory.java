package kolo.server.persistence;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import kolo.engine.error.ConflictException;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.GameException;
import kolo.engine.error.NotFoundException;
import kolo.engine.error.SaveFileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Тека, де сервер створює файли світів ({@code *.koloworld}) і звідки їх завантажує. Ім'я файлу — {@code
 * world-<seed>}, а якщо такий уже є — {@code world-<seed>-2}, {@code -3}…: наявні файли ніколи не перезаписуються. Те
 * саме ім'я стає назвою світу; у списку збережень світ зветься ім'ям файлу без розширення ({@link #name(Path)}), тож
 * перейменований чи перенесений з іншого режиму файл — теж світ.
 *
 * <p>Файл, відкритий сесією, зайнятий ({@link #create}, {@link #open(String)}), доки сесія не відпустить його ({@link
 * #release(Path)}): один файл світу — одна сесія і один записувач. Зайнятих файлів немає в {@link #list()}.
 *
 * <p>Тека вбудованого сервера — {@link #defaultLocation()}, окремого — з аргументу запуску. Потокобезпечний.
 */
public final class WorldDirectory {

    /** Системна властивість: домашня тека гри замість типової (тести, портативний запуск). */
    public static final String HOME_PROPERTY = "kolo.home";

    /** Скільки імен з номером пробувати, перш ніж здатися. */
    static final int MAX_NAMES = 10_000;

    private static final Logger LOG = LoggerFactory.getLogger(WorldDirectory.class);

    private final Path directory;
    /** Файли, відкриті сесіями; під замком цього об'єкта. */
    private final TreeSet<Path> inUse = new TreeSet<>();

    public WorldDirectory(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    /**
     * Типова тека світів вбудованого сервера: {@code <kolo.home>/worlds}, якщо задано властивість {@value
     * #HOME_PROPERTY}; у Windows — {@code %APPDATA%\Kolo\worlds}; інакше — {@code ~/.kolo/worlds}.
     */
    public static Path defaultLocation() {
        return home().resolve("worlds");
    }

    /**
     * Домашня тека гри: {@code <kolo.home>}, у Windows — {@code %APPDATA%\Kolo}, інакше — {@code ~/.kolo}. Там тека
     * світів вбудованого сервера й файли клієнта.
     */
    public static Path home() {
        return home(
                System.getProperty(HOME_PROPERTY),
                System.getProperty("os.name", ""),
                System.getenv("APPDATA"),
                System.getProperty("user.home"));
    }

    static Path defaultLocation(String home, String osName, String appData, String userHome) {
        return home(home, osName, appData, userHome).resolve("worlds");
    }

    static Path home(String home, String osName, String appData, String userHome) {
        if (home != null && !home.isBlank()) {
            return Path.of(home);
        }
        if (osName.startsWith("Windows") && appData != null && !appData.isBlank()) {
            return Path.of(appData, "Kolo");
        }
        return Path.of(userHome, ".kolo");
    }

    /** Ім'я світу в списку — ім'я файлу без розширення. */
    public static String name(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(WorldStore.EXTENSION)
                ? name.substring(0, name.length() - WorldStore.EXTENSION.length())
                : name;
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
        return create(seed, WorldStore.newKey(), map, initial, players);
    }

    /**
     * Те саме із заданим ключем світу. Файл зайнятий, доки його не відпустять ({@link #release(Path)}).
     *
     * @param key ключ світу ({@link WorldStore#newKey()})
     */
    public WorldStore create(
            long seed, String key, MapSnapshot map, StateSnapshot initial, List<PlayerRecord> players) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw SaveErrors.io(directory, "create_directory", e);
        }
        String base = "world-" + seed;
        for (int n = 1; n <= MAX_NAMES; n++) {
            String name = n == 1 ? base : base + "-" + n;
            Path file = directory.resolve(name + WorldStore.EXTENSION);
            if (Files.exists(file) || !claim(file)) {
                continue;
            }
            try {
                return WorldStore.create(file, name, key, map, initial, players);
            } catch (SaveFileException e) {
                release(file);
                // Файл міг з'явитися між перевіркою й створенням — тоді беремо наступне ім'я.
                if (!"file_exists".equals(e.details().get("problem"))) {
                    throw e;
                }
            } catch (RuntimeException e) {
                release(file);
                throw e;
            }
        }
        throw SaveErrors.file(directory, "create", "no_free_name");
    }

    /**
     * Світи теки, що зараз не відкриті сесіями, за ім'ям. Файли, яких не прочитати (пошкоджені, новішої версії гри),
     * пропускаються з записом у лог: список не мусить падати через один файл.
     */
    public List<WorldSummary> list() {
        List<WorldSummary> worlds = new ArrayList<>();
        for (Path file : files()) {
            synchronized (this) {
                if (inUse.contains(file)) {
                    continue;
                }
            }
            try {
                worlds.add(WorldStore.summary(file));
            } catch (GameException e) {
                LOG.warn("Файл світу {} пропущено: {} {}", file.getFileName(), e.code(), e.details());
            }
        }
        return List.copyOf(worlds);
    }

    /**
     * Файл світу за ім'ям зі списку ({@link #list()}), зайнятий для сесії; відкриває його сесія сама.
     *
     * @throws NotFoundException з {@code what} = {@code world}, якщо такого світу в теці немає (ім'я — лише ім'я
     *     файлу в цій теці, без шляху)
     * @throws ConflictException з {@link ErrorCode#WORLD_IN_USE}, якщо світ уже відкрито
     */
    public Path open(String name) {
        Objects.requireNonNull(name, "name");
        Path file = files().stream()
                .filter(candidate -> name(candidate).equals(name))
                .findFirst()
                .orElseThrow(
                        () -> new NotFoundException(ErrorCode.NOT_FOUND, ErrorDetails.of("what", "world", "id", name)));
        if (!claim(file)) {
            throw new ConflictException(ErrorCode.WORLD_IN_USE, ErrorDetails.of("world", name));
        }
        return file;
    }

    /** Відпускає файл, відкритий сесією: його знову можна завантажити. */
    public synchronized void release(Path file) {
        inUse.remove(file.toAbsolutePath().normalize());
    }

    /** Чи файл відкрито сесією. */
    public synchronized boolean inUse(Path file) {
        return inUse.contains(file.toAbsolutePath().normalize());
    }

    private synchronized boolean claim(Path file) {
        return inUse.add(file.toAbsolutePath().normalize());
    }

    /** Файли світів теки за ім'ям; тимчасові файли створення ({@code .ім'я.creating}) — не світи. */
    private List<Path> files() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory, "*" + WorldStore.EXTENSION)) {
            for (Path entry : entries) {
                if (!entry.getFileName().toString().startsWith(".") && Files.isRegularFile(entry)) {
                    files.add(entry.toAbsolutePath().normalize());
                }
            }
        } catch (IOException e) {
            throw SaveErrors.io(directory, "list", e);
        }
        files.sort(Comparator.comparing(WorldDirectory::name));
        return files;
    }

    @Override
    public String toString() {
        return "WorldDirectory[" + directory + "]";
    }
}
