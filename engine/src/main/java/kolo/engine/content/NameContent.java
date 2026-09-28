package kolo.engine.content;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.GrammaticalGender;

/**
 * Усе для назв держав (GD §4.9) та імен людей: парадигми відмінювання, мовні стилі коренів, форми державності й
 * стилі імен. Колекції — за id.
 *
 * <p>Стилі імен людей відповідають стилям назв один до одного: у кожної держави є мова, якою звуть її людей.
 *
 * <p>Що кожній підкласифікації доступна хоча б одна форма, перевіряє {@link ContentPack}: лише він знає ідеології.
 */
public final class NameContent {

    private final SortedMap<NameParadigmId, NameParadigmDef> paradigms;
    private final SortedMap<NameStyleId, NameStyleDef> styles;
    private final SortedMap<StateFormId, StateFormDef> stateForms;
    private final SortedMap<NameStyleId, PersonNameStyleDef> personStyles;

    /**
     * @throws ValidationException якщо якась колекція порожня, id повторюється, кінцівка посилається на невідому
     *     парадигму чи на парадигму не того роду, або стилі імен людей не відповідають стилям назв один до одного
     */
    public NameContent(
            List<NameParadigmDef> paradigms,
            List<NameStyleDef> styles,
            List<StateFormDef> stateForms,
            List<PersonNameStyleDef> personStyles) {
        this.paradigms = byId("name_paradigms", paradigms, NameParadigmDef::id);
        this.styles = byId("name_styles", styles, NameStyleDef::id);
        for (NameStyleDef style : this.styles.values()) {
            for (NameFinalDef nameFinal : style.finals()) {
                if (!this.paradigms.containsKey(nameFinal.paradigm())) {
                    throw new ValidationException(
                            ErrorCode.UNKNOWN_REFERENCE,
                            ErrorDetails.of(
                                    "field", "name_style." + style.id() + ".finals", "value", nameFinal.paradigm()));
                }
            }
        }
        this.stateForms = byId("state_forms", stateForms, StateFormDef::id);

        this.personStyles = byId("person_name_styles", personStyles, PersonNameStyleDef::id);
        for (PersonNameStyleDef style : this.personStyles.values()) {
            String field = "person_name_style." + style.id();
            if (!this.styles.containsKey(style.id())) {
                throw new ValidationException(
                        ErrorCode.UNKNOWN_REFERENCE, ErrorDetails.of("field", field + ".id", "value", style.id()));
            }
            for (NameFinalDef nameFinal : style.maleFinals()) {
                checkGender(field + ".male_finals", nameFinal.paradigm(), GrammaticalGender.MASCULINE);
            }
            for (NameFinalDef nameFinal : style.femaleFinals()) {
                checkGender(field + ".female_finals", nameFinal.paradigm(), GrammaticalGender.FEMININE);
            }
            for (SurnameFinalDef surnameFinal : style.surnameFinals()) {
                checkGender(field + ".surname_finals", surnameFinal.male(), GrammaticalGender.MASCULINE);
                checkGender(field + ".surname_finals", surnameFinal.female(), GrammaticalGender.FEMININE);
            }
        }
        // Інакше людей держави з цим стилем назви не буде як назвати.
        for (NameStyleId style : this.styles.keySet()) {
            if (!this.personStyles.containsKey(style)) {
                throw new ValidationException(
                        ErrorCode.MISSING_DEFINITION, ErrorDetails.of("field", "person_name_styles", "value", style));
            }
        }
    }

    public SortedMap<NameParadigmId, NameParadigmDef> paradigms() {
        return paradigms;
    }

    /** Парадигма кінцівки; існує завжди — посилання перевірено при створенні. */
    public NameParadigmDef paradigm(NameFinalDef nameFinal) {
        return paradigms.get(Objects.requireNonNull(nameFinal, "final").paradigm());
    }

    public SortedMap<NameStyleId, NameStyleDef> styles() {
        return styles;
    }

    public Optional<NameStyleDef> style(NameStyleId id) {
        return Optional.ofNullable(styles.get(id));
    }

    public SortedMap<StateFormId, StateFormDef> stateForms() {
        return stateForms;
    }

    public Optional<StateFormDef> stateForm(StateFormId id) {
        return Optional.ofNullable(stateForms.get(id));
    }

    /** Стилі імен людей; ключі збігаються з ключами {@link #styles()}. */
    public SortedMap<NameStyleId, PersonNameStyleDef> personStyles() {
        return personStyles;
    }

    public Optional<PersonNameStyleDef> personStyle(NameStyleId id) {
        return Optional.ofNullable(personStyles.get(id));
    }

    /** Парадигма за id; існує для кожного посилання з контенту — перевірено при створенні. */
    public Optional<NameParadigmDef> paradigm(NameParadigmId id) {
        return Optional.ofNullable(paradigms.get(id));
    }

    private void checkGender(String field, NameParadigmId id, GrammaticalGender expected) {
        NameParadigmDef paradigm = paradigms.get(id);
        if (paradigm == null) {
            throw new ValidationException(ErrorCode.UNKNOWN_REFERENCE, ErrorDetails.of("field", field, "value", id));
        }
        if (paradigm.gender() != expected) {
            throw new ValidationException(
                    ErrorCode.NAME_GENDER_MISMATCH,
                    ErrorDetails.of("field", field, "value", id, "expected", expected.key()));
        }
    }

    private static <K extends Comparable<K>, V> SortedMap<K, V> byId(String field, List<V> values, Function<V, K> id) {
        if (values.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", field));
        }
        TreeMap<K, V> map = new TreeMap<>();
        for (V value : values) {
            Objects.requireNonNull(value, field);
            K key = id.apply(value);
            if (map.putIfAbsent(key, value) != null) {
                throw new ValidationException(ErrorCode.DUPLICATE_ID, ErrorDetails.of("field", field, "value", key));
            }
        }
        return Collections.unmodifiableSortedMap(map);
    }
}
