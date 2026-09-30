package kolo.server.persistence;

import java.util.Arrays;
import java.util.Objects;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.InvariantViolationException;
import kolo.engine.error.SaveFileException;
import kolo.engine.error.SaveVersionException;
import kolo.engine.state.WorldInvariants;
import kolo.engine.state.WorldState;

/**
 * Снапшот стану світу: канонічний JSON усього, крім геометрії карти (замість неї — хеш {@link MapSnapshot}), і його
 * хеш. Хеш стану покриває й карту через її хеш, тож збігається для однакових станів на будь-якій JVM — на ньому
 * стоїть тест реплею: {@code snapshot(N) + orders(N) → hash(snapshot(N + 1))}.
 */
public final class StateSnapshot {

    private final WorldState state;
    private final byte[] json;
    private final String hash;

    private StateSnapshot(WorldState state, byte[] json) {
        this.state = state;
        this.json = json;
        this.hash = Sha256.hex(json);
    }

    /**
     * Знімок стану. Стан не копіюється: знімок описує його на цю мить.
     *
     * @param map снапшот карти цього стану
     * @throws IllegalArgumentException якщо стан на іншій карті — помилка виклику, а не даних
     */
    public static StateSnapshot of(WorldState state, MapSnapshot map) {
        Objects.requireNonNull(state, "state");
        // Копії стану ділять один екземпляр карти, тож звичайно рівність за посиланням.
        if (state.map() != map.map() && !state.map().equals(map.map())) {
            throw new IllegalArgumentException("state is on another map");
        }
        return new StateSnapshot(state, SnapshotWriter.state(state, map.hash()));
    }

    /**
     * Читає стан зі снапшота й перевіряє інваріанти.
     *
     * @param map снапшот карти, на яку посилається стан
     * @throws SaveVersionException якщо снапшот записано новішою версією схеми
     * @throws SaveFileException з {@link ErrorCode#SAVE_MALFORMED}, якщо снапшот пошкоджений, не канонічний, від іншої
     *     карти або стан порушує інваріанти
     */
    public static StateSnapshot read(byte[] json, MapSnapshot map) {
        byte[] bytes = json.clone();
        SnapshotNode root = SnapshotReader.parse(SnapshotReader.STATE, bytes);
        SnapshotNode mapHash = root.field("map_hash");
        if (!mapHash.text().equals(map.hash())) {
            throw mapHash.malformed("map_hash_mismatch");
        }
        WorldState state = SnapshotReader.state(root, map.map());
        try {
            WorldInvariants.check(state);
        } catch (InvariantViolationException e) {
            throw root.because(e);
        }
        StateSnapshot snapshot = new StateSnapshot(state, SnapshotWriter.state(state, map.hash()));
        // Лише канонічний запис: інакше хеш прочитаного не збігався б із хешем того самого стану, записаного знову.
        if (!Arrays.equals(snapshot.json, bytes)) {
            throw root.malformed("not_canonical");
        }
        return snapshot;
    }

    /** Стан; змінний — не змінюйте його, поки знімок у вжитку. */
    public WorldState state() {
        return state;
    }

    /** Канонічний JSON, UTF-8; копія. */
    public byte[] json() {
        return json.clone();
    }

    /** SHA-256 канонічного JSON, шістнадцятково. */
    public String hash() {
        return hash;
    }

    @Override
    public String toString() {
        return "StateSnapshot[" + hash + "]";
    }
}
