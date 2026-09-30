package kolo.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.GZIPOutputStream;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldLimits;
import kolo.engine.state.WorldState;
import kolo.server.Budget;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Снапшоти світів з вбудованого контенту: світи будь-якого розміру записуються й читаються без втрат; найбільший світ —
 * у бюджеті часу, а стиснений знімок стану — у бюджеті розміру файлу світу.
 */
class BundledSnapshotIntegrationTest {

    private static final int WORLDS = 8;

    @Test
    void everyWorldRoundTrips() {
        for (long seed = 0; seed < WORLDS; seed++) {
            int players = 1 + (int) (seed * 5 % WorldLimits.MAX_PLAYERS);
            NpcShare share = NpcShare.values()[(int) (seed % NpcShare.values().length)];
            WorldState state = TestWorlds.state(seed, players, share);
            MapSnapshot map = MapSnapshot.of(state.map());
            StateSnapshot snapshot = StateSnapshot.of(state, map);

            StateSnapshot read = StateSnapshot.read(snapshot.json(), MapSnapshot.read(map.json()));

            assertThat(read.state()).isEqualTo(state);
            assertThat(read.hash()).isEqualTo(snapshot.hash());
        }
    }

    @Test
    void sameSeedSameHashes() {
        WorldState first = TestWorlds.state(7, 4, NpcShare.NORMAL);
        WorldState second = TestWorlds.state(7, 4, NpcShare.NORMAL);
        MapSnapshot map = MapSnapshot.of(first.map());

        assertThat(MapSnapshot.of(second.map()).hash()).isEqualTo(map.hash());
        assertThat(StateSnapshot.of(second, MapSnapshot.of(second.map())).hash())
                .isEqualTo(StateSnapshot.of(first, map).hash());
    }

    @Test
    @Tag("budget")
    void largestWorldFitsBudget() {
        WorldState state = TestWorlds.state(1, WorldLimits.MAX_PLAYERS, NpcShare.MANY);
        MapSnapshot map = MapSnapshot.of(state.map());

        Budget.Timed<StateSnapshot> written = Budget.best(() -> StateSnapshot.of(state, map));
        byte[] json = written.result().json();
        Budget.Timed<StateSnapshot> read = Budget.best(() -> StateSnapshot.read(json, map));
        Budget.Timed<MapSnapshot> mapRead = Budget.best(() -> MapSnapshot.read(map.json()));

        // Знімок стану робиться щороку разом з ходом (resolveTurn — 500 мс), карта читається раз при відкритті.
        assertThat(written.millis()).isLessThan(100);
        assertThat(read.millis()).isLessThan(250);
        assertThat(mapRead.millis()).isLessThan(500);
        // 200 років без проріджування мусять лишитися в 100 МБ файлу світу.
        assertThat(gzip(json).length * 200L).isLessThan(100L * 1024 * 1024);
    }

    private static byte[] gzip(byte[] bytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
