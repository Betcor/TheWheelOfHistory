package kolo.engine.content;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Шаблон релігій світу (GD §25.1): архетипи, аспекти божества, догмати, устрої й форми назви віри.
 *
 * <p>Списки — у порядку контенту: це порядок секторів коліс генерації релігії. Колеса йдуть так: архетип → аспекти →
 * догмати → устрій → назва, і кожне бачить мітки попередніх. Тому добавки до ваги можуть залежати лише від міток
 * частин, обраних раніше (або від міток частин того самого колеса — аспект від уже обраного аспекту).
 */
public final class ReligionContent {

    private final List<ArchetypeDef> archetypes;
    private final SortedMap<ArchetypeId, ArchetypeDef> archetypesById;
    private final List<AspectDef> aspects;
    private final SortedMap<AspectId, AspectDef> aspectsById;
    private final List<DogmaDef> dogmas;
    private final SortedMap<DogmaId, DogmaDef> dogmasById;
    private final List<ReligionPolityDef> polities;
    private final SortedMap<ReligionPolityId, ReligionPolityDef> politiesById;
    private final List<FaithFormDef> faithForms;
    private final SortedMap<FaithFormId, FaithFormDef> faithFormsById;

    /**
     * @param archetypes архетипи в порядку секторів колеса архетипу
     * @param aspects аспекти в порядку секторів колеса аспекту
     * @param dogmas догмати в порядку секторів колеса догмату
     * @param polities устрої в порядку секторів колеса устрою
     * @param faithForms форми назви віри; кожному архетипу доступна хоча б одна
     * @throws ValidationException якщо якась колекція порожня, id повторюється, догмат чи форма назви посилається на
     *     невідомий догмат чи архетип ({@link ErrorCode#UNKNOWN_REFERENCE}), архетипу не доступна жодна форма назви
     *     ({@link ErrorCode#MISSING_DEFINITION}) або добавка до ваги залежить від мітки, якої на цьому колесі ще не
     *     може бути ({@link ErrorCode#UNKNOWN_REFERENCE})
     */
    public ReligionContent(
            List<ArchetypeDef> archetypes,
            List<AspectDef> aspects,
            List<DogmaDef> dogmas,
            List<ReligionPolityDef> polities,
            List<FaithFormDef> faithForms) {
        this.archetypes = List.copyOf(archetypes);
        this.archetypesById = byId("archetypes", archetypes, ArchetypeDef::id);
        this.aspects = List.copyOf(aspects);
        this.aspectsById = byId("aspects", aspects, AspectDef::id);
        this.dogmas = List.copyOf(dogmas);
        this.dogmasById = byId("dogmas", dogmas, DogmaDef::id);
        this.polities = List.copyOf(polities);
        this.politiesById = byId("religion_polities", polities, ReligionPolityDef::id);
        this.faithForms = List.copyOf(faithForms);
        this.faithFormsById = byId("faith_forms", faithForms, FaithFormDef::id);

        for (DogmaDef dogma : this.dogmas) {
            for (DogmaId other : dogma.incompatible()) {
                if (!dogmasById.containsKey(other)) {
                    throw unknown("dogma." + dogma.id() + ".incompatible", other);
                }
            }
        }
        for (FaithFormDef form : this.faithForms) {
            for (ArchetypeId archetype : form.archetypes()) {
                if (!archetypesById.containsKey(archetype)) {
                    throw unknown("faith_form." + form.id() + ".archetypes", archetype);
                }
            }
        }
        // Інакше генератор не зможе назвати релігію з цим архетипом.
        for (ArchetypeDef archetype : this.archetypes) {
            if (faithFormsFor(archetype.id()).isEmpty()) {
                throw new ValidationException(
                        ErrorCode.MISSING_DEFINITION, ErrorDetails.of("field", "faith_forms", "value", archetype.id()));
            }
        }

        // Мітки, які релігія може мати на кожному колесі: від архетипу до устрою.
        TreeSet<String> known = new TreeSet<>();
        this.archetypes.forEach(archetype -> known.addAll(archetype.tags()));
        this.aspects.forEach(aspect -> known.addAll(aspect.tags()));
        for (AspectDef aspect : this.aspects) {
            checkTags("aspect." + aspect.id() + ".weight_tags", aspect.weightTags(), known);
        }
        this.dogmas.forEach(dogma -> known.addAll(dogma.tags()));
        for (DogmaDef dogma : this.dogmas) {
            checkTags("dogma." + dogma.id() + ".weight_tags", dogma.weightTags(), known);
        }
        this.polities.forEach(polity -> known.addAll(polity.tags()));
        for (ReligionPolityDef polity : this.polities) {
            checkTags("religion_polity." + polity.id() + ".weight_tags", polity.weightTags(), known);
        }
    }

