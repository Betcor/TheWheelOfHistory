package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.MapGridDef;
import kolo.engine.rng.Rng;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class VoronoiGridPropertiesTest {

    @Property(tries = 50)
    void sameSeedGivesSameGrid(@ForAll long seed, @ForAll @IntRange(min = 1, max = 300) int cells) {
        MapGridDef def = new MapGridDef(30, 2, 1, 2);

        assertThat(VoronoiGrid.generate(Rng.of(seed), def, cells))
                .isEqualTo(VoronoiGrid.generate(Rng.of(seed), def, cells));
    }

    @Property(tries = 100)
    void gridTilesMapWithoutGaps(
            @ForAll long seed,
            @ForAll @IntRange(min = 1, max = 400) int cells,
            @ForAll @IntRange(min = MapGridDef.MIN_CELL_SIZE, max = 120) int cellSize,
            @ForAll @IntRange(min = 1, max = MapGridDef.MAX_ASPECT) int aspectWidth,
            @ForAll @IntRange(min = 1, max = MapGridDef.MAX_ASPECT) int aspectHeight,
            @ForAll @IntRange(min = 0, max = 3) int relaxation) {
        MapGridDef def = new MapGridDef(cellSize, aspectWidth, aspectHeight, relaxation);

        GridChecks.assertValid(VoronoiGrid.generate(Rng.of(seed), def, cells), cells);
    }
}
