package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeSet;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.generation.country.NameCandidate;
import kolo.engine.generation.country.NameWheel;
import kolo.engine.generation.country.StartName;
import kolo.engine.rng.Rng;
import kolo.engine.state.GrammaticalCase;
import org.junit.jupiter.api.Test;

/** Вбудовані стилі й форми державності ↔ колесо назви рушія. */
class BundledNameWheelIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();
    private static final int SEEDS = 200;

    @Test
    void everySubIdeologyGetsDistinctCandidatesWithItsForms() {
        for (IdeologyDef ideology : PACK.ideologies().values()) {
            for (SubIdeologyDef sub : ideology.subIdeologies()) {
                for (long seed = 0; seed < SEEDS; seed++) {
                    StartName name = NameWheel.generate(Rng.of(seed), PACK, sub.id(), Set.of());

                    assertThat(name.candidates())
                            .hasSize(PACK.balance().generation().nameCandidates());
                    for (NameCandidate candidate : name.candidates()) {
                        String full = candidate.name().fullName().nominative();
                        assertThat(PACK.stateFormsFor(sub.id()))
                                .as(full)
                                .anyMatch(form -> full.equals(form.render(
                                        GrammaticalCase.NOMINATIVE,
                                        candidate.name().shortName().nominative())));
                        assertThat(PACK.names().styles()).containsKey(candidate.style());
                        for (GrammaticalCase grammaticalCase : GrammaticalCase.values()) {
                            assertThat(candidate.name().fullName().form(grammaticalCase))
                                    .isNotBlank();
                        }
                    }
                }
            }
        }
    }

    @Test
    void crowdedWorldStillHasFreeNames() {
        // 40 держав з однією підкласифікацією, що має найменше форм, — тісніше, ніж буде в одному світі.
        SubIdeologyId narrowest = PACK.ideologies().values().stream()
                .flatMap(ideology -> ideology.subIdeologies().stream())
                .map(SubIdeologyDef::id)
                .min((a, b) -> Integer.compare(
                        PACK.stateFormsFor(a).size(), PACK.stateFormsFor(b).size()))
                .orElseThrow();
        TreeSet<String> taken = new TreeSet<>();
        for (long country = 0; country < 40; country++) {
            StartName name = NameWheel.generate(Rng.of(country), PACK, narrowest, taken);

            for (NameCandidate candidate : name.candidates()) {
                assertThat(taken).doesNotContain(candidate.name().fullName().nominative());
            }
            taken.add(name.name().fullName().nominative());
        }
        assertThat(taken).hasSize(40);
    }
}
