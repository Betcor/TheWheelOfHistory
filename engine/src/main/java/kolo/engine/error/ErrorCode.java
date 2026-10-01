package kolo.engine.error;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Код помилки. Клієнт показує гравцеві текст за ключем {@link #key()}, підставляючи подробиці з {@link
 * GameException#details()}.
 *
 * <p>Кожен код належить одній гілці ієрархії ({@link #exceptionType()}). Коди лише додаються: їхні імена потрапляють у протокол і ключі i18n.
 */
public enum ErrorCode {

    // ---- ValidationException ----
    /** Число поза допустимими межами. Подробиці: {@code field}, {@code value}, {@code min}, {@code max}. */
    VALUE_OUT_OF_RANGE(ValidationException.class),
    /** Порожній рядок там, де потрібне значення. Подробиці: {@code field}. */
    BLANK_VALUE(ValidationException.class),
    /** Ключ не у форматі {@code snake_case}. Подробиці: {@code field}, {@code value}. */
    INVALID_KEY_FORMAT(ValidationException.class),
    /** Повторний id у колекції, де id мають бути унікальні. Подробиці: {@code field}, {@code value}. */
    DUPLICATE_ID(ValidationException.class),
    /** Посилання на відсутній об'єкт у межах того самого значення. Подробиці: {@code field}, {@code value}. */
    UNKNOWN_REFERENCE(ValidationException.class),
    /** Порожня колекція там, де потрібен хоча б один елемент. Подробиці: {@code field}. */
    EMPTY_COLLECTION(ValidationException.class),
    /**
     * Не задано визначення для значення, яке має бути визначене завжди (напр. кожна галузь технологій). Подробиці:
     * {@code field}, {@code value}.
     */
    MISSING_DEFINITION(ValidationException.class),
    /** Об'єкт посилається сам на себе там, де це безглуздо (напр. риса несумісна сама з собою). Подробиці: {@code field}. */
    SELF_REFERENCE(ValidationException.class),
    /**
     * Частина назви чи шаблон назви має неприпустимий формат: не ті літери, недозволений стик голосних і приголосних,
     * немає рівно одного {@code {root}}. Подробиці: {@code field}, {@code value}.
     */
    INVALID_NAME_FORMAT(ValidationException.class),
    /**
     * Парадигма відмінювання не того роду, якого вимагає місце посилання (напр. жіноча парадигма в кінцівці чоловічого
     * імені). Подробиці: {@code field}, {@code value} (id парадигми), {@code expected} (ключ роду).
     */
    NAME_GENDER_MISMATCH(ValidationException.class),
    /**
     * Шаблон тексту має неприпустимий формат: невідома змінна чи відмінок, незакрита дужка, змінна, якої не дозволяє
     * визначення. Подробиці: {@code field}, {@code value}.
     */
    INVALID_TEMPLATE(ValidationException.class),
    /**
     * Елементи списку йдуть не в тому порядку, якого вимагають правила (напр. рівні ВВП — від бідного до багатого).
     * Подробиці: {@code field}, {@code value}.
     */
    OUT_OF_ORDER(ValidationException.class),
    /**
     * Задано поля, які виключають одне одного (напр. придатність до родовищ і за місцевістю, і за родючістю).
     * Подробиці: {@code field}, {@code value} (друге поле).
     */
    CONFLICTING_FIELDS(ValidationException.class),
    /** Сума ваг колеса нульова. */
    WHEEL_ZERO_WEIGHT(ValidationException.class),
    /** Критичних секторів більше, ніж вміщує мінімум 1% кожному. Подробиці: {@code count}, {@code max}. */
    WHEEL_TOO_MANY_CRITICAL(ValidationException.class),
    /** Наказ некоректний структурно. */
    INVALID_ORDER(InvalidOrderException.class),

    // ---- RuleViolationException ----
    INSUFFICIENT_FUNDS(InsufficientFundsException.class),
    INSUFFICIENT_RESOURCES(InsufficientResourcesException.class),
    PREREQUISITE_MISSING(PrerequisiteMissingException.class),
    NOT_OWNER(NotOwnerException.class),
    PHASE_CLOSED(PhaseClosedException.class),
    VASSAL_RESTRICTION(VassalRestrictionException.class),
    DIPLOMATIC_RESTRICTION(DiplomaticRestrictionException.class),
    FATE_TOKEN_LIMIT(FateTokenLimitException.class),

    // ---- Інші гілки ієрархії ----
    NOT_FOUND(NotFoundException.class),
    UNAUTHORIZED(UnauthorizedException.class),
    FORBIDDEN(ForbiddenException.class),
    CONFLICT(ConflictException.class),
    /** Гру сесії вже почато або сесію закрито: до лобі не приєднатися й гру не почати вдруге. */
    LOBBY_CLOSED(ConflictException.class),
    /** У лобі вже найбільша кількість гравців. Подробиці: {@code max}. */
    LOBBY_FULL(ConflictException.class, "max"),
    /** Нікнейм уже зайнятий у цій сесії (без огляду на регістр). Подробиці: {@code nickname}. */
    NICKNAME_TAKEN(ConflictException.class, "nickname"),
    /**
     * Повідомлення протоколу пошкоджене або прийшло не в тому порядку. Подробиці: {@code location} (шлях усередині
     * повідомлення, {@code cells[3].site}) і {@code problem} (опис для розробника, не для гравця) або {@code cause}
     * (код первинної помилки) з її подробицями.
     */
    PROTOCOL_ERROR(ProtocolException.class),
    /**
     * Клієнт і сервер говорять різними версіями протоколу або мають різний контент. Подробиці: {@code part} ({@code
     * protocol} чи {@code content}), {@code client}, {@code server} — версії або хеші контенту.
     */
    VERSION_MISMATCH(VersionMismatchException.class, "part", "client", "server"),
    /**
     * Значення в контенті невалідне. Подробиці: {@code file}, {@code location} (шлях усередині файлу), {@code cause}
     * (код первинної помилки) і подробиці первинної помилки ({@code field}, {@code value}…).
     */
    INVALID_CONTENT(ContentException.class),
    /** Файлу контенту немає. Подробиці: {@code file}. */
    CONTENT_FILE_MISSING(ContentException.class),
    /** Файл контенту не вдалося прочитати. Подробиці: {@code file}. */
    CONTENT_READ_FAILED(ContentException.class),
    /**
     * Файл контенту — не валідний YAML або не відповідає структурі (невідоме поле, не той тип). Подробиці: {@code
     * file}, {@code line}, {@code column}, {@code problem} (опис для автора контенту, не для гравця).
     */
    CONTENT_MALFORMED(ContentException.class),
    /**
     * Файл світу не вдалося створити, відкрити, записати чи прочитати. Подробиці: {@code file} (ім'я файлу), {@code
     * operation} ({@code create}, {@code open}, {@code save_turn}, {@code save_player}, {@code load}, {@code close}) і {@code problem}
     * ({@code file_exists}, {@code file_missing}, {@code io_error}) або {@code sqlite_code}.
     */
    SAVE_FILE_ERROR(SaveFileException.class),
    /**
     * Снапшот світу пошкоджений: не валідний JSON, не та структура, значення поза межами, порушено інваріант, не
     * канонічний запис, снапшот стану не від цієї карти, або сам файл світу чужий чи неповний. Подробиці: {@code part}
     * ({@code map}, {@code state} чи {@code file}), {@code location} (шлях усередині снапшота або таблиця файлу
     * світу, {@code snapshots[3]}), {@code problem} (опис для розробника, не для гравця) або {@code cause} (код
     * первинної помилки) з її подробицями.
     */
    SAVE_MALFORMED(SaveFileException.class),
    /**
     * Файл світу створено новішою версією гри. Подробиці: {@code version}, {@code supported}; для схеми файлу — ще
     * {@code part} = {@code file}.
     */
    SAVE_VERSION_TOO_NEW(SaveVersionException.class, "version", "supported"),
    INVARIANT_VIOLATION(InvariantViolationException.class);

    private static final String KEY_PREFIX = "error.";

    private final Class<? extends GameException> exceptionType;
    private final SortedSet<String> requiredDetails;

    ErrorCode(Class<? extends GameException> exceptionType, String... requiredDetails) {
        this.exceptionType = exceptionType;
        this.requiredDetails = Collections.unmodifiableSortedSet(new TreeSet<>(List.of(requiredDetails)));
    }

    /**
     * Найвужчий клас винятку, що може нести цей код. Виняток з кодом має бути екземпляром цього класу: так
     * {@code ValidationException} не отримає код {@code INSUFFICIENT_FUNDS}.
     */
    public Class<? extends GameException> exceptionType() {
        return exceptionType;
    }

    /**
     * Подробиці, які має кожен виняток з цим кодом ({@link GameException} перевіряє це при створенні). Лише їх текст
     * помилки для гравця може підставляти як {@code {назва}}: решта подробиць буває не завжди.
     */
    public SortedSet<String> requiredDetails() {
        return requiredDetails;
    }

    /** Ключ i18n, напр. {@code error.insufficient_funds}. */
    public String key() {
        // Locale.ROOT: інакше в турецькій локалі «I» перетворюється на «ı».
        return KEY_PREFIX + name().toLowerCase(Locale.ROOT);
    }
}
