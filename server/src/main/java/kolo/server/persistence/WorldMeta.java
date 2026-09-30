package kolo.server.persistence;

import java.time.Instant;
import java.util.Objects;

/**
 * Метадані файлу світу: назва, seed, хеш контенту (клієнт і сервер звіряють його при підключенні) і хеш карти.
 *
 * @param createdAt момент створення файлу; лише для показу, у хеш стану не входить
 */
public record WorldMeta(String name, long seed, String contentHash, String mapHash, Instant createdAt) {

    public WorldMeta {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(mapHash, "mapHash");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
