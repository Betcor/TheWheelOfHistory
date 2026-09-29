package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestBackstory.CIVIL_WAR;
import static kolo.engine.generation.country.TestBackstory.FAMINE;
import static kolo.engine.generation.country.TestBackstory.FLOOD;
import static kolo.engine.generation.country.TestBackstory.GOLDEN_AGE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.engine.content.BackstoryFragmentDef;
import kolo.engine.content.BackstoryFragmentId;
import kolo.engine.content.BackstoryText;
import kolo.engine.content.ModifierDef;
import kolo.engine.content.TagCondition;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierSource;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.modifier.SourceKind;
import kolo.engine.rng.Rng;
import kolo.engine.state.Stat;
import org.junit.jupiter.api.Test;

/** Якість передісторії для стріків генерації — одна на всю передісторію; модифікатори фрагментів — держави. */
class BackstoryQualityTest {

    @Test
    void qualityIsFlooredAverageOfFragments() {
        // Повінь 20, голод 10: (20 + 10) / 2 = 15 — дуже погано, як і кожен фрагмент окремо.
        assertThat(backstory(new BackstoryEntry(FLOOD, 1950), new BackstoryEntry(FAMINE, 1960))
                        .quality())
                .hasValue(15);
        // (20 + 10 + 90) / 3 = 40.
        assertThat(backstory(
                                new BackstoryEntry(FLOOD, 1920),
                                new BackstoryEntry(FAMINE, 1930),
                                new BackstoryEntry(GOLDEN_AGE, 1940))
                        .quality())
                .hasValue(40);
        assertThat(backstory(new BackstoryEntry(GOLDEN_AGE, 1960)).quality()).hasValue(90);
        // Громадянська війна 15, повінь 20: 17,5 округлюється вниз.
        assertThat(backstory(new BackstoryEntry(CIVIL_WAR, 1950), new BackstoryEntry(FLOOD, 1960))
                        .quality())
                .hasValue(17);
    }

    @Test
    void emptyBackstoryIsNotCounted() {
        assertThat(backstory().quality()).isEmpty();
    }

    @Test
    void generatedBackstoryHasQualityOfItsFragments() {
        Backstory backstory = BackstoryWheel.generate(Rng.of(5), TestBackstory.PACK, new TreeSet<>(), List.of());
        int sum = backstory.entries().stream()
                .mapToInt(entry -> entry.fragment().quality())
                .sum();

        assertThat(backstory.quality()).hasValue(sum / backstory.entries().size());
    }

    @Test
    void modifiersOfFragmentsBecomeCountryModifiers() {
        // Слава: два модифікатори на 5 років; повінь — без модифікаторів.
        Backstory backstory = backstory(new BackstoryEntry(FLOOD, 1950), new BackstoryEntry(TestChain.GLORY, 1960));

        List<Modifier> modifiers = backstory.modifiers();

        assertThat(modifiers).extracting(Modifier::id).containsExactly("backstory:glory:0", "backstory:glory:1");
        assertThat(modifiers).allSatisfy(modifier -> {
            assertThat(modifier.source()).isEqualTo(new ModifierSource(SourceKind.BACKSTORY, "glory"));
            assertThat(modifier.descriptionKey()).isEqualTo("backstory.glory");
            assertThat(modifier.expiresAtTurn()).isEqualTo(4);
        });
        assertThat(modifiers)
                .extracting(Modifier::target)
                .containsExactly(ModifierTarget.stat(Stat.INFLUENCE), ModifierTarget.stat(Stat.LEGITIMACY));
    }

    @Test
    void fragmentWithoutDurationGivesPermanentModifiers() {
        BackstoryFragmentDef permanent = new BackstoryFragmentDef(
                new BackstoryFragmentId("reform"),
                100,
                50,
                1950,
                1969,
                TagCondition.NONE,
                new TreeMap<>(),
                false,
                List.of(),
                0,
                List.of(new ModifierDef(ModifierTarget.stat(Stat.STABILITY), -5)),
                new BackstoryText("text", "Реформа {year} року."));

        List<Modifier> modifiers =
                backstory(new BackstoryEntry(permanent, 1955)).modifiers();

        assertThat(modifiers).singleElement().satisfies(modifier -> {
            assertThat(modifier.expiresAtTurn()).isNull();
            assertThat(modifier.value()).isEqualTo(-5);
        });
    }

    private static Backstory backstory(BackstoryEntry... entries) {
        return new Backstory(List.of(entries), Optional.empty(), new TreeSet<>(), List.of());
    }
}
