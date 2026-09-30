package kolo.server.persistence;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 у шістнадцятковому записі малими літерами — як хеш контенту. */
final class Sha256 {

    private Sha256() {}

    static String hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 — обов'язковий алгоритм кожної JVM.
            throw new IllegalStateException(e);
        }
    }
}
