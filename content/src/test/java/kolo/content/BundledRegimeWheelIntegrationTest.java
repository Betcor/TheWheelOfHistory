package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.TreeSet;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.BackstoryFragmentDef;
import kolo.engine.content.ContentPack;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.generation.country.Backstory;
import kolo.engine.generation.country.BackstoryWheel;
import kolo.engine.generation.country.Regime;
import kolo.engine.generation.country.RegimeWheel;
import kolo.engine.rng.Rng;
import kolo.engine.state.CountryId;
import org.junit.jupiter.api.Test;

/** Колеса ладу на вбудованому контенті: досяжна кожна підкласифікація, мітки ладу ведуть передісторію. */
class BundledRegimeWheelIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();
    private static final int SEEDS = 5_000;

    @Test
    void ideologyWheelFollowsFileOrder() {
        assertThat(PACK.ideologiesInContentOrder())
                .extracting(ideology -> ideology.id().value())
                .containsExactly(
                        "democracy", "authoritarianism", "totalitarianism", "monarchy", "theocracy", "socialism");
    }

    @Test
    void everySubIdeologyIsReachable() {
        TreeSet<SubIdeologyId> seen = new TreeSet<>();
        for (long seed = 0; seed < SEEDS; seed++) {
            seen.add(RegimeWheel.generate(Rng.of(seed), PACK).subIdeology().id());
        }

        TreeSet<SubIdeologyId> all = new TreeSet<>();
        for (IdeologyDef ideology : PACK.ideologies().values()) {
            ideology.subIdeologies().forEach(sub -> all.add(sub.id()));
        }
        assertThat(seen).isEqualTo(all);
    }

    @Test
    void regimeTagsLeadBackstory() {
        List<CountryId> neighbors = List.of(CountryId.of(2), CountryId.of(5));
        for (long seed = 0; seed < 500; seed++) {
            Rng rng = Rng.of(seed);
            Regime regime = RegimeWheel.generate(rng.fork("regime"), PACK);
            Backstory backstory = BackstoryWheel.generate(rng.fork("backstory"), PACK, regime.tags(), neighbors);

            assertThat(regime.tags()).containsAll(regime.ideology().tags());
            assertThat(regime.tags()).containsAll(regime.subIdeology().tags());
            assertThat(backstory.entries()).as("seed %d", seed).isNotEmpty();
            // Перший фрагмент обирається лише за мітками ладу: наступні вже бачать і мітки фрагментів.
            BackstoryFragmentDef first = backstory.entries().getFirst().fragment();
            assertThat(first.available(regime.tags(), true)).isTrue();
            assertThat(first.weightFor(regime.tags())).isPositive();
        }
    }
}
