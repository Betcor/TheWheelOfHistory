package kolo.protocol.message;

import kolo.engine.error.Checks;

/**
 * Ключ світу — випадковий ідентифікатор, незмінний для світу в будь-якому режимі й під будь-яким ім'ям файлу. За ним
 * клієнт зберігає токени гравців на своєму диску.
 */
public final class WorldKeys {

    /** Найдовший ключ світу, символів. */
    public static final int MAX_LENGTH = 64;

    private WorldKeys() {}

    static void check(String field, String key) {
        Checks.notBlank(field, key);
        Checks.inRange(field, key.length(), 1, MAX_LENGTH);
    }
}
