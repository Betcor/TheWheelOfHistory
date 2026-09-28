package kolo.engine.error;

import java.util.Locale;

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
    PROTOCOL_ERROR(ProtocolException.class),
    VERSION_MISMATCH(VersionMismatchException.class),
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
    SAVE_FILE_ERROR(SaveFileException.class),
    SAVE_VERSION_TOO_NEW(SaveVersionException.class),
    INVARIANT_VIOLATION(InvariantViolationException.class);

    private static final String KEY_PREFIX = "error.";

    private final Class<? extends GameException> exceptionType;

    ErrorCode(Class<? extends GameException> exceptionType) {
        this.exceptionType = exceptionType;
    }

    /**
     * Найвужчий клас винятку, що може нести цей код. Виняток з кодом має бути екземпляром цього класу: так
     * {@code ValidationException} не отримає код {@code INSUFFICIENT_FUNDS}.
     */
    public Class<? extends GameException> exceptionType() {
        return exceptionType;
    }

    /** Ключ i18n, напр. {@code error.insufficient_funds}. */
    public String key() {
        // Locale.ROOT: інакше в турецькій локалі «I» перетворюється на «ı».
        return KEY_PREFIX + name().toLowerCase(Locale.ROOT);
    }
}
