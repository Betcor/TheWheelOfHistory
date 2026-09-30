package kolo.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldLimits;
import kolo.engine.state.WorldState;
import kolo.server.Budget;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Файли світів з вбудованого контенту: світи різного розміру зберігаються й відкриваються без втрат; найбільший світ —
 * у бюджетах часу (створення, рік, відкриття) і розміру файлу за 200 років.
 */
class BundledWorldStoreIntegrationTest {

    private static final int WORLDS = 4;
    private static final int YEARS = 10;

    @TempDir
    Path dir;

    @Test
    void worldsOfEverySizeRoundTrip() {
        for (long seed = 0; seed < WORLDS; seed++) {
            int players = 1 + (int) (seed * 5 % WorldLimits.MAX_PLAYERS);
            NpcShare share = NpcShare.values()[(int) (seed % NpcShare.values().length)];
            WorldState state = TestWorlds.state(seed, players, share);
            MapSnapshot map = MapSnapshot.of(state.map());
            StateSnapshot initial = StateSnapshot.of(state, map);
            Path file = dir.resolve("w" + seed + WorldStore.EXTENSION);

            WorldStore.create(file, "Світ " + seed, map, initial).close();

            try (WorldStore store = WorldStore.open(file)) {
                assertThat(store.map().hash()).isEqualTo(map.hash());
                assertThat(store.loadLatest().state()).isEqualTo(state);
            }
        }
    }

    @Test
    @Tag("budget")
    void largestWorldFitsBudget() throws IOException {
        WorldState state = TestWorlds.state(1, WorldLimits.MAX_PLAYERS, NpcShare.MANY);
        MapSnapshot map = MapSnapshot.of(state.map());
        StateSnapshot initial = StateSnapshot.of(state, map);
        AtomicInteger files = new AtomicInteger();

        Budget.Timed<Path> created = Budget.best(() -> {
            Path file = dir.resolve("c" + files.incrementAndGet() + WorldStore.EXTENSION);
            WorldStore.create(file, "Найбільший", map, initial).close();
            return file;
        });
        Path file = created.result();
        AtomicInteger turn = new AtomicInteger();
        long years;
        try (WorldStore store = WorldStore.open(file)) {
            Budget.Timed<Integer> year = Budget.best(() -> {
                for (int i = 0; i < YEARS; i++) {
                    WorldState next = state.deepCopy();
                    next.setTurn(turn.incrementAndGet());
                    store.saveTurn(StateSnapshot.of(next, map));
                }
                return turn.get();
            });
            years = year.millis();
        }
        Budget.Timed<StateSnapshot> opened = Budget.best(() -> {
            try (WorldStore store = WorldStore.open(file)) {
                return store.loadLatest();
            }
        });

        assertThat(opened.result().state().turn()).isEqualTo(turn.get());
        // Створення — раз після генерації (карта ≤ 2 с); рік пишеться разом з resolveTurn (≤ 500 мс).
        assertThat(created.millis()).isLessThan(1500);
        assertThat(years / YEARS).isLessThan(150);
        assertThat(opened.millis()).isLessThan(1500);
        // Файл після 200 років мусить лишитися в 100 МБ (без проріджування снапшотів).
        long perYear = (Files.size(file) - Files.size(dir.resolve("c1" + WorldStore.EXTENSION))) / turn.get();
        assertThat(Files.size(file) + perYear * 200).isLessThan(100L * 1024 * 1024);
    }
}
