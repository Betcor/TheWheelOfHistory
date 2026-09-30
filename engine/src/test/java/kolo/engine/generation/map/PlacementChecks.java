package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.List;

/** Перевірки розміщення держав, спільні для тестів рушія. */
public final class PlacementChecks {

    private PlacementChecks() {}

    /**
     * Держав стільки, скільки дав розмір світу; кожна зв'язна, стоїть лише на своєму материку й має щонайменше
     * {@code minProvinces} провінцій; держави разом не займають більше суходолу, ніж дозволяє частка нічийних земель
     * на кожному материку; у кожної держави — обертання материка й площі.
     */
    public static void assertValid(
            MapGrid grid, ContinentMap continents, PlacementMap placement, WorldSize size, int minProvinces) {
        assertThat(placement.cellCountries()).hasSize(grid.cells().size());
        assertThat(placement.countries()).hasSize(size.countries());
        assertThat(placement.rolls()).hasSize(2 * size.countries());
        long[] claimed = new long[continents.continents().size()];
        for (int i = 0; i < placement.countries().size(); i++) {
            PlacedCountry country = placement.countries().get(i);
            assertThat(country.provinces()).isGreaterThanOrEqualTo(minProvinces);
            assertThat(country.rolls())
                    .extracting(roll -> roll.kind())
                    .containsExactly(PlacementGenerator.CONTINENT_KIND, PlacementGenerator.AREA_KIND);
            assertThat(country.rolls().getFirst().resultSectorId()).isEqualTo("continent_" + country.continent());
            assertThat(country.rolls().get(1).resultSectorId())
                    .isEqualTo(country.area().value());
            for (int cell : country.cells()) {
                assertThat(continents.cellContinents().get(cell)).isEqualTo(country.continent());
            }
            assertThat(reachable(grid, placement, i, country.seed())).isEqualTo(country.provinces());
            claimed[country.continent()] += country.provinces();
        }
        for (int c = 0; c < claimed.length; c++) {
            long land = continents.continents().get(c).cells().size();
            assertThat(claimed[c]).isLessThanOrEqualTo(land * (10_000 - size.unclaimedBp()) / 10_000);
        }
    }

    /** Скільки комірок держави досяжно від зерна по її ж комірках. */
    static int reachable(MapGrid grid, PlacementMap placement, int country, int seed) {
        boolean[] seen = new boolean[grid.cells().size()];
        ArrayDeque<Integer> queue = new ArrayDeque<>(List.of(seed));
        seen[seed] = true;
        int reached = 0;
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            reached++;
            for (int neighbor : grid.cells().get(cell).neighbors()) {
                if (placement.country(neighbor) == country && !seen[neighbor]) {
                    seen[neighbor] = true;
                    queue.add(neighbor);
                }
            }
        }
        return reached;
    }
}
