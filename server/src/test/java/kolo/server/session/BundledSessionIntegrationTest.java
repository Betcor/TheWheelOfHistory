package kolo.server.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldLimits;
import kolo.engine.state.WorldState;
import kolo.engine.turn.TurnPipeline;
import kolo.server.Budget;
import kolo.server.TestServers;
import kolo.server.persistence.MapSnapshot;
import kolo.server.persistence.SavedTurn;
import kolo.server.persistence.StateSnapshot;
import kolo.server.persistence.WorldDirectory;
import kolo.server.persistence.WorldStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Сесія з вбудованим контентом: світ, кілька порожніх років, файл світу відтворює кожен рік. */
@Timeout(120)
class BundledSessionIntegrationTest {

    @TempDir
    Path worlds;

    @Test
    void yearsAreSavedAndReadBackIdentically() throws Exception {
        Sessions sessions = new Sessions(new WorldDirectory(worlds));
        SessionActor session = sessions.create(TestServers.CONTENT);
        List<RecordingPeer> players = new ArrayList<>();
        for (int n = 0; n < 4; n++) {
            RecordingPeer player = new RecordingPeer();
            if (n == 0) {
                session.open(player, "Хост", 1970, NpcShare.NORMAL);
            } else {
                session.join(player, "Гравець " + n);
            }
            player.joined();
            players.add(player);
        }
        session.start(players.getFirst());
        for (int n = 0; n < players.size(); n++) {
            RecordingPeer player = players.get(n);
            // Стан лобі після кожного приєднання, починаючи з власного.
            for (int lobby = n; lobby < players.size(); lobby++) {
                player.lobby();
            }
            assertThat(player.map()).isEqualTo(TestServers.map(1970, 4, NpcShare.NORMAL));
            player.expectOrders(0);
        }

        for (int turn = 0; turn < 5; turn++) {
            for (RecordingPeer player : players) {
                session.ready(player, turn);
            }
            for (RecordingPeer player : players) {
                for (int ready = 1; ready < players.size(); ready++) {
                    player.players();
                }
                player.expectYear(turn);
            }
        }
        players.forEach(session::leave);
        assertThat(session.awaitClosed(30, TimeUnit.SECONDS)).isTrue();

        try (WorldStore store = WorldStore.open(worlds.resolve("world-1970" + WorldStore.EXTENSION))) {
            assertThat(store.players()).hasSize(4);
            assertThat(store.turns()).extracting(SavedTurn::turn).containsExactly(0, 1, 2, 3, 4, 5);
            // Реплей: рік N із файлу + рушій → той самий хеш, що записаний для року N + 1.
            for (int turn = 0; turn < 5; turn++) {
                WorldState state = store.loadState(turn).orElseThrow().state();
                WorldState next =
                        TurnPipeline.resolve(state, TestServers.CONTENT).newState();
                assertThat(StateSnapshot.of(next, store.map()).hash())
                        .isEqualTo(store.turns().get(turn + 1).stateHash());
            }
        }
    }

    @Test
    @Tag("budget")
    void emptyYearOfTheLargestWorldFitsBudget() {
        WorldState initial =
                kolo.server.session.NewWorlds.generate(TestServers.CONTENT, 1, WorldLimits.MAX_PLAYERS, NpcShare.MANY);
        MapSnapshot map = MapSnapshot.of(initial.map());
        try (WorldStore store = new WorldDirectory(worlds).create(1, map, StateSnapshot.of(initial, map))) {
            WorldState[] state = {initial};

            Budget.Timed<WorldState> year = Budget.best(() -> {
                WorldState next =
                        TurnPipeline.resolve(state[0], TestServers.CONTENT).newState();
                store.saveTurn(StateSnapshot.of(next, map));
                state[0] = next;
                return next;
            });

            assertThat(year.result().turn()).isEqualTo(Budget.RUNS + 1);
            // resolveTurn ≤ 500 мс разом із записом року; порожній рік — це копія стану, знімок і транзакція.
            assertThat(year.millis()).isLessThan(500);
        }
    }
}
