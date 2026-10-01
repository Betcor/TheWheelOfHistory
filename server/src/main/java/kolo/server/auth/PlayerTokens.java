package kolo.server.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Токени гравців: з токеном гравець повертається до своєї держави після розриву з'єднання. Токен — 32 випадкові байти
 * в hex (знає лише клієнт гравця); сервер і файл світу тримають тільки SHA-256 hex токена. Потокобезпечний.
 */
public final class PlayerTokens {

    /** Байтів випадковості в токені. */
    static final int TOKEN_BYTES = 32;

    private static final HexFormat HEX = HexFormat.of();

    private final SecureRandom random;

    public PlayerTokens() {
        this(new SecureRandom());
    }

    PlayerTokens(SecureRandom random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    /** Новий токен: 64 шістнадцяткові символи. */
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    /** SHA-256 hex токена — те, що зберігається. */
    public static String hash(String token) {
        Objects.requireNonNull(token, "token");
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 — обов'язковий алгоритм кожної JVM.
            throw new IllegalStateException(e);
        }
    }

    /** Чи це токен із цим хешем; порівняння за сталий час — не підказує, скільки символів збіглося. */
    public static boolean matches(String token, String tokenHash) {
        return MessageDigest.isEqual(
                hash(token).getBytes(StandardCharsets.US_ASCII), tokenHash.getBytes(StandardCharsets.US_ASCII));
    }
}
