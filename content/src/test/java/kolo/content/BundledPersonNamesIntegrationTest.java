package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.TreeSet;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.content.NameFinalDef;
import kolo.engine.content.NameParadigmDef;
import kolo.engine.content.NameParadigmId;
import kolo.engine.content.PersonNameStyleDef;
import kolo.engine.content.SurnameFinalDef;
import kolo.engine.generation.name.CountryNames;
import kolo.engine.generation.name.PersonNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.GrammaticalCase;
import kolo.engine.state.LocalizedName;
import kolo.engine.state.Sex;
import org.junit.jupiter.api.Test;

/** Вбудовані імена людей ↔ генератор рушія: кожен стиль дає граматично цілісні імена обох статей. */
class BundledPersonNamesIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();
    private static final int SEEDS = 500;

    @Test
    void personParadigmsDeclineAsInUkrainian() {
        assertThat(decline("вел", "ор", "person_masc_hard"))
                .containsExactly("Велор", "Велора", "Велорові", "Велора", "Велором", "Велорові", "Велоре");
        assertThat(decline("тор", "і", "person_masc_j"))
                .containsExactly("Торій", "Торія", "Торієві", "Торія", "Торієм", "Торієві", "Торію");
        assertThat(decline("вел", "ен", "fem_hard"))
                .containsExactly("Велена", "Велени", "Велені", "Велену", "Веленою", "Велені", "Велено");
        assertThat(decline("дан", "енк", "surname_enko"))
                .containsExactly("Даненко", "Даненка", "Даненкові", "Даненка", "Даненком", "Даненкові", "Даненку");
        assertThat(decline("дан", "енк", "fixed_fem_o")).containsOnly("Даненко");
        assertThat(decline("сул", "ак", "surname_k"))
                .containsExactly("Сулак", "Сулака", "Сулакові", "Сулака", "Сулаком", "Сулакові", "Сулаку");
        assertThat(decline("грім", "ович", "surname_ich"))
                .containsExactly(
                        "Грімович", "Грімовича", "Грімовичеві", "Грімовича", "Грімовичем", "Грімовичеві", "Грімовичу");
        assertThat(decline("кар", "ов", "surname_ov"))
                .containsExactly("Каров", "Карова", "Карову", "Карова", "Каровим", "Карові", "Карове");
        assertThat(decline("кар", "ов", "adj_fem"))
                .containsExactly("Карова", "Карової", "Каровій", "Карову", "Каровою", "Каровій", "Карова");
        assertThat(decline("торв", "івськ", "adj_masc"))
                .containsExactly(
                        "Торвівський",
                        "Торвівського",
                        "Торвівському",
                        "Торвівського",
                        "Торвівським",
                        "Торвівському",
                        "Торвівський");
        assertThat(decline("торв", "ер", "fixed_fem")).containsOnly("Торвер");
    }

    @Test
    void everyCountryStyleNamesItsPeople() {
        assertThat(PACK.names().personStyles().keySet())
                .containsExactlyElementsOf(PACK.names().styles().keySet());
    }

    @Test
    void everyStyleGivesWellFormedVariedNamesOfBothSexes() {
        for (PersonNameStyleDef style : PACK.names().personStyles().values()) {
            for (Sex sex : Sex.values()) {
                TreeSet<String> names = new TreeSet<>();
                TreeSet<String> surnames = new TreeSet<>();
                for (long seed = 0; seed < SEEDS; seed++) {
                    LocalizedName name = PersonNames.generate(Rng.of(seed), PACK, style.id(), sex);
                    assertThat(name.fullName().gender()).isEqualTo(sex.gender());
                    assertThat(name.shortName().gender()).isEqualTo(sex.gender());
                    for (String form : name.fullName().forms()) {
                        // Ім'я й прізвище — кожне з великої української літери, далі малі літери чи апостроф.
                        assertThat(form).as(form).matches("[А-ЩЬЮЯҐЄІЇ][а-щьюяґєії']* [А-ЩЬЮЯҐЄІЇ][а-щьюяґєії']*");
                    }
                    names.add(name.fullName().nominative());
                    surnames.add(name.shortName().nominative());
                }

                // Імен вистачає, щоб у державі з кількома постатями вони майже не повторювалися.
                assertThat(names).as(style.id() + " " + sex.key()).hasSizeGreaterThan(SEEDS * 9 / 10);
                for (SurnameFinalDef surnameFinal : style.surnameFinals()) {
                    String ending = surnameFinal.text()
                            + paradigm(surnameFinal.paradigm(sex)).ending(GrammaticalCase.NOMINATIVE);
                    assertThat(surnames)
                            .as(style.id() + " " + sex.key() + " -" + ending)
                            .anyMatch(surname -> surname.endsWith(ending));
                }
            }
        }
    }

    private static List<String> decline(String stem, String text, String paradigm) {
        NameParadigmId id = new NameParadigmId(paradigm);
        return CountryNames.root(stem, new NameFinalDef(text, id), paradigm(id)).forms();
    }

    private static NameParadigmDef paradigm(NameParadigmId id) {
        return PACK.names().paradigm(id).orElseThrow();
    }
}
