package kolo.client.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RasterLevelTest {

    @Test
    void levelsFollowLongSide() {
        List<RasterLevel> levels = RasterLevel.of(16_000, 8000, 2, 2048, 4096);

        assertThat(levels).containsExactly(new RasterLevel(2048, 1024, 0.128), new RasterLevel(4096, 2048, 0.256));
    }

    @Test
    void levelsNeverExceedMaxScale() {
        List<RasterLevel> levels = RasterLevel.of(1000, 500, 1.5, 2048, 4096);

        assertThat(levels).containsExactly(new RasterLevel(1500, 750, 1.5));
    }

    @Test
    void chooseTakesCoarsestSharpEnoughLevel() {
        List<RasterLevel> levels = RasterLevel.of(16_000, 8000, 2, 2048, 4096);

        assertThat(RasterLevel.choose(levels, 0.05)).isZero();
        assertThat(RasterLevel.choose(levels, 0.128)).isZero();
        assertThat(RasterLevel.choose(levels, 0.2)).isEqualTo(1);
        assertThat(RasterLevel.choose(levels, 1.5)).isEqualTo(1);
    }
}
