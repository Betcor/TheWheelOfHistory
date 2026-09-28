package kolo.engine.content;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Увесь контент гри: незмінний, зібраний і перевірений при старті.
 *
 * <p>Світ фіксує {@link #hash()} при створенні; клієнт і сервер звіряють його при підключенні. Колекції —
 * впорядковані за id, тож ітерація детермінована.
 */
public final class ContentPack {

    private final String hash;
    private final SortedMap<IdeologyId, IdeologyDef> ideologies;
    private final SortedMap<SubIdeologyId, IdeologyId> subIdeologyOwners;
    private final SortedMap<DoctrineId, DoctrineDef> doctrines;
    private final SortedMap<ResourceId, ResourceDef> resources;

    /**
     * @param hash хеш вихідних файлів контенту
     * @throws ValidationException якщо якась колекція порожня або id повторюється, зокрема id підкласифікацій
     *     різних ідеологій
     */
    public ContentPack(
            String hash, List<IdeologyDef> ideologies, List<DoctrineDef> doctrines, List<ResourceDef> resources) {
        this.hash = Checks.notBlank("content.hash", hash);

        TreeMap<IdeologyId, IdeologyDef> ideologyMap = new TreeMap<>();
        TreeMap<SubIdeologyId, IdeologyId> owners = new TreeMap<>();
        for (IdeologyDef ideology : nonEmpty("ideologies", ideologies)) {
            put("ideology.id", ideologyMap, ideology.id(), ideology);
            for (SubIdeologyDef sub : ideology.subIdeologies()) {
                put("sub_ideology.id", owners, sub.id(), ideology.id());
            }
        }
        this.ideologies = Collections.unmodifiableSortedMap(ideologyMap);
        this.subIdeologyOwners = Collections.unmodifiableSortedMap(owners);

        TreeMap<DoctrineId, DoctrineDef> doctrineMap = new TreeMap<>();
        for (DoctrineDef doctrine : nonEmpty("doctrines", doctrines)) {
            put("doctrine.id", doctrineMap, doctrine.id(), doctrine);
        }
        this.doctrines = Collections.unmodifiableSortedMap(doctrineMap);

        TreeMap<ResourceId, ResourceDef> resourceMap = new TreeMap<>();
        for (ResourceDef resource : nonEmpty("resources", resources)) {
            put("resource.id", resourceMap, resource.id(), resource);
        }
        this.resources = Collections.unmodifiableSortedMap(resourceMap);
    }

    /** Хеш вихідних файлів контенту: однаковий на всіх машинах для однакових файлів. */
    public String hash() {
        return hash;
    }

    public SortedMap<IdeologyId, IdeologyDef> ideologies() {
        return ideologies;
    }

    public Optional<IdeologyDef> ideology(IdeologyId id) {
        return Optional.ofNullable(ideologies.get(id));
    }

    /** Підкласифікація за id, серед усіх ідеологій. */
    public Optional<SubIdeologyDef> subIdeology(SubIdeologyId id) {
        return ideologyOf(id).flatMap(ideology -> ideology.subIdeology(id));
    }

    /** Ідеологія, якій належить підкласифікація. */
    public Optional<IdeologyDef> ideologyOf(SubIdeologyId id) {
        return Optional.ofNullable(subIdeologyOwners.get(id)).map(ideologies::get);
    }

    public SortedMap<DoctrineId, DoctrineDef> doctrines() {
        return doctrines;
    }

    public Optional<DoctrineDef> doctrine(DoctrineId id) {
        return Optional.ofNullable(doctrines.get(id));
    }

    public SortedMap<ResourceId, ResourceDef> resources() {
        return resources;
    }

    public Optional<ResourceDef> resource(ResourceId id) {
        return Optional.ofNullable(resources.get(id));
    }

    private static <T> List<T> nonEmpty(String field, List<T> values) {
        if (values.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", field));
        }
        return values;
    }

    private static <K extends Comparable<K>, V> void put(String field, TreeMap<K, V> map, K key, V value) {
        Objects.requireNonNull(value, field);
        if (map.putIfAbsent(key, value) != null) {
            throw new ValidationException(ErrorCode.DUPLICATE_ID, ErrorDetails.of("field", field, "value", key));
        }
    }
}
