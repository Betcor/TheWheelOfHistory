package kolo.engine.content;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.Development;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.TechBranch;

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
    private final SortedMap<TechBranch, TechBranchDef> techBranches;
    private final SortedMap<Integer, DevelopmentLevelDef> developmentLevels;
    private final SortedMap<NuclearStatus, NuclearStatusDef> nuclearStatuses;

    /**
     * @param hash хеш вихідних файлів контенту
     * @param techBranches рівно по одному визначенню на кожну {@link TechBranch}
     * @param developmentLevels рівно по одному визначенню на кожен рівень {@link Development#MIN}..{@link
     *     Development#MAX}
     * @param nuclearStatuses рівно по одному визначенню на кожен {@link NuclearStatus}
     * @throws ValidationException якщо якась колекція порожня, id повторюється (зокрема id підкласифікацій різних
     *     ідеологій) або бракує визначення галузі, рівня чи статусу
     */
    public ContentPack(
            String hash,
            List<IdeologyDef> ideologies,
            List<DoctrineDef> doctrines,
            List<ResourceDef> resources,
            List<TechBranchDef> techBranches,
            List<DevelopmentLevelDef> developmentLevels,
            List<NuclearStatusDef> nuclearStatuses) {
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

        TreeMap<TechBranch, TechBranchDef> branchMap = new TreeMap<>();
        for (TechBranchDef branch : techBranches) {
            put("tech_branch.id", branchMap, branch.branch(), branch.branch().key(), branch);
        }
        this.techBranches = complete("tech_branches", branchMap, List.of(TechBranch.values()), TechBranch::key);

        TreeMap<Integer, DevelopmentLevelDef> levelMap = new TreeMap<>();
        for (DevelopmentLevelDef level : developmentLevels) {
            put("development_level.level", levelMap, level.level(), level.level(), level);
        }
        this.developmentLevels = complete("development_levels", levelMap, Development.levels(), level -> level);

        TreeMap<NuclearStatus, NuclearStatusDef> nuclearMap = new TreeMap<>();
        for (NuclearStatusDef status : nuclearStatuses) {
            put(
                    "nuclear_status.id",
                    nuclearMap,
                    status.status(),
                    status.status().key(),
                    status);
        }
        this.nuclearStatuses =
                complete("nuclear_statuses", nuclearMap, List.of(NuclearStatus.values()), NuclearStatus::key);
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

    /** Галузі технологій у порядку enum; визначено кожну. */
    public SortedMap<TechBranch, TechBranchDef> techBranches() {
        return techBranches;
    }

    public TechBranchDef techBranch(TechBranch branch) {
        return techBranches.get(Objects.requireNonNull(branch, "branch"));
    }

    /** Рівні розвиненості від {@link Development#MIN} до {@link Development#MAX}; визначено кожен. */
    public SortedMap<Integer, DevelopmentLevelDef> developmentLevels() {
        return developmentLevels;
    }

    /** @throws ValidationException якщо рівень поза {@link Development#MIN}..{@link Development#MAX} */
    public DevelopmentLevelDef developmentLevel(int level) {
        return developmentLevels.get(Development.check("development_level", level));
    }

    /** Ядерні статуси в порядку enum; визначено кожен. */
    public SortedMap<NuclearStatus, NuclearStatusDef> nuclearStatuses() {
        return nuclearStatuses;
    }

    public NuclearStatusDef nuclearStatus(NuclearStatus status) {
        return nuclearStatuses.get(Objects.requireNonNull(status, "status"));
    }

    private static <T> List<T> nonEmpty(String field, List<T> values) {
        if (values.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", field));
        }
        return values;
    }

    /**
     * Кожен ключ з {@code expected} має визначення, інакше {@link ErrorCode#MISSING_DEFINITION}.
     *
     * @param display як показати ключ у подробицях помилки: ключ контенту, а не ім'я константи enum
     */
    private static <K extends Comparable<K>, V> SortedMap<K, V> complete(
            String field, TreeMap<K, V> map, Collection<K> expected, Function<K, Object> display) {
        for (K key : expected) {
            if (!map.containsKey(key)) {
                throw new ValidationException(
                        ErrorCode.MISSING_DEFINITION, ErrorDetails.of("field", field, "value", display.apply(key)));
            }
        }
        return Collections.unmodifiableSortedMap(map);
    }

    private static <K extends Comparable<K>, V> void put(String field, TreeMap<K, V> map, K key, V value) {
        put(field, map, key, key, value);
    }

    /** @param shown ключ у подробицях помилки */
    private static <K extends Comparable<K>, V> void put(
            String field, TreeMap<K, V> map, K key, Object shown, V value) {
        Objects.requireNonNull(value, field);
        if (map.putIfAbsent(key, value) != null) {
            throw new ValidationException(ErrorCode.DUPLICATE_ID, ErrorDetails.of("field", field, "value", shown));
        }
    }
}
