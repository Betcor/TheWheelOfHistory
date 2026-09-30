package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.ContentPack;
import kolo.engine.modifier.Modifier;
import kolo.engine.rng.Rng;
import kolo.engine.state.FateTokens;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

/** Властивості ланцюжка коліс генерації на довільних seed. */
class CountryGeneratorPropertiesTest {

    private static final CountryGenerationInput NEUTRAL_INPUT = TestChain.input(TestChain.NEUTRAL);
    private static final CountryGenerationInput STREAKY_INPUT = TestChain.input(TestChain.STREAKY);

    @Property
    void sameSeedGivesSameCountry(@ForAll long seed, @ForAll boolean streaky) {
        ContentPack pack = streaky ? TestChain.STREAKY : TestChain.NEUTRAL;
        CountryGenerationInput input = input(streaky);

        assertThat(CountryGenerator.generate(Rng.of(seed), pack, input))
                .isEqualTo(CountryGenerator.generate(Rng.of(seed), pack, input));
    }

    @Property
    void countryIsConsistent(@ForAll long seed, @ForAll boolean streaky) {
        ContentPack pack = streaky ? TestChain.STREAKY : TestChain.NEUTRAL;
        StartCountry country = CountryGenerator.generate(Rng.of(seed), pack, input(streaky));

        assertThat(country.fateTokens()).isBetween(0, FateTokens.MAX);
        assertThat(country.streaks()).extracting(StreakBonus::streak).doesNotHaveDuplicates();
        assertThat(country.tags())
                .containsAll(country.territory().tags())
                .containsAll(country.geography().tags())
                .containsAll(country.population().tags())
                .containsAll(country.regime().tags())
                .containsAll(country.religion().tags())
                .containsAll(country.development().tags())
                .containsAll(country.nuclear().tags())
                .containsAll(country.backstory().tags());
        country.streaks().forEach(bonus -> assertThat(country.tags()).containsAll(bonus.tags()));
        assertThat(country.modifiers()).extracting(Modifier::id).doesNotHaveDuplicates();
        assertThat(country.modifiers()).allMatch(modifier -> modifier.isActiveAt(0));
        assertThat(pack.subIdeology(country.regime().subIdeology().id())).isPresent();
        assertThat(country.totalGdp()).isPositive();
        assertThat(country.armyStrength()).isNotNegative();
    }

    private static CountryGenerationInput input(boolean streaky) {
        return streaky ? STREAKY_INPUT : NEUTRAL_INPUT;
    }
}
