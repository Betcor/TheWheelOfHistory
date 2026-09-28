package kolo.engine.generation.name;

import static kolo.engine.generation.name.TestNames.PACK;
import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.NameStyleId;
import kolo.engine.rng.Rng;
import kolo.engine.state.GrammaticalCase;
import kolo.engine.state.LocalizedName;
import kolo.engine.state.Sex;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Властивості генератора імен на довільних seed, стилях і статях. */
class PersonNamesPropertiesTest {

    @Property
    void sameSeedGivesSameName(@ForAll long seed, @ForAll("styles") NameStyleId style, @ForAll Sex sex) {
        assertThat(PersonNames.generate(Rng.of(seed), PACK, style, sex))
                .isEqualTo(PersonNames.generate(Rng.of(seed), PACK, style, sex));
    }

    @Property
    void genderFollowsSex(@ForAll long seed, @ForAll("styles") NameStyleId style, @ForAll Sex sex) {
        LocalizedName name = PersonNames.generate(Rng.of(seed), PACK, style, sex);

        assertThat(name.fullName().gender()).isEqualTo(sex.gender());
        assertThat(name.shortName().gender()).isEqualTo(sex.gender());
    }

    @Property
    void fullNameIsGivenNameAndShortNameInTheSameCase(
            @ForAll long seed, @ForAll("styles") NameStyleId style, @ForAll Sex sex) {
        LocalizedName name = PersonNames.generate(Rng.of(seed), PACK, style, sex);

        for (GrammaticalCase grammaticalCase : GrammaticalCase.values()) {
            String full = name.fullName().form(grammaticalCase);
            String surname = name.shortName().form(grammaticalCase);
            assertThat(full).endsWith(" " + surname);
            // Два слова, кожне з великої літери.
            assertThat(full).matches("[А-ЩЬЮЯҐЄІЇ][а-щьюяґєії']* [А-ЩЬЮЯҐЄІЇ][а-щьюяґєії']*");
        }
    }

    @Provide
    Arbitrary<NameStyleId> styles() {
        return Arbitraries.of(TestNames.NORTHERN.id(), TestNames.SOUTHERN.id());
    }
}