    /** Архетипи в порядку контенту — порядок секторів колеса архетипу. */
    public List<ArchetypeDef> archetypes() {
        return archetypes;
    }

    public Optional<ArchetypeDef> archetype(ArchetypeId id) {
        return Optional.ofNullable(archetypesById.get(Objects.requireNonNull(id, "id")));
    }

    /** Аспекти в порядку контенту — порядок секторів колеса аспекту. */
    public List<AspectDef> aspects() {
        return aspects;
    }

    public Optional<AspectDef> aspect(AspectId id) {
        return Optional.ofNullable(aspectsById.get(Objects.requireNonNull(id, "id")));
    }

    /** Догмати в порядку контенту — порядок секторів колеса догмату. */
    public List<DogmaDef> dogmas() {
        return dogmas;
    }

    public Optional<DogmaDef> dogma(DogmaId id) {
        return Optional.ofNullable(dogmasById.get(Objects.requireNonNull(id, "id")));
    }

    /** Устрої в порядку контенту — порядок секторів колеса устрою. */
    public List<ReligionPolityDef> polities() {
        return polities;
    }

    public Optional<ReligionPolityDef> polity(ReligionPolityId id) {
        return Optional.ofNullable(politiesById.get(Objects.requireNonNull(id, "id")));
    }

    /** Форми назви віри в порядку контенту. */
    public List<FaithFormDef> faithForms() {
        return faithForms;
    }

    public Optional<FaithFormDef> faithForm(FaithFormId id) {
        return Optional.ofNullable(faithFormsById.get(Objects.requireNonNull(id, "id")));
    }

    /** Форми назви, доступні архетипу, в порядку контенту; для архетипу з контенту — хоча б одна. */
    public List<FaithFormDef> faithFormsFor(ArchetypeId archetype) {
        Objects.requireNonNull(archetype, "archetype");
        return faithForms.stream().filter(form -> form.appliesTo(archetype)).toList();
    }

    /**
     * Чи можуть два різні догмати бути в одній релігії. Несумісність симетрична: досить, щоб її вказав один з них.
     *
     * @throws ValidationException з {@link ErrorCode#UNKNOWN_REFERENCE}, якщо догмату немає в контенті
     */
    public boolean compatible(DogmaId a, DogmaId b) {
        DogmaDef first = known(a);
        DogmaDef second = known(b);
        return !a.equals(b)
                && !first.incompatible().contains(b)
                && !second.incompatible().contains(a);
    }

    /** Мітки, які може мати релігія: з архетипів, аспектів, догматів і устроїв. */
    public SortedSet<String> producedTags() {
        TreeSet<String> produced = new TreeSet<>();
        archetypes.forEach(archetype -> produced.addAll(archetype.tags()));
        aspects.forEach(aspect -> produced.addAll(aspect.tags()));
        dogmas.forEach(dogma -> produced.addAll(dogma.tags()));
        polities.forEach(polity -> produced.addAll(polity.tags()));
        return Collections.unmodifiableSortedSet(produced);
    }

    private DogmaDef known(DogmaId id) {
        DogmaDef dogma = dogmasById.get(Objects.requireNonNull(id, "dogma"));
        if (dogma == null) {
            throw unknown("dogma", id);
        }
        return dogma;
    }

    private static void checkTags(String field, SortedMap<String, Integer> weightTags, SortedSet<String> known) {
        for (String tag : weightTags.keySet()) {
            if (!known.contains(tag)) {
                throw unknown(field, tag);
            }
        }
    }

    private static ValidationException unknown(String field, Object value) {
        return new ValidationException(ErrorCode.UNKNOWN_REFERENCE, ErrorDetails.of("field", field, "value", value));
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
