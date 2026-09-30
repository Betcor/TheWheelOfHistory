package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.TreeSet;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.world.StartWorld;
import kolo.engine.generation.world.WorldGenerator;
import kolo.engine.generation.world.WorldStates;
import kolo.engine.rng.Rng;
import kolo.engine.state.Country;
import kolo.engine.state.NpcShare;
import kolo.engine.state.Province;
import kolo.engine.state.WorldInvariants;
import kolo.engine.state.WorldLimits;
import kolo.engine.state.WorldState;
import kolo.engine.view.MapView;
import kolo.engine.view.MapViews;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Стан світу з вбудованого контенту: світи будь-якого розміру стають станом без порушень інваріантів, представлення
 * карти зі стану збігається з картою, копія стану дорівнює оригіналу; найбільший світ разом зі станом — у бюджеті.
 */
class BundledWorldStateIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();
    private static final int WORLDS = 20;

    @Test
    void everyWorldBecomesAValidState() {
        for (long seed = 0; seed < WORLDS; seed++) {
            int players = 1 + (int) (seed * 5 % WorldLimits.MAX_PLAYERS);
            NpcShare share = NpcShare.values()[(int) (seed % NpcShare.values().length)];
            StartWorld world = WorldGenerator.generate(Rng.of(seed), PACK, WorldSizeInput.of(players, share));
            WorldState state = WorldStates.of(seed, PACK, world);

            WorldInvariants.check(state);
            assertThat(state.contentHash()).isEqualTo(PACK.hash());
            assertThat(state.countries()).hasSize(world.countries().size());
            assertThat(state.deepCopy()).isEqualTo(state);
            check(state);
        }
    }

    @Test
    void sameSeedGivesSameState() {
        WorldSizeInput input = WorldSizeInput.of(4, NpcShare.NORMAL);

        assertThat(WorldStates.of(7, PACK, WorldGenerator.generate(Rng.of(7), PACK, input)))
                .isEqualTo(WorldStates.of(7, PACK, WorldGenerator.generate(Rng.of(7), PACK, input)));
    }

    @Test
    @Tag("budget")
    void largestWorldWithStateFitsBudget() {
        WorldSizeInput input = WorldSizeInput.of(WorldLimits.MAX_PLAYERS, NpcShare.MANY);

        Budget.Timed<WorldState> timed =
                Budget.best(() -> WorldStates.of(1, PACK, WorldGenerator.generate(Rng.of(1), PACK, input)));

        assertThat(timed.result().countries()).hasSizeGreaterThan(2 * WorldLimits.MAX_PLAYERS);
        // Бюджет генерації карти — 2 с; стан світу мусить вкластися разом із нею.
        assertThat(timed.millis()).isLessThan(2000);
    }

    private static void check(WorldState state) {
        MapView view = MapViews.of(state);
        assertThat(view.cells()).hasSameSizeAs(state.map().tiles());
        assertThat(view.countries()).hasSameSizeAs(state.countries().values());
        long land = view.cells().stream().filter(cell -> cell.isLand()).count();
        assertThat(state.provinces()).hasSize((int) land);

        TreeSet<String> names = new TreeSet<>();
        for (Country country : state.countries().values()) {
            Province capital = state.provinces().get(country.capital());
            int most = state.provinces().values().stream()
                    .filter(province -> province.owner().equals(Optional.of(country.id())))
                    .mapToInt(Province::populationK)
                    .max()
                    .orElseThrow();
            assertThat(capital.populationK()).isEqualTo(most);
            assertThat(names.add(country.name().fullName().nominative())).isTrue();
        }
    }
}
