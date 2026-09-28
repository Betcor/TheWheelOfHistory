package kolo.engine.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CountryStatsTest {

    private static final CountryStats STATS = new CountryStats(5_000, 60, 55, 30, 70, 10, 40);

    @Test
    void statReadsMatchingField() {
        assertThat(Stat.GDP.of(STATS)).isEqualTo(5_000);
        assertThat(Stat.STABILITY.of(STATS)).isEqualTo(60);
        assertThat(Stat.HDI.of(STATS)).isEqualTo(55);
        assertThat(Stat.INFLUENCE.of(STATS)).isEqualTo(30);
        assertThat(Stat.LEGITIMACY.of(STATS)).isEqualTo(70);
        assertThat(Stat.WAR_WEARINESS.of(STATS)).isEqualTo(10);
        assertThat(Stat.SCIENCE.of(STATS)).isEqualTo(40);
    }

    @Test
    void fromRebuildsSameStats() {
        assertThat(CountryStats.from(stat -> stat.of(STATS))).isEqualTo(STATS);
    }

    @Test
    void rejectsValuesOutsideRange() {
        assertThatThrownBy(() -> new CountryStats(-1, 60, 55, 30, 70, 10, 40))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CountryStats(0, 101, 55, 30, 70, 10, 40))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CountryStats(0, 60, 55, 30, 70, -1, 40))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CountryStats(0, 60, 55, 30, 70, 10, -5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsBoundaries() {
        new CountryStats(0, 0, 0, 0, 0, 0, 0);
        new CountryStats(Long.MAX_VALUE, 100, 100, 100, 100, 100, Integer.MAX_VALUE);
    }

    @Test
    void clampKeepsValueWithinStatRange() {
        assertThat(Stat.STABILITY.clamp(-20)).isZero();
        assertThat(Stat.STABILITY.clamp(140)).isEqualTo(100);
        assertThat(Stat.STABILITY.clamp(42)).isEqualTo(42);
        assertThat(Stat.GDP.clamp(-1)).isZero();
    }
}
