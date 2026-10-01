package kolo.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.SaveFileException;
import kolo.engine.state.NpcShare;
import kolo.engine.state.TurnTimer;
import kolo.engine.state.WorldState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Таймер ходу й пропуски гравців у файлі світу: запис, читання, міграція старого файлу. */
class WorldStoreTimerTest {

    private static final WorldState STATE = TestWorlds.state(3, 2, NpcShare.FEW);
    private static final MapSnapshot MAP = MapSnapshot.of(STATE.map());
    private static final StateSnapshot INITIAL = StateSnapshot.of(STATE, MAP);
    private static final List<PlayerRecord> PLAYERS = List.of(
            new PlayerRecord(1, "Оля", "a".repeat(64), 0, true), new PlayerRecord(4, "Ігор", "b".repeat(64), 1, false));
    private static final TurnTimer LIVE = new TurnTimer(TurnTimer.Mode.LIVE, 180);

    @TempDir
    Path dir;

    @Test
    void newWorldIsManualWithoutDeadline() {
        try (WorldStore store = create(file())) {
            assertThat(store.timer()).isEqualTo(new SavedTimer(TurnTimer.MANUAL, Optional.empty()));
            assertThat(store.players()).extracting(SavedPlayer::missedTurns).containsExactly(0, 0);
        }
    }

    @Test
    void timerAndDeadlineSurviveReopening() {
        Path file = file();
        SavedTimer.Deadline deadline = new SavedTimer.Deadline(0, TestStores.NOW.plusSeconds(180));
        try (WorldStore store = create(file)) {
            store.saveTimer(LIVE);
            store.saveDeadline(Optional.of(deadline));
        }

        try (WorldStore store = TestStores.open(file)) {
            SavedTimer saved = store.timer();
            assertThat(saved).isEqualTo(new SavedTimer(LIVE, Optional.of(deadline)));
            assertThat(saved.deadlineOf(0)).contains(deadline.at());
            assertThat(saved.deadlineOf(1)).isEmpty();
        }
    }

    @Test
    void newTimerClearsTheDeadline() {
        try (WorldStore store = create(file())) {
            store.saveTimer(LIVE);
            store.saveDeadline(Optional.of(new SavedTimer.Deadline(0, TestStores.NOW)));

            store.saveTimer(new TurnTimer(TurnTimer.Mode.ASYNC, 43_200));

            assertThat(store.timer().deadline()).isEmpty();
            store.saveDeadline(Optional.of(new SavedTimer.Deadline(0, TestStores.NOW)));
            store.saveDeadline(Optional.empty());
            assertThat(store.timer().deadline()).isEmpty();
        }
    }

    @Test
    void missedTurnsCountInARowAndResetOnReady() {
        try (WorldStore store = create(file())) {
            store.saveTurn(next(1), List.of(4));
            store.saveTurn(next(2), List.of(1, 4));
            assertThat(store.players()).extracting(SavedPlayer::missedTurns).containsExactly(1, 2);

            store.saveTurn(next(3), List.of(1));

            assertThat(store.players()).extracting(SavedPlayer::missedTurns).containsExactly(2, 0);
            store.saveTurn(next(4));
            assertThat(store.players()).extracting(SavedPlayer::missedTurns).containsExactly(0, 0);
        }
    }

    @Test
    void unknownMissingPlayerLeavesTheFileOnThePreviousYear() {
        try (WorldStore store = create(file())) {
            store.saveTurn(next(1), List.of(4));

            assertThatThrownBy(() -> store.saveTurn(next(2), List.of(1, 9)))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(store.lastTurn()).isEqualTo(1);
            assertThat(store.players()).extracting(SavedPlayer::missedTurns).containsExactly(0, 1);
        }
    }

    @Test
    void brokenTimerRowIsMalformed() {
        Path file = file();
        create(file).close();
        TestStores.sql(file, "UPDATE turn_timer SET seconds = 60");

        try (WorldStore store = TestStores.open(file)) {
            assertThatThrownBy(store::timer).isInstanceOfSatisfying(SaveFileException.class, e -> {
                assertThat(e.code()).isEqualTo(ErrorCode.SAVE_MALFORMED);
                assertThat(e.details()).containsEntry("location", "turn_timer");
            });
        }
    }

    @Test
    void fileOfTheThirdSchemaGetsAManualTimerAndKeepsPlayers() {
        Path file = file();
        create(file).close();
        TestStores.downgrade(file, 3);

        try (WorldStore store = TestStores.open(file)) {
            assertThat(store.timer()).isEqualTo(new SavedTimer(TurnTimer.MANUAL, Optional.empty()));
            assertThat(store.players()).extracting(SavedPlayer::player).isEqualTo(PLAYERS);
            assertThat(store.players()).extracting(SavedPlayer::missedTurns).containsExactly(0, 0);
        }
    }

    private Path file() {
        return dir.resolve("w" + WorldStore.EXTENSION);
    }

    private static WorldStore create(Path file) {
        return WorldStore.create(file, "Світ", MAP, INITIAL, PLAYERS, TestStores.CLOCK, Migrator.bundled());
    }

    private static StateSnapshot next(int turn) {
        WorldState state = STATE.deepCopy();
        state.setTurn(turn);
        return StateSnapshot.of(state, MAP);
    }
}
