package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.content.SeaDef;
import kolo.engine.generation.map.ContinentGenerator;
import kolo.engine.generation.map.ContinentMap;
import kolo.engine.generation.map.MapGrid;
import kolo.engine.generation.map.SeaGenerator;
import kolo.engine.generation.map.SeaMap;
import kolo.engine.generation.map.SeaZone;
import kolo.engine.generation.map.VoronoiGrid;
import kolo.engine.generation.map.WaterBody;
import kolo.engine.generation.map.WaterKind;
import kolo.engine.generation.map.WorldSize;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.map.WorldSizeWheel;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldLimits;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Море на вбудованому контенті: для кожного шаблону вода поділена на моря й озера за порогом, кожне море — на
 * належну кількість зон, зони приблизно рівні, більшість провінцій-берегів має вихід до моря; найбільша карта з морем
 * вкладається в бюджет.
 */
class BundledSeaIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();

    @Test
    void everyTemplateGivesSeaZonesOfSensibleSize() {
        SeaDef def = PACK.map().sea();
        for (MapTemplateDef template : PACK.map().templates()) {
            long zones = 0;
            long zoneCells = 0;
            long coastal = 0;
            long land = 0;
            int smallest = Integer.MAX_VALUE;
            int largest = 0;
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

                SeaMap sea = SeaGenerator.generate(world.fork("sea"), PACK, grid, continents);

                for (WaterBody body : sea.bodies()) {
                    assertThat(body.kind())
                            .isEqualTo(body.cells().size() >= def.minCells() ? WaterKind.SEA : WaterKind.LAKE);
                }
                assertThat(sea.count(WaterKind.SEA)).isPositive();
                for (SeaZone zone : sea.zones()) {
                    smallest = Math.min(smallest, zone.cells().size());
                    largest = Math.max(largest, zone.cells().size());
                    zoneCells += zone.cells().size();
                }
                zones += sea.zones().size();
                coastal += sea.coasts().size();
                land += size.provinces();
            }
            long average = zoneCells / zones;
            // Зона в середньому близька до zone_cells; крайні — не більш як удвічі більші, а малі — лише затиснуті
            // в затоках.
            assertThat(average).as(template.id().value()).isBetween(def.zoneCells() * 3L / 4, def.zoneCells() * 5L / 4);
            assertThat(largest).as(template.id().value()).isLessThanOrEqualTo(def.zoneCells() * 2);
            assertThat(smallest).as(template.id().value()).isPositive();
            // Узбережжя — помітна частина суходолу, але не весь він.
            assertThat(coastal * 100 / land).as(template.id().value()).isBetween(10L, 80L);
        }
    }

    @Test
    @Tag("budget")
    void largestMapWithSeaFitsBudget() {
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
        MapGrid grid = VoronoiGrid.generate(Rng.of(1), PACK.map().grid(), cells);
        ContinentMap continents = ContinentGenerator.generate(Rng.of(1), PACK, size, grid);
        Budget.Timed<SeaMap> timed = Budget.best(() -> SeaGenerator.generate(Rng.of(1), PACK, grid, continents));
        SeaMap sea = timed.result();
        long millis = timed.millis();

        assertThat(sea.cellBodies()).hasSize(cells);
        // Бюджет усієї генерації карти — 2 с; море — мала її частина, із запасом на CI.
        assertThat(millis).isLessThan(500);
    }
}
