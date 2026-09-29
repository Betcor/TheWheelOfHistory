package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.content.ReliefDef;
import kolo.engine.generation.map.ContinentGenerator;
import kolo.engine.generation.map.ContinentMap;
import kolo.engine.generation.map.MapGrid;
import kolo.engine.generation.map.ReliefGenerator;
import kolo.engine.generation.map.ReliefMap;
import kolo.engine.generation.map.Ridge;
import kolo.engine.generation.map.VoronoiGrid;
import kolo.engine.generation.map.WorldSize;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.map.WorldSizeWheel;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import kolo.engine.state.Relief;
import kolo.engine.state.WorldLimits;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Рельєф на вбудованому контенті: для кожного шаблону висоту й рельєф має рівно суходіл, хребти лежать на своїх
 * материках, частки рівнин, пагорбів і гір — у розумних межах; найбільша карта з рельєфом вкладається в бюджет.
 */
class BundledReliefIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();

    @Test
    void everyTemplateGivesReliefWithSensibleShares() {
        ReliefDef def = PACK.map().relief();
        for (MapTemplateDef template : PACK.map().templates()) {
            long[] counts = new long[Relief.values().length];
            for (long seed = 0; seed < 8; seed++) {
                int players = 1 + (int) (seed % WorldLimits.MAX_PLAYERS);
                NpcShare share = NpcShare.values()[(int) (seed % NpcShare.values().length)];
                Rng world = Rng.of(seed);
                WorldSize size = WorldSizeWheel.generate(
                        world.fork("world_size"),
                        PACK,
                        new WorldSizeInput(players, share, Optional.of(template.id()), OptionalInt.empty()));
                MapGrid grid = VoronoiGrid.generate(
                        world.fork("grid"), PACK.map().grid(), template.gridCells(size.provinces()));
                ContinentMap continents = ContinentGenerator.generate(world.fork("continents"), PACK, size, grid);

                ReliefMap relief = ReliefGenerator.generate(world.fork("relief"), PACK, grid, continents);

                assertThat(relief.heights()).hasSize(size.provinces());
                for (Map.Entry<Integer, Integer> entry : relief.heights().entrySet()) {
                    assertThat(continents.isLand(entry.getKey())).isTrue();
                    assertThat(relief.reliefs().get(entry.getKey())).isEqualTo(def.relief(entry.getValue()));
                }
                assertThat(relief.rolls()).hasSize(size.continents());
                for (Ridge ridge : relief.ridges()) {
                    for (int cell : ridge.cells()) {
                        assertThat(continents.cellContinents().get(cell)).isEqualTo(ridge.continent());
                        assertThat(relief.reliefs().get(cell)).isEqualTo(Relief.MOUNTAINS);
                    }
                }
                for (Relief level : Relief.values()) {
                    counts[level.ordinal()] += relief.count(level);
                }
            }
            long total = counts[0] + counts[1] + counts[2];
            // Рівнин — більшість, гір — помітна меншість: на вбудованому контенті ~60 / 25 / 7–17 %.
            assertThat(counts[Relief.PLAIN.ordinal()] * 100 / total).isBetween(45L, 80L);
            assertThat(counts[Relief.HILLS.ordinal()] * 100 / total).isBetween(10L, 40L);
            assertThat(counts[Relief.MOUNTAINS.ordinal()] * 100 / total).isBetween(3L, 25L);
        }
    }

    @Test
    @Tag("budget")
    void largestMapWithReliefFitsBudget() {
        MapTemplateDef template = PACK.map().templates().stream()
                .min((a, b) -> Integer.compare(a.landPct(), b.landPct()))
                .orElseThrow();
        int provinces = PACK.balance().world().provinces().max();
        WorldSize size = new WorldSize(
                WorldLimits.MAX_PLAYERS,
                WorldLimits.MAX_COUNTRIES - WorldLimits.MAX_PLAYERS,
                template.id(),
                template.continents().max(),
                100,
                provinces,
                500,
                List.of());
        int cells = template.gridCells(provinces);
        Budget.Timed<ReliefMap> timed = Budget.best(() -> {
            MapGrid grid = VoronoiGrid.generate(Rng.of(1), PACK.map().grid(), cells);
            ContinentMap continents = ContinentGenerator.generate(Rng.of(1), PACK, size, grid);
            return ReliefGenerator.generate(Rng.of(1), PACK, grid, continents);
        });
        ReliefMap relief = timed.result();
        long millis = timed.millis();

        assertThat(relief.heights()).hasSize(provinces);
        // Бюджет усієї генерації карти — 2 с; сітка, материки й рельєф — лише її частина, із запасом на повільний CI.
        assertThat(millis).isLessThan(2_000);
    }
}
