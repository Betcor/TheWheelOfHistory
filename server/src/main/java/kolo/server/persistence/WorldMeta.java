package kolo.server.persistence;

import java.time.Instant;
import java.util.Objects;
import kolo.engine.error.Checks;

/**
 * Метадані файлу світу: назва, ключ, seed, хеш контенту (клієнт і сервер звіряють його при підключенні) і хеш карти.
 *
 * @param key ключ світу ({@link WorldStore#newKey()}): за ним клієнт зберігає токени гравців; не змінюється, коли файл
 *     перейменовують чи переносять між режимами
 * @param createdAt момент створення файлу; лише для показу, у хеш стану не входить
 */
public record WorldMeta(String name, String key, long seed, String contentHash, String mapHash, Instant createdAt) {

    public WorldMeta {
        Objects.requireNonNull(name, "name");
        Checks.notBlank("key", key);
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(mapHash, "mapHash");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
