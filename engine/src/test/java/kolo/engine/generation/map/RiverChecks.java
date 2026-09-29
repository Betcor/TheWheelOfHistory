package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.engine.content.RiverDef;

/** Перевірки річок, спільні для тестів рушія. */
public final class RiverChecks {

    private RiverChecks() {}

    /**
     * Кожна комірка суходолу стікає до сусіда й ланцюжком доходить до води; берег стікає просто у воду, до моря, якщо
     * воно поруч; шлях до води — з найнижчим можливим перевалом; стік — волога плюс стік верхів'їв; річка — рівно там,
     * де стік від порогу в системі не менш як з {@code minCells} комірок, а системи — дерева, що сходяться до гирла.
     */
    public static void assertValid(
            MapGrid grid, ReliefMap relief, ClimateMap climate, SeaMap sea, RiverMap rivers, RiverDef def) {
        int cells = grid.cells().size();
        assertThat(rivers.downstream().keySet()).isEqualTo(relief.heights().keySet());
        TreeMap<Integer, Integer> expectedFlow = new TreeMap<>();
        for (int cell : rivers.downstream().keySet()) {
            int next = rivers.downstream().get(cell);
            assertThat(grid.cells().get(cell).neighbors()).contains(next);
            boolean nearSea = grid.cells().get(cell).neighbors().stream().anyMatch(sea::isSea);
            boolean nearWater = grid.cells().get(cell).neighbors().stream().anyMatch(sea::isWater);
            assertThat(sea.isWater(next)).isEqualTo(nearWater);
            if (nearSea) {
                assertThat(sea.isSea(next)).isTrue();
            }
            // Без циклів: ланцюжок доходить до води, не довший за весь суходіл.
            int steps = 0;
            for (int at = cell; !sea.isWater(at); at = rivers.downstream().get(at)) {
                assertThat(steps++).isLessThanOrEqualTo(relief.heights().size());
            }
            expectedFlow.merge(cell, climate.moistures().get(cell), Integer::sum);
            if (!sea.isWater(next)) {
                int upstream = cell;
                // Стік комірки додається до всіх комірок нижче за течією.
                for (int at = next; !sea.isWater(at); at = rivers.downstream().get(at)) {
                    expectedFlow.merge(at, climate.moistures().get(upstream), Integer::sum);
                }
            }
        }
        assertThat(rivers.flows()).isEqualTo(expectedFlow);

        int[] pass = passes(grid, relief, sea);
        for (int cell : rivers.downstream().keySet()) {
            int highest = 0;
            for (int at = cell; !sea.isWater(at); at = rivers.downstream().get(at)) {
                highest = Math.max(highest, relief.heights().get(at));
            }
            assertThat(highest).as("перевал комірки %d", cell).isEqualTo(pass[cell]);
        }

        // Гирло кожної комірки з достатнім стоком — остання комірка перед водою.
        TreeMap<Integer, Integer> mouths = new TreeMap<>();
        TreeMap<Integer, Integer> systemSizes = new TreeMap<>();
        for (int cell : rivers.flows().keySet()) {
            if (def.enoughFlow(rivers.flows().get(cell))) {
                int at = cell;
                while (!sea.isWater(rivers.downstream().get(at))) {
                    at = rivers.downstream().get(at);
                    assertThat(def.enoughFlow(rivers.flows().get(at))).isTrue();
                }
                mouths.put(cell, at);
                systemSizes.merge(at, 1, Integer::sum);
            }
        }
        TreeSet<Integer> riverCells = new TreeSet<>();
        for (int cell : mouths.keySet()) {
            if (systemSizes.get(mouths.get(cell)) >= def.minCells()) {
                riverCells.add(cell);
            }
        }
        assertThat(rivers.cellRivers().keySet()).isEqualTo(riverCells);
        TreeSet<Integer> riverMouths = new TreeSet<>();
        for (River river : rivers.rivers()) {
            riverMouths.add(river.mouth());
            assertThat(river.cells()).hasSize(systemSizes.get(river.mouth()));
        }
        assertThat(riverMouths).hasSameSizeAs(rivers.rivers());
        for (int r = 0; r < rivers.rivers().size(); r++) {
            River river = rivers.rivers().get(r);
            assertThat(sea.isWater(river.outlet())).isTrue();
            assertThat(rivers.downstream(river.mouth())).hasValue(river.outlet());
            for (int cell : river.cells()) {
                int at = cell;
                while (at != river.mouth()) {
                    at = rivers.downstream().get(at);
                    assertThat(rivers.river(at)).hasValue(r);
                }
            }
        }
        for (int cell = 0; cell < cells; cell++) {
            if (sea.isWater(cell)) {
                assertThat(rivers.hasRiver(cell)).isFalse();
                assertThat(rivers.downstream(cell)).isEmpty();
            }
        }
    }

    /**
     * Найнижчий можливий перевал до води для кожної комірки суходолу — найменша з найбільших висот на шляхах до води,
     * порахована незалежно від генератора: ітерації, доки значення змінюються.
     */
    static int[] passes(MapGrid grid, ReliefMap relief, SeaMap sea) {
        int[] pass = new int[grid.cells().size()];
        Arrays.fill(pass, Integer.MAX_VALUE);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int cell : relief.heights().keySet()) {
                int best = Integer.MAX_VALUE;
                for (int neighbor : grid.cells().get(cell).neighbors()) {
                    best = Math.min(best, sea.isWater(neighbor) ? 0 : pass[neighbor]);
                }
                if (best == Integer.MAX_VALUE) {
                    continue;
                }
                int value = Math.max(relief.heights().get(cell), best);
                if (value < pass[cell]) {
                    pass[cell] = value;
                    changed = true;
                }
            }
        }
        return pass;
    }
}
