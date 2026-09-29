package kolo.engine.content;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.Development;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.PersonKind;
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
    private final List<IdeologyDef> ideologiesInContentOrder;
    private final SortedMap<SubIdeologyId, IdeologyId> subIdeologyOwners;
    private final SortedMap<DoctrineId, DoctrineDef> doctrines;
    private final SortedMap<ResourceId, ResourceDef> resources;
    private final SortedMap<TechBranch, TechBranchDef> techBranches;
    private final SortedMap<Integer, DevelopmentLevelDef> developmentLevels;
    private final SortedMap<NuclearStatus, NuclearStatusDef> nuclearStatuses;
    private final SortedMap<PersonKind, PersonKindDef> personKinds;
    private final SortedMap<TraitId, TraitDef> traits;
    private final NameContent names;
    private final BackstoryContent backstory;
    private final BalanceDef balance;

    /**
     * @param hash хеш вихідних файлів контенту
     * @param ideologies ідеології в порядку контенту (порядок секторів колеса ідеології)
     * @param techBranches рівно по одному визначенню на кожну {@link TechBranch}
     * @param developmentLevels рівно по одному визначенню на кожен рівень {@link Development#MIN}..{@link
     *     Development#MAX}
     * @param nuclearStatuses рівно по одному визначенню на кожен {@link NuclearStatus}
     * @param personKinds рівно по одному визначенню на кожен {@link PersonKind}
     * @param traits риси постатей; кожному типу постаті доступна хоча б одна
     * @param names назви держав; кожній підкласифікації доступна хоча б одна форма державності
     * @param backstory фрагменти передісторії; кожна мітка в їхніх умовах і вагах має джерело: ідеологію,
     *     підкласифікацію, рівень розвиненості, ядерний статус, фрагмент або словник міток коліс генерації
     * @throws ValidationException якщо якась колекція порожня, id повторюється (зокрема id підкласифікацій різних
     *     ідеологій), бракує визначення галузі, рівня, статусу чи типу постаті, риса посилається на невідому рису,
     *     типу постаті не доступна жодна риса, форма державності посилається на невідому ідеологію чи
     *     підкласифікацію, підкласифікації не доступна жодна форма або фрагмент передісторії залежить від мітки без
     *     джерела
     */
    public ContentPack(
            String hash,
            List<IdeologyDef> ideologies,
            List<DoctrineDef> doctrines,
            List<ResourceDef> resources,
            List<TechBranchDef> techBranches,
            List<DevelopmentLevelDef> developmentLevels,
            List<NuclearStatusDef> nuclearStatuses,
            List<PersonKindDef> personKinds,
            List<TraitDef> traits,
            NameContent names,
            BackstoryContent backstory,
            BalanceDef balance) {
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
        this.ideologiesInContentOrder = List.copyOf(ideologies);
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
        this.techBranches = Defs.complete("tech_branches", branchMap, List.of(TechBranch.values()), TechBranch::key);

        TreeMap<Integer, DevelopmentLevelDef> levelMap = new TreeMap<>();
        for (DevelopmentLevelDef level : developmentLevels) {
            put("development_level.level", levelMap, level.level(), level.level(), level);
        }
        this.developmentLevels = Defs.complete("development_levels", levelMap, Development.levels(), level -> level);

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
                Defs.complete("nuclear_statuses", nuclearMap, List.of(NuclearStatus.values()), NuclearStatus::key);

        TreeMap<PersonKind, PersonKindDef> kindMap = new TreeMap<>();
        for (PersonKindDef kind : personKinds) {
            put("person_kind.id", kindMap, kind.kind(), kind.kind().key(), kind);
        }
        this.personKinds = Defs.complete("person_kinds", kindMap, List.of(PersonKind.values()), PersonKind::key);

        TreeMap<TraitId, TraitDef> traitMap = new TreeMap<>();
        for (TraitDef trait : nonEmpty("traits", traits)) {
            put("trait.id", traitMap, trait.id(), trait);
        }
        for (TraitDef trait : traitMap.values()) {
            for (TraitId other : trait.incompatible()) {
                if (!traitMap.containsKey(other)) {
                    throw new ValidationException(
                            ErrorCode.UNKNOWN_REFERENCE,
                            ErrorDetails.of("field", "trait." + trait.id() + ".incompatible", "value", other));
                }
            }
        }
        // Інакше генератор не зможе дати постаті цього типу жодної риси.
        for (PersonKind kind : PersonKind.values()) {
            if (traitMap.values().stream().noneMatch(trait -> trait.allows(kind))) {
                throw new ValidationException(
                        ErrorCode.MISSING_DEFINITION, ErrorDetails.of("field", "traits", "value", kind.key()));
            }
        }
        this.traits = Collections.unmodifiableSortedMap(traitMap);

        this.names = Objects.requireNonNull(names, "names");
        for (StateFormDef form : names.stateForms().values()) {
            for (IdeologyId ideology : form.ideologies()) {
                if (!ideologyMap.containsKey(ideology)) {
                    throw unknown("state_form." + form.id() + ".ideologies", ideology);
                }
            }
            for (SubIdeologyId sub : form.subIdeologies()) {
                if (!owners.containsKey(sub)) {
                    throw unknown("state_form." + form.id() + ".sub_ideologies", sub);
                }
            }
        }
        // Інакше генератор не зможе скласти повну назву державі з цією підкласифікацією.
        for (SubIdeologyId sub : owners.keySet()) {
            if (stateFormsFor(sub).isEmpty()) {
                throw new ValidationException(
                        ErrorCode.MISSING_DEFINITION, ErrorDetails.of("field", "state_forms", "value", sub));
            }
        }

        this.backstory = Objects.requireNonNull(backstory, "backstory");
        TreeSet<String> known = new TreeSet<>(backstory.producedTags());
        for (IdeologyDef ideology : ideologyMap.values()) {
            known.addAll(ideology.tags());
            ideology.subIdeologies().forEach(sub -> known.addAll(sub.tags()));
        }
        levelMap.values().forEach(level -> known.addAll(level.tags()));
        nuclearMap.values().forEach(status -> known.addAll(status.tags()));
        for (BackstoryFragmentDef fragment : backstory.fragments().values()) {
            for (String tag : fragment.referencedTags()) {
                if (!known.contains(tag)) {
                    throw unknown("backstory." + fragment.id() + ".tags", tag);
                }
            }
        }

        this.balance = Objects.requireNonNull(balance, "balance");
    }

    /** Хеш вихідних файлів контенту: однаковий на всіх машинах для однакових файлів. */
    public String hash() {
        return hash;
    }

    public SortedMap<IdeologyId, IdeologyDef> ideologies() {
        return ideologies;
    }

    /** Ідеології в порядку контенту — порядок секторів колеса ідеології. */
    public List<IdeologyDef> ideologiesInContentOrder() {
        return ideologiesInContentOrder;
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

    /** Типи постатей у порядку enum; визначено кожен. */
    public SortedMap<PersonKind, PersonKindDef> personKinds() {
        return personKinds;
    }

    public PersonKindDef personKind(PersonKind kind) {
        return personKinds.get(Objects.requireNonNull(kind, "kind"));
    }

    public SortedMap<TraitId, TraitDef> traits() {
        return traits;
    }

    public Optional<TraitDef> trait(TraitId id) {
        return Optional.ofNullable(traits.get(id));
    }

    /** Риси, доступні постаті цього типу, за id; хоча б одна. */
    public List<TraitDef> traitsFor(PersonKind kind) {
        Objects.requireNonNull(kind, "kind");
        return traits.values().stream().filter(trait -> trait.allows(kind)).toList();
    }

    /**
     * Чи можуть дві різні риси бути в однієї постаті. Несумісність симетрична: досить, щоб її вказала одна з рис.
     *
     * @throws ValidationException з {@link ErrorCode#UNKNOWN_REFERENCE}, якщо риси немає в контенті
     */
    public boolean compatible(TraitId a, TraitId b) {
        TraitDef first = known(a);
        TraitDef second = known(b);
        return !a.equals(b)
                && !first.incompatible().contains(b)
                && !second.incompatible().contains(a);
    }

    /** Назви держав: стилі коренів, парадигми відмінювання, форми державності. */
    public NameContent names() {
        return names;
    }

    /**
     * Форми державності, доступні підкласифікації, за id; хоча б одна.
     *
     * @throws ValidationException з {@link ErrorCode#UNKNOWN_REFERENCE}, якщо підкласифікації немає в контенті
     */
    public List<StateFormDef> stateFormsFor(SubIdeologyId subIdeology) {
        IdeologyId ideology = subIdeologyOwners.get(Objects.requireNonNull(subIdeology, "subIdeology"));
        if (ideology == null) {
            throw unknown("sub_ideology", subIdeology);
        }
        return names.stateForms().values().stream()
                .filter(form -> form.appliesTo(ideology, subIdeology))
                .toList();
    }

    /** Передісторія: фрагменти й словник міток коліс генерації. */
    public BackstoryContent backstory() {
        return backstory;
    }

    /** Числа балансу: колеса, стріки, коридор сили, кількості генерації. */
    public BalanceDef balance() {
        return balance;
    }

    private static ValidationException unknown(String field, Object value) {
        return new ValidationException(ErrorCode.UNKNOWN_REFERENCE, ErrorDetails.of("field", field, "value", value));
    }

    private TraitDef known(TraitId id) {
        TraitDef trait = traits.get(Objects.requireNonNull(id, "trait"));
        if (trait == null) {
            throw new ValidationException(ErrorCode.UNKNOWN_REFERENCE, ErrorDetails.of("field", "trait", "value", id));
        }
        return trait;
    }

    private static <T> List<T> nonEmpty(String field, List<T> values) {
        if (values.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", field));
        }
        return values;
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
