package kolo.engine.generation.name;

import static kolo.engine.generation.name.TestNames.FEM_IYA;
import static kolo.engine.generation.name.TestNames.KINGDOM;
import static kolo.engine.generation.name.TestNames.MASC_HARD;
import static kolo.engine.generation.name.TestNames.PACK;
import static kolo.engine.generation.name.TestNames.REPUBLIC;
import static kolo.engine.generation.name.TestNames.SOUTHERN;
import static kolo.engine.generation.name.TestNames.UNITED_PROVINCES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.TreeSet;
import kolo.engine.content.NameFinalDef;
import kolo.engine.content.NameStyleId;
import kolo.engine.content.StateFormDef;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.rng.Rng;
import kolo.engine.state.GrammaticalCase;
import kolo.engine.state.GrammaticalGender;
import kolo.engine.state.LocalizedName;
import kolo.engine.state.NounPhrase;
import org.junit.jupiter.api.Test;

class CountryNamesTest {

    private static final SubIdeologyId LIBERAL = new SubIdeologyId("liberal_democracy");
    private static final SubIdeologyId DIRECT = new SubIdeologyId("direct_democracy");
    private static final SubIdeologyId ABSOLUTE = new SubIdeologyId("absolute_monarchy");

    @Test
    void rootTakesParadigmEndingsAndCapitalLetter() {
        NounPhrase velimor = CountryNames.root("велім", new NameFinalDef("ор", MASC_HARD.id()), MASC_HARD);

        assertThat(velimor.gender()).isEqualTo(GrammaticalGender.MASCULINE);
        assertThat(velimor.forms())
                .containsExactly("Велімор", "Велімору", "Велімору", "Велімор", "Велімором", "Веліморі", "Веліморе");

        NounPhrase garia = CountryNames.root("гар", new NameFinalDef("і", FEM_IYA.id()), FEM_IYA);
        assertThat(garia.gender()).isEqualTo(GrammaticalGender.FEMININE);
        assertThat(garia.forms()).containsExactly("Гарія", "Гарії", "Гарії", "Гарію", "Гарією", "Гарії", "Гаріє");
    }

    @Test
    void fullNameDeclinesFormAndKeepsRootInNominative() {
        NounPhrase garia = CountryNames.root("гар", new NameFinalDef("і", FEM_IYA.id()), FEM_IYA);

        LocalizedName name = CountryNames.name(UNITED_PROVINCES, garia);

        assertThat(name.fullName().gender()).isEqualTo(GrammaticalGender.PLURAL);
        assertThat(name.fullName().nominative()).isEqualTo("Об'єднані Провінції Гарія");
        assertThat(name.fullName().form(GrammaticalCase.GENITIVE)).isEqualTo("Об'єднаних Провінцій Гарія");
        assertThat(name.fullName().form(GrammaticalCase.INSTRUMENTAL)).isEqualTo("Об'єднаними Провінціями Гарія");
        assertThat(name.shortName()).isEqualTo(garia);
    }

    @Test
    void generatedFormSuitsSubIdeology() {
        TreeSet<String> liberalForms = new TreeSet<>();
        TreeSet<String> directForms = new TreeSet<>();
        TreeSet<String> monarchyForms = new TreeSet<>();
        for (long seed = 0; seed < 200; seed++) {
            liberalForms.add(formOf(CountryNames.generate(Rng.of(seed), PACK, LIBERAL)));
            directForms.add(formOf(CountryNames.generate(Rng.of(seed), PACK, DIRECT)));
            monarchyForms.add(formOf(CountryNames.generate(Rng.of(seed), PACK, ABSOLUTE)));
        }

        assertThat(liberalForms).containsExactly(REPUBLIC.id().value());
        assertThat(directForms)
                .containsExactly(REPUBLIC.id().value(), UNITED_PROVINCES.id().value());
        assertThat(monarchyForms).containsExactly(KINGDOM.id().value());
    }

    @Test
    void generationUsesEveryStylePartAndMiddleOnlyWhenStyleHasThem() {
        TreeSet<String> roots = new TreeSet<>();
        for (long seed = 0; seed < 2000; seed++) {
            roots.add(CountryNames.generate(Rng.of(seed), PACK, LIBERAL)
                    .shortName()
                    .nominative());
        }

        // Північний: 3 початки × (без вставки + 2 вставки) × 2 кінцівки; південний: 2 початки × 1 кінцівка.
        assertThat(roots).hasSize(3 * 3 * 2 + 2);
        assertThat(roots).contains("Велор", "Велімор", "Торенія", "Гарія", "Салан", "Маран");
    }

    @Test
    void givenStyleIsUsedForTheRoot() {
        TreeSet<String> roots = new TreeSet<>();
        for (long seed = 0; seed < 200; seed++) {
            roots.add(CountryNames.generate(Rng.of(seed), PACK, LIBERAL, SOUTHERN.id())
                    .shortName()
                    .nominative());
        }

        assertThat(roots).containsExactly("Маран", "Салан");
        assertThatThrownBy(() -> CountryNames.generate(Rng.of(1), PACK, LIBERAL, new NameStyleId("eastern")))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE));
    }

    @Test
    void sameSeedGivesSameName() {
        LocalizedName first = CountryNames.generate(Rng.of(42), PACK, DIRECT);
        LocalizedName second = CountryNames.generate(Rng.of(42), PACK, DIRECT);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void unknownSubIdeologyIsRejected() {
        assertThatThrownBy(() -> CountryNames.generate(Rng.of(1), PACK, new SubIdeologyId("revanchism")))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE));
    }

    private static String formOf(LocalizedName name) {
        String full = name.fullName().nominative();
        String root = name.shortName().nominative();
        for (StateFormDef form : PACK.names().stateForms().values()) {
            if (form.render(GrammaticalCase.NOMINATIVE, root).equals(full)) {
                return form.id().value();
            }
        }
        throw new AssertionError(full);
    }
}
