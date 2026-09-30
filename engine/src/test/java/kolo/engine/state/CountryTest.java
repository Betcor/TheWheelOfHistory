package kolo.engine.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import kolo.engine.content.IdeologyId;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.error.ValidationException;
import org.junit.jupiter.api.Test;

class CountryTest {

    private static Country country() {
        return TestWorldStates.country(
                CountryId.of(0), ControlType.PLAYER, ProvinceId.of(0), ReligionId.of(2), List.of(PersonId.of(3)));
    }

    @Test
    void copyIsEqualButIndependent() {
        Country original = country();
        Country copy = original.copy();

        assertThat(copy).isEqualTo(original).isNotSameAs(original);
        copy.setControl(ControlType.AUTOPILOT);
        copy.setCapital(ProvinceId.of(1));
        copy.setRegime(new IdeologyId("monarchy"), new SubIdeologyId("absolute_monarchy"));
        copy.setReligion(null);
        copy.setNuclear(NuclearStatus.ARSENAL, 3);
        copy.setFateTokens(3);
        copy.modifiers().clear();
        copy.tags().add("revanchism");
        copy.people().clear();

        assertThat(original).isEqualTo(country());
        assertThat(copy).isNotEqualTo(original);
        assertThat(copy.religion()).isEmpty();
        assertThat(original.religion()).contains(ReligionId.of(2));
    }

    @Test
    void startDevelopmentIsReadOnly() {
        Country country = country();

        assertThatThrownBy(() -> country.startDevelopment().put(TechBranch.ECONOMY, 2))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(country.startDevelopment()).hasSize(TechBranch.values().length);
    }

    @Test
    void gdpPerCapitaIsNotNegative() {
        assertThatThrownBy(() -> country().setGdpPerCapita(-1)).isInstanceOf(ValidationException.class);
    }
}
