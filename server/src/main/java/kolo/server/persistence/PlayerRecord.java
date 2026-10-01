package kolo.server.persistence;

import kolo.engine.error.Checks;

/**
 * Гравець світу у файлі: хто якою державою грає.
 *
 * @param number номер гравця в сесії, з якою світ створено; не змінюється
 * @param nickname нікнейм
 * @param tokenHash SHA-256 hex токена гравця — сам токен не зберігається
 * @param country номер держави гравця
 * @param host чи це хост світу
 */
public record PlayerRecord(int number, String nickname, String tokenHash, int country, boolean host) {

    public PlayerRecord {
        Checks.inRange("number", number, 1, Integer.MAX_VALUE);
        Checks.notBlank("nickname", nickname);
        Checks.notBlank("token_hash", tokenHash);
        Checks.inRange("country", country, 0, Integer.MAX_VALUE);
    }
}
