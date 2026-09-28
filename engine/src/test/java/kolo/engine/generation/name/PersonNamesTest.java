package kolo.engine.generation.name;

import static kolo.engine.generation.name.TestNames.NORTHERN;
import static kolo.engine.generation.name.TestNames.PACK;
import static kolo.engine.generation.name.TestNames.SOUTHERN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.TreeSet;
import kolo.engine.content.NameStyleId;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.rng.Rng;
import kolo.engine.state.GrammaticalCase;
import kolo.engine.state.GrammaticalGender;
import kolo.engine.state.LocalizedName;
import kolo.engine.state.NounPhrase;
import kolo.engine.state.Sex;
import org.junit.jupiter.api.Test;

class PersonNamesTest {

    @Test
    void maleNameAndSurnameDeclineAsAnimateNouns() {
        LocalizedName name = PersonNames.generate(Rng.of(1), PACK, SOUTHERN.id(), Sex.MALE);

        assertThat(name.fullName().gender()).isEqualTo(GrammaticalGender.MASCULINE);
        assertThat(name.fullName().forms())
                .containsExactly(
                        "Салан Марес",
                        "Салана Мареса",
                        "Саланові Маресові",
                        "Салана Мареса",
                        "Саланом Маресом",
                        "Саланові Маресові",
                        "Салане Маресе");
        assertThat(name.shortName().forms())
                .containsExactly("Марес", "Мареса", "Маресові", "Мареса", "Маресом", "Маресові", "Маресе");
    }

    @Test
    void femaleSurnameOnConsonantDoesNotDecline() {
        LocalizedName name = PersonNames.generate(Rng.of(1), PACK, SOUTHERN.id(), Sex.FEMALE);

        assertThat(name.fullName().gender()).isEqualTo(GrammaticalGender.FEMININE);
        assertThat(name.fullName().forms())
                .containsExactly(
                        "Саліна Марес",
                        "Саліни Марес",
                        "Саліні Марес",
                        "Саліну Марес",
                        "Саліною Марес",
                        "Саліні Марес",
                        "Саліно Марес");
        assertThat(name.shortName().gender()).isEqualTo(GrammaticalGender.FEMININE);
        assertThat(name.shortName().forms()).containsOnly("Марес");
    }

    @Test
    void generationUsesEveryPartOfTheStyleForBothSexes() {
        TreeSet<String> male = new TreeSet<>();
        TreeSet<String> female = new TreeSet<>();
        for (long seed = 0; seed < 2000; seed++) {
            male.add(PersonNames.generate(Rng.of(seed), PACK, NORTHERN.id(), Sex.MALE)
                    .fullName()
                    .nominative());
            female.add(PersonNames.generate(Rng.of(seed), PACK, NORTHERN.id(), Sex.FEMALE)
                    .fullName()
                    .nominative());
        }

        // Ім'я: 2 початки × (без вставки + 1 вставка) × кінцівки статі; прізвище: 2 початки × 1 кінцівка.
        assertThat(male).hasSize(2 * 2 * 1 * 2);
        assertThat(female).hasSize(2 * 2 * 2 * 2);
        assertThat(male).contains("Велор Торвер", "Веларор Гальмер", "Торор Торвер");
        assertThat(female).contains("Велена Торвер", "Торарія Гальмер", "Велія Гальмер");
    }

    @Test
    void sameSeedGivesSameName() {
        assertThat(PersonNames.generate(Rng.of(42), PACK, NORTHERN.id(), Sex.FEMALE))
                .isEqualTo(PersonNames.generate(Rng.of(42), PACK, NORTHERN.id(), Sex.FEMALE));
    }

    @Test
    void nameJoinsGivenNameAndSurnameCaseByCase() {
        NounPhrase given = new NounPhrase(GrammaticalGender.MASCULINE, List.of("А", "Б", "В", "Г", "Ґ", "Д", "Е"));
        NounPhrase surname = new NounPhrase(GrammaticalGender.MASCULINE, List.of("а", "б", "в", "г", "ґ", "д", "е"));

        LocalizedName name = PersonNames.name(given, surname);

        assertThat(name.fullName().form(GrammaticalCase.INSTRUMENTAL)).isEqualTo("Ґ ґ");
        assertThat(name.shortName()).isEqualTo(surname);

        NounPhrase female = new NounPhrase(GrammaticalGender.FEMININE, surname.forms());
        assertThatThrownBy(() -> PersonNames.name(given, female))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NAME_GENDER_MISMATCH));
    }

    @Test
    void unknownStyleIsRejected() {
        assertThatThrownBy(() -> PersonNames.generate(Rng.of(1), PACK, new NameStyleId("eastern"), Sex.MALE))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE));
    }
}
