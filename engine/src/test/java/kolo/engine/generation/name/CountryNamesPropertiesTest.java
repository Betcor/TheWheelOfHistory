package kolo.engine.generation.name;

import static kolo.engine.generation.name.TestNames.PACK;
import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.StateFormDef;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.rng.Rng;
import kolo.engine.state.GrammaticalCase;
import kolo.engine.state.LocalizedName;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Властивості генератора назв на довільних seed і підкласифікаціях. */
class CountryNamesPropertiesTest {

    @Property
    void sameSeedGivesSameName(@ForAll long seed, @ForAll("subIdeologies") SubIdeologyId sub) {
        assertThat(CountryNames.generate(Rng.of(seed), PACK, sub))
                .isEqualTo(CountryNames.generate(Rng.of(seed), PACK, sub));
    }

    @Property
    void fullNameIsSomeAvailableFormWithRootInNominative(
            @ForAll long seed, @ForAll("subIdeologies") SubIdeologyId sub) {
        LocalizedName name = CountryNames.generate(Rng.of(seed), PACK, sub);
        String root = name.shortName().nominative();

        StateFormDef form = PACK.stateFormsFor(sub).stream()
                .filter(candidate -> candidate
                        .render(GrammaticalCase.NOMINATIVE, root)
                        .equals(name.fullName().nominative()))
                .findFirst()
                .orElseThrow();
        assertThat(name.fullName().gender()).isEqualTo(form.gender());
        for (GrammaticalCase grammaticalCase : GrammaticalCase.values()) {
            assertThat(name.fullName().form(grammaticalCase)).isEqualTo(form.render(grammaticalCase, root));
        }
    }

    @Property
    void everyRootFormStartsWithCapitalAndSharesTheStem(@ForAll long seed, @ForAll("subIdeologies") SubIdeologyId sub) {
        LocalizedName name = CountryNames.generate(Rng.of(seed), PACK, sub);

        for (String form : name.shortName().forms()) {
            assertThat(Character.isUpperCase(form.charAt(0))).as(form).isTrue();
            assertThat(form.substring(1)).as(form).isLowerCase();
            // Закінчення в тестових парадигмах — не довші за 2 літери, тож спільна основа — усе, крім них.
            String nominative = name.shortName().nominative();
            assertThat(form).startsWith(nominative.substring(0, nominative.length() - 1));
        }
    }

    @Provide
    Arbitrary<SubIdeologyId> subIdeologies() {
        return Arbitraries.of(
                new SubIdeologyId("liberal_democracy"),
                new SubIdeologyId("direct_democracy"),
                new SubIdeologyId("absolute_monarchy"));
    }
}
