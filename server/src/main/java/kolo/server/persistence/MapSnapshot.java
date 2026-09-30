package kolo.server.persistence;

import java.util.Arrays;
import java.util.Objects;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.SaveFileException;
import kolo.engine.error.SaveVersionException;
import kolo.engine.state.GameMap;

/**
 * Снапшот карти: канонічний JSON незмінної карти і його хеш. Карта пишеться у файл світу один раз, а снапшоти стану
 * щороку лише посилаються на її хеш — так рік не повторює мегабайти многокутників.
 */
public final class MapSnapshot {

    private final GameMap map;
    private final byte[] json;
    private final String hash;

    private MapSnapshot(GameMap map, byte[] json) {
        this.map = map;
        this.json = json;
        this.hash = Sha256.hex(json);
    }

    /** Знімок карти. */
    public static MapSnapshot of(GameMap map) {
        return new MapSnapshot(Objects.requireNonNull(map, "map"), SnapshotWriter.map(map));
    }

    /**
     * Читає карту зі снапшота.
     *
     * @throws SaveVersionException якщо снапшот записано новішою версією схеми
     * @throws SaveFileException з {@link ErrorCode#SAVE_MALFORMED}, якщо снапшот пошкоджений або не канонічний
     */
    public static MapSnapshot read(byte[] json) {
        byte[] bytes = json.clone();
        SnapshotNode root = SnapshotReader.parse(SnapshotReader.MAP, bytes);
        MapSnapshot snapshot = of(SnapshotReader.map(root));
        // Лише канонічний запис: інакше хеш прочитаного не збігався б із хешем того самого стану, записаного знову.
        if (!Arrays.equals(snapshot.json, bytes)) {
            throw root.malformed("not_canonical");
        }
        return snapshot;
    }

    public GameMap map() {
        return map;
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
        return "MapSnapshot[" + hash + "]";
    }
}
