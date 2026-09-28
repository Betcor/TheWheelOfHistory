package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.NameFinalDef;
import kolo.engine.content.NameParadigmId;
import kolo.engine.content.StateFormDef;
import kolo.engine.content.StateFormId;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.generation.name.CountryNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.GrammaticalCase;
import kolo.engine.state.GrammaticalGender;
import kolo.engine.state.LocalizedName;
import kolo.engine.state.NounPhrase;
import org.junit.jupiter.api.Test;

/** Вбудовані назви ↔ генератор рушія: кожна підкласифікація отримує граматично цілісні назви. */
class BundledNamesIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();
    private static final int SEEDS = 500;

    @Test
    void paradigmsDeclineAsInUkrainian() {
        assertThat(root("вел", "ор", "masc_hard"))
                .containsExactly("Велор", "Велору", "Велору", "Велор", "Велором", "Велорі", "Велоре");
        assertThat(root("вард", "ел", "masc_soft"))
                .containsExactly("Вардель", "Варделю", "Варделю", "Вардель", "Варделем", "Варделі", "Варделю");
        assertThat(root("сал", "а", "masc_j"))
                .containsExactly("Салай", "Салаю", "Салаю", "Салай", "Салаєм", "Салаї", "Салаю");
        assertThat(root("тор", "ов", "fem_hard"))
                .containsExactly("Торова", "Торови", "Торові", "Торову", "Торовою", "Торові", "Торово");
        assertThat(root("гар", "і", "fem_iya"))
                .containsExactly("Гарія", "Гарії", "Гарії", "Гарію", "Гарією", "Гарії", "Гаріє");
    }

    @Test
    void everySubIdeologyGetsWellFormedNames() {
        TreeSet<String> roots = new TreeSet<>();
        TreeMap<StateFormId, Integer> formUse = new TreeMap<>();
        for (IdeologyDef ideology : PACK.ideologies().values()) {
            for (SubIdeologyDef sub : ideology.subIdeologies()) {
                for (long seed = 0; seed < SEEDS; seed++) {
                    LocalizedName name = CountryNames.generate(Rng.of(seed), PACK, sub.id());
                    assertWellFormed(name.fullName());
                    assertWellFormed(name.shortName());
                    roots.add(name.shortName().nominative());
                    formUse.merge(formOf(sub.id(), name), 1, Integer::sum);
                }
            }
        }

        // Кожна форма державності реально трапляється, а коренів вистачає на світ із десятками держав.
        assertThat(formUse.keySet())
                .containsExactlyElementsOf(PACK.names().stateForms().keySet());
        assertThat(roots).hasSizeGreaterThan(300);
    }

    @Test
    void stylesProduceBothGendersOfShortNames() {
        TreeSet<GrammaticalGender> genders = new TreeSet<>();
        for (long seed = 0; seed < SEEDS; seed++) {
            genders.add(CountryNames.generate(Rng.of(seed), PACK, new SubIdeologyId("liberal_democracy"))
                    .shortName()
                    .gender());
        }

        assertThat(genders).contains(GrammaticalGender.MASCULINE, GrammaticalGender.FEMININE);
    }

    private static List<String> root(String stem, String text, String paradigm) {
        NameFinalDef nameFinal = new NameFinalDef(text, new NameParadigmId(paradigm));
        return CountryNames.root(stem, nameFinal, PACK.names().paradigm(nameFinal))
                .forms();
    }

    private static StateFormId formOf(SubIdeologyId sub, LocalizedName name) {
        String root = name.shortName().nominative();
        for (StateFormDef form : PACK.stateFormsFor(sub)) {
            if (form.render(GrammaticalCase.NOMINATIVE, root)
                    .equals(name.fullName().nominative())) {
                assertThat(name.fullName().gender()).isEqualTo(form.gender());
                return form.id();
            }
        }
        throw new AssertionError("форма недоступна " + sub + ": " + name);
    }

    private static void assertWellFormed(NounPhrase phrase) {
        for (String form : phrase.forms()) {
            // Кожне слово — з великої української літери, далі малі літери чи апостроф.
            assertThat(form).as(form).matches("[А-ЩЬЮЯҐЄІЇ][а-щьюяґєії']*( [А-ЩЬЮЯҐЄІЇ][а-щьюяґєії']*)*");
        }
    }
}
