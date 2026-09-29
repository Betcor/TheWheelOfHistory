package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.TreeSet;
import kolo.engine.content.ReliefDef;

/** Перевірки рельєфу, спільні для тестів рушія. */
public final class ReliefChecks {

    private ReliefChecks() {}

    /**
     * Висоту й рельєф має рівно суходіл, рельєф відповідає порогам, висота — межам формули; хребтів на кожному материку
     * стільки, скільки дало його колесо, а кожен хребет — неперервна ламана свого материка не довша за належну.
     */
    public static void assertValid(MapGrid grid, ContinentMap continents, ReliefMap relief, ReliefDef def) {
        TreeSet<Integer> land = new TreeSet<>();
        for (int cell = 0; cell < grid.cells().size(); cell++) {
            if (continents.isLand(cell)) {
                land.add(cell);
            }
        }
        assertThat(relief.heights().keySet()).isEqualTo(land);
        int lowest = Math.max(0, def.baseHeight() - def.noiseAmplitude());
        int highest = Math.min(ReliefDef.MAX_HEIGHT, def.baseHeight() + def.ridgeHeight() + def.noiseAmplitude());
        for (Map.Entry<Integer, Integer> entry : relief.heights().entrySet()) {
            assertThat(entry.getValue()).isBetween(lowest, highest);
            assertThat(relief.reliefs().get(entry.getKey())).isEqualTo(def.relief(entry.getValue()));
        }

        assertThat(relief.rolls()).hasSize(continents.continents().size());
        int onRidge = Math.min(
                ReliefDef.MAX_HEIGHT, Math.max(0, def.baseHeight() + def.ridgeHeight() - def.noiseAmplitude()));
        int previousContinent = 0;
        int[] ridges = new int[continents.continents().size()];
        for (Ridge ridge : relief.ridges()) {
            assertThat(ridge.continent()).isGreaterThanOrEqualTo(previousContinent);
            previousContinent = ridge.continent();
            ridges[ridge.continent()]++;
            int size = continents.continents().get(ridge.continent()).cells().size();
            assertThat(ridge.cells()).hasSizeLessThanOrEqualTo(ReliefGenerator.ridgeLength(def, size));
            for (int i = 0; i < ridge.cells().size(); i++) {
                int cell = ridge.cells().get(i);
                assertThat(continents.cellContinents().get(cell)).isEqualTo(ridge.continent());
                assertThat(relief.heights().get(cell)).isGreaterThanOrEqualTo(onRidge);
                if (i > 0) {
                    assertThat(grid.cells().get(ridge.cells().get(i - 1)).neighbors())
                            .contains(cell);
                }
            }
        }
        for (int c = 0; c < ridges.length; c++) {
            assertThat(relief.rolls().get(c).resultSectorId()).isEqualTo("ridges_" + ridges[c]);
        }
    }
}
