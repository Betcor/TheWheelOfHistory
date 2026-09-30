package kolo.client.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CountryLabelsTest {

    @Test
    void labelStandsOverOwnProvinceNearestToCenter() {
        List<CountryLabels.Label> labels = CountryLabels.of(TestMaps.MAP, MapGeometry.of(TestMaps.MAP));

        assertThat(labels).hasSize(2);
        CountryLabels.Label first = labels.get(0);
        assertThat(first.country()).isZero();
        // Центр держави 0 — між провінціями 0 і 3; при рівності — перша за номером.
        assertThat(first.cell()).isZero();
        assertThat(first.x()).isEqualTo(5);
        assertThat(first.y()).isEqualTo(15);
        assertThat(first.size()).isEqualTo(Math.sqrt(2) * TestMaps.SIDE);
        CountryLabels.Label second = labels.get(1);
        assertThat(second.country()).isEqualTo(1);
        assertThat(second.cell()).isEqualTo(1);
        assertThat(second.size()).isEqualTo(TestMaps.SIDE);
    }
}
