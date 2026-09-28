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

/**
 * Усе для назв держав (GD §4.9): парадигми відмінювання, мовні стилі коренів і форми державності. Колекції — за id.
 *
 * <p>Що кожній підкласифікації доступна хоча б одна форма, перевіряє {@link ContentPack}: лише він знає ідеології.
 */
public final class NameContent {

    private final SortedMap<NameParadigmId, NameParadigmDef> paradigms;
    private final SortedMap<NameStyleId, NameStyleDef> styles;
    private final SortedMap<StateFormId, StateFormDef> stateForms;

    /**
     * @throws ValidationException якщо якась колекція порожня, id повторюється або кінцівка посилається на невідому
     *     парадигму
     */
    public NameContent(List<NameParadigmDef> paradigms, List<NameStyleDef> styles, List<StateFormDef> stateForms) {
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
