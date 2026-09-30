package kolo.engine.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kolo.engine.content.ResourceId;
import kolo.engine.error.ValidationException;
import org.junit.jupiter.api.Test;

class ProvinceTest {

    @Test
    void copyIsEqualButIndependent() {
        Province original = TestWorldStates.province(ProvinceId.of(0), CountryId.of(0), 500);
        Province copy = original.copy();

        assertThat(copy).isEqualTo(original).isNotSameAs(original);
        copy.setOwner(CountryId.of(1));
        copy.setController(null);
        copy.setPopulationK(1);
        copy.deposits().add(new ResourceId("oil"));
        copy.tags().add("occupied");

        assertThat(original.owner()).contains(CountryId.of(0));
        assertThat(original.controller()).contains(CountryId.of(0));
        assertThat(original.populationK()).isEqualTo(500);
        assertThat(original.deposits()).containsExactly(new ResourceId("iron"));
        assertThat(original.tags()).isEmpty();
        assertThat(copy).isNotEqualTo(original);
    }

    @Test
    void unclaimedHasNoOwner() {
        Province unclaimed = TestWorldStates.province(ProvinceId.of(2), null, 0);

        assertThat(unclaimed.owner()).isEmpty();
        assertThat(unclaimed.controller()).isEmpty();
    }

    @Test
    void populationIsNotNegative() {
        Province province = TestWorldStates.province(ProvinceId.of(0), null, 0);

        assertThatThrownBy(() -> province.setPopulationK(-1)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> TestWorldStates.province(ProvinceId.of(0), null, -1))
                .isInstanceOf(ValidationException.class);
    }
}
