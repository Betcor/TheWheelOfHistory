package kolo.server.persistence;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Короткий опис файлу світу для списку збережень ({@link WorldStore#summary(Path)}): читається без міграції й без
 * карти та снапшотів.
 *
 * @param file файл світу
 * @param key ключ світу; порожньо — файл старішої схеми, ключ він отримає при першому відкритті
 * @param contentHash хеш контенту, з яким світ створено
 * @param lastTurn останній збережений рік
 * @param savedAt коли його збережено
 * @param players нікнейми гравців за номером
 */
public record WorldSummary(
        Path file,
        Optional<String> key,
        long seed,
        String contentHash,
        int lastTurn,
        Instant savedAt,
        List<String> players) {

    public WorldSummary {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(savedAt, "savedAt");
        players = List.copyOf(players);
    }

    /** Ім'я світу в списку — ім'я файлу без розширення. */
    public String name() {
        return WorldDirectory.name(file);
    }
}
