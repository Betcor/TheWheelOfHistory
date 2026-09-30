package kolo.server.persistence;

import java.time.Instant;
import java.util.Objects;

/**
 * Збережений рік у файлі світу.
 *
 * @param stateHash хеш стану наприкінці року ({@link StateSnapshot#hash()}) — за ним тест реплею звіряє відтворений
 *     стан, навіть коли снапшот року проріджено
 * @param hasSnapshot чи зберігся снапшот цього року
 */
public record SavedTurn(int turn, String stateHash, Instant savedAt, boolean hasSnapshot) {

    public SavedTurn {
        Objects.requireNonNull(stateHash, "stateHash");
        Objects.requireNonNull(savedAt, "savedAt");
    }
}
