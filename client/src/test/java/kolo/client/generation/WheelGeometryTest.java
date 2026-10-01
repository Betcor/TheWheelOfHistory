package kolo.client.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.WheelKind;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.Test;

class WheelGeometryTest {

    @Test
    void slicesFollowWeightsClockwise() {
        RollRecord roll = TestRolls.roll("generation_area", "b", "a", "b", "c", "d");

        List<WheelGeometry.Slice> slices = WheelGeometry.slices(roll);

        assertThat(slices).extracting(WheelGeometry.Slice::start).containsExactly(0.0, 90.0, 180.0, 270.0);
        assertThat(slices).allSatisfy(slice -> assertThat(slice.extent()).isEqualTo(90.0));
    }

    @Test
    void wheelStopsAfterFullTurnsOnTheResult() {
        RollRecord roll = TestRolls.roll("generation_area", "c", "a", "b", "c", "d");

        double rotation = WheelGeometry.finalRotation(roll);

        assertThat(rotation).isGreaterThan((WheelGeometry.TURNS - 1) * 360.0);
        assertThat(WheelGeometry.sectorAt(roll, rotation).id()).isEqualTo("c");
        assertThat(WheelGeometry.sectorAt(roll, 0).id()).isEqualTo("a");
    }

    @Test
    void easeOutStartsFastAndEndsStill() {
        assertThat(WheelGeometry.easeOut(0)).isZero();
        assertThat(WheelGeometry.easeOut(1)).isEqualTo(1.0);
        assertThat(WheelGeometry.easeOut(0.5)).isGreaterThan(0.5);
        assertThat(WheelGeometry.easeOut(2)).isEqualTo(1.0);
        assertThat(WheelGeometry.pointerAt(-30)).isCloseTo(30, within(1e-9));
    }

    /** Будь-яке колесо зупиняється стрілкою на секторі, що випав, — навіть на найвужчому. */
    @Property(tries = 300)
    void pointerAlwaysLandsOnTheResult(
            @ForAll @Size(min = 1, max = 30) List<@IntRange(min = 1, max = 1000) Integer> weights,
            @ForAll @IntRange(min = 0, max = 9_999) int at) {
        List<RolledSector> sectors = new ArrayList<>();
        int total = weights.stream().mapToInt(Integer::intValue).sum();
        int left = 10_000;
        for (int i = 0; i < weights.size(); i++) {
            int weight = i + 1 == weights.size() ? left : Math.max(1, weights.get(i) * 10_000 / total);
            weight = Math.min(weight, left - (weights.size() - i - 1));
            left -= weight;
            sectors.add(new RolledSector("s" + i, weight, OutcomeTier.PARTIAL, 50));
        }
        int cumulative = 0;
        String result = sectors.getLast().id();
        for (RolledSector sector : sectors) {
            cumulative += sector.weightBp();
            if (at < cumulative) {
                result = sector.id();
                break;
            }
        }
        RollRecord roll = new RollRecord(new WheelKind("generation_area"), sectors, 0, List.of(), result, at, 0, null);

        assertThat(WheelGeometry.sectorAt(roll, WheelGeometry.finalRotation(roll))
                        .id())
                .isEqualTo(result);
    }
}
