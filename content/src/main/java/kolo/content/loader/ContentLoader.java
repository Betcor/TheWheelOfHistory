package kolo.content.loader;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import kolo.engine.content.ArchetypeDef;
import kolo.engine.content.ArchetypeId;
import kolo.engine.content.ArmySizeDef;
import kolo.engine.content.ArmySizeId;
import kolo.engine.content.AspectDef;
import kolo.engine.content.AspectId;
import kolo.engine.content.BackstoryContent;
import kolo.engine.content.BackstoryFragmentDef;
import kolo.engine.content.BackstoryFragmentId;
import kolo.engine.content.BackstoryText;
import kolo.engine.content.BalanceDef;
import kolo.engine.content.ContentPack;
import kolo.engine.content.CountRange;
import kolo.engine.content.DevelopmentLevelDef;
import kolo.engine.content.DoctrineDef;
import kolo.engine.content.DoctrineId;
import kolo.engine.content.DogmaDef;
import kolo.engine.content.DogmaId;
import kolo.engine.content.FaithFormDef;
import kolo.engine.content.FaithFormId;
import kolo.engine.content.GdpLevelDef;
import kolo.engine.content.GdpLevelId;
import kolo.engine.content.GenerationBalanceDef;
import kolo.engine.content.HdiLevelDef;
import kolo.engine.content.HdiLevelId;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.IdeologyId;
import kolo.engine.content.MedianRange;
import kolo.engine.content.ModifierDef;
import kolo.engine.content.NameContent;
import kolo.engine.content.NameFinalDef;
import kolo.engine.content.NameParadigmDef;
import kolo.engine.content.NameParadigmId;
import kolo.engine.content.NamePartsDef;
import kolo.engine.content.NameStyleDef;
import kolo.engine.content.NameStyleId;
import kolo.engine.content.NuclearStatusDef;
import kolo.engine.content.PersonKindDef;
import kolo.engine.content.PersonNameStyleDef;
import kolo.engine.content.PowerCorridorDef;
import kolo.engine.content.ReligionBalanceDef;
import kolo.engine.content.ReligionContent;
import kolo.engine.content.ReligionPolityDef;
import kolo.engine.content.ReligionPolityId;
import kolo.engine.content.ResourceDef;
import kolo.engine.content.ResourceId;
import kolo.engine.content.StateFormDef;
import kolo.engine.content.StateFormId;
import kolo.engine.content.StreakContent;
import kolo.engine.content.StreakKind;
import kolo.engine.content.StreakRewardDef;
import kolo.engine.content.StreakRewardId;
import kolo.engine.content.StreakRulesDef;
import kolo.engine.content.StreakWheelDef;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.content.SurnameFinalDef;
import kolo.engine.content.TagCondition;
import kolo.engine.content.TechBranchDef;
import kolo.engine.content.TrainingLevelDef;
import kolo.engine.content.TraitDef;
import kolo.engine.content.TraitId;
import kolo.engine.content.WheelBalanceDef;
import kolo.engine.error.ContentException;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.Development;
import kolo.engine.state.GrammaticalCase;
import kolo.engine.state.GrammaticalGender;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.PersonKind;
import kolo.engine.state.PowerCorridor;
import kolo.engine.state.TechBranch;
import kolo.engine.state.Training;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.WheelKind;

/**
 * Читає YAML-файли контенту й збирає з них {@link ContentPack}.
 *
 * <p>Будь-яка проблема — {@link ContentException} з ім'ям файлу й місцем у ньому: гра чи сервер із невалідним
 * контентом не стартує. Невідомі поля, повтори ключів у YAML і рядки замість чисел — теж помилки: опечатка в
 * контенті не повинна мовчки зникати.
 */
public final class ContentLoader {

    public static final String IDEOLOGIES = "ideologies.yaml";
    public static final String DOCTRINES = "doctrines.yaml";
    public static final String RESOURCES = "resources.yaml";
    public static final String DEVELOPMENT = "development.yaml";
    public static final String NUCLEAR = "nuclear.yaml";
    public static final String GDP = "gdp.yaml";
    public static final String HDI = "hdi.yaml";
    public static final String ARMY = "army.yaml";
    public static final String PEOPLE = "people.yaml";
    public static final String NAMES = "names.yaml";
    public static final String BACKSTORY = "backstory.yaml";
    public static final String STREAKS = "streaks.yaml";
    public static final String RELIGIONS = "religions.yaml";
    public static final String BALANCE = "balance.yaml";

    /** Усі файли контенту; кожен обов'язковий. */
    public static final List<String> FILES = List.of(
            IDEOLOGIES,
            DOCTRINES,
            RESOURCES,
            DEVELOPMENT,
            NUCLEAR,
            GDP,
            HDI,
            ARMY,
            PEOPLE,
            NAMES,
            BACKSTORY,
            STREAKS,
            RELIGIONS,
            BALANCE);

    private static final YAMLMapper MAPPER = createMapper();

    private ContentLoader() {}

    /** Контент, вбудований у гру. */
    public static ContentPack loadBundled() {
        return load(ContentSource.bundled());
    }

    public static ContentPack load(ContentSource source) {
        SortedMap<String, byte[]> files = new TreeMap<>();
        for (String file : FILES) {
            files.put(file, read(source, file));
        }

        List<IdeologyDef> ideologies = ideologies(parse(files, IDEOLOGIES, ContentYaml.IdeologiesFile.class));
        List<DoctrineDef> doctrines = doctrines(parse(files, DOCTRINES, ContentYaml.DoctrinesFile.class));
        List<ResourceDef> resources = resources(parse(files, RESOURCES, ContentYaml.ResourcesFile.class));
        ContentYaml.DevelopmentFile development = parse(files, DEVELOPMENT, ContentYaml.DevelopmentFile.class);
        List<TechBranchDef> branches = techBranches(development);
        List<DevelopmentLevelDef> levels = developmentLevels(development);
        List<NuclearStatusDef> nuclear = nuclearStatuses(parse(files, NUCLEAR, ContentYaml.NuclearFile.class));
        List<GdpLevelDef> gdp = gdpLevels(parse(files, GDP, ContentYaml.GdpFile.class));
        List<HdiLevelDef> hdi = hdiLevels(parse(files, HDI, ContentYaml.HdiFile.class));
        ContentYaml.ArmyFile armyFile = parse(files, ARMY, ContentYaml.ArmyFile.class);
        List<ArmySizeDef> army = armySizes(armyFile);
        List<TrainingLevelDef> training = trainingLevels(armyFile);
        ContentYaml.PeopleFile people = parse(files, PEOPLE, ContentYaml.PeopleFile.class);
        List<PersonKindDef> kinds = personKinds(people);
        List<TraitDef> traits = traits(people);
        NameContent names = names(parse(files, NAMES, ContentYaml.NamesFile.class), ideologies);
        StreakContent streaks = streaks(parse(files, STREAKS, ContentYaml.StreaksFile.class));
        BackstoryContent backstory = backstory(
                parse(files, BACKSTORY, ContentYaml.BackstoryFile.class),
                streaks,
                ideologies,
                levels,
                nuclear,
                gdp,
                hdi,
                army,
                training);
        checkPersonKindTags(
                kinds, knownTags(backstory, streaks, ideologies, levels, nuclear, gdp, hdi, army, training));
        ReligionContent religions = religions(parse(files, RELIGIONS, ContentYaml.ReligionsFile.class));
        BalanceDef balance = balance(parse(files, BALANCE, ContentYaml.BalanceFile.class));

        // Повтори, пропуски й порожні колекції вже відловлено по файлах, з місцем помилки; тут — лише збирання.
        String hash = ContentHash.of(files);
        return at(
                "*",
                "",
                () -> new ContentPack(
                        hash,
                        ideologies,
                        doctrines,
                        resources,
                        branches,
                        levels,
                        nuclear,
                        gdp,
                        hdi,
                        army,
                        training,
                        kinds,
                        traits,
                        names,
                        backstory,
                        streaks,
                        religions,
                        balance));
    }

    // ---- Файли ----

    private static byte[] read(ContentSource source, String file) {
        Optional<byte[]> content;
        try {
            content = source.read(file);
        } catch (IOException e) {
            throw new ContentException(ErrorCode.CONTENT_READ_FAILED, ErrorDetails.of("file", file), e);
        }
        return content.orElseThrow(
                () -> new ContentException(ErrorCode.CONTENT_FILE_MISSING, ErrorDetails.of("file", file)));
    }

    private static <T> T parse(SortedMap<String, byte[]> files, String file, Class<T> type) {
        try {
            T parsed = MAPPER.readValue(files.get(file), type);
            if (parsed == null) {
                // Порожній файл: нуль документів YAML.
                throw malformed(file, JsonLocation.NA, "порожній файл");
            }
            return parsed;
        } catch (JsonMappingException e) {
            // Рядок у Jackson — позиція після проблемного токена, тож шлях у файлі точніший.
            ContentException error = malformed(file, e.getLocation(), e.getOriginalMessage(), e);
            TreeMap<String, Object> details = new TreeMap<>(error.details());
            details.put("location", path(e.getPath()));
            throw new ContentException(ErrorCode.CONTENT_MALFORMED, details, e);
        } catch (JsonProcessingException e) {
            throw malformed(file, e.getLocation(), e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new ContentException(ErrorCode.CONTENT_READ_FAILED, ErrorDetails.of("file", file), e);
        }
    }

    // ---- Перетворення YAML → модель рушія ----

    private static List<IdeologyDef> ideologies(ContentYaml.IdeologiesFile yaml) {
        TreeSet<IdeologyId> ids = new TreeSet<>();
        TreeSet<SubIdeologyId> subIds = new TreeSet<>();
        return list(
                IDEOLOGIES,
                "ideologies",
                nonEmpty(IDEOLOGIES, "ideologies", yaml.ideologies()),
                (location, ideology) -> {
                    IdeologyId id = at(IDEOLOGIES, location, () -> unique(ids, new IdeologyId(ideology.id())));
                    List<ModifierDef> modifiers = modifiers(IDEOLOGIES, location, ideology.modifiers());
                    List<SubIdeologyDef> subs = list(
                            IDEOLOGIES, location + ".sub_ideologies", ideology.subIdeologies(), (subLocation, sub) -> {
                                SubIdeologyId subId =
                                        at(IDEOLOGIES, subLocation, () -> unique(subIds, new SubIdeologyId(sub.id())));
                                List<ModifierDef> subModifiers = modifiers(IDEOLOGIES, subLocation, sub.modifiers());
                                return at(
                                        IDEOLOGIES,
                                        subLocation,
                                        () -> new SubIdeologyDef(
                                                subId,
                                                sub.name(),
                                                required("weight", sub.weight()),
                                                subModifiers,
                                                sub.tags()));
                            });
                    return at(
                            IDEOLOGIES,
                            location,
                            () -> new IdeologyDef(
                                    id,
                                    ideology.name(),
                                    required("weight", ideology.weight()),
                                    modifiers,
                                    ideology.tags(),
                                    subs));
                });
    }

    private static List<DoctrineDef> doctrines(ContentYaml.DoctrinesFile yaml) {
        TreeSet<DoctrineId> ids = new TreeSet<>();
        return list(
                DOCTRINES, "doctrines", nonEmpty(DOCTRINES, "doctrines", yaml.doctrines()), (location, doctrine) -> {
                    DoctrineId id = at(DOCTRINES, location, () -> unique(ids, new DoctrineId(doctrine.id())));
                    List<ModifierDef> modifiers = modifiers(DOCTRINES, location, doctrine.modifiers());
                    return at(
                            DOCTRINES,
                            location,
                            () -> new DoctrineDef(id, doctrine.name(), modifiers, doctrine.tags()));
                });
    }

    private static List<ResourceDef> resources(ContentYaml.ResourcesFile yaml) {
        TreeSet<ResourceId> ids = new TreeSet<>();
        return list(
                RESOURCES, "resources", nonEmpty(RESOURCES, "resources", yaml.resources()), (location, resource) -> {
                    ResourceId id = at(RESOURCES, location, () -> unique(ids, new ResourceId(resource.id())));
                    return at(RESOURCES, location, () -> new ResourceDef(id, resource.name(), resource.tags()));
                });
    }

    private static List<TechBranchDef> techBranches(ContentYaml.DevelopmentFile yaml) {
        TreeSet<TechBranch> seen = new TreeSet<>();
        List<TechBranchDef> branches = list(DEVELOPMENT, "branches", yaml.branches(), (location, branch) -> {
            TechBranch key = at(
                    DEVELOPMENT,
                    location,
                    () -> unique(
                            seen,
                            ContentKeys.parse("tech_branch", TechBranch.values(), TechBranch::key, branch.id()),
                            TechBranch::key));
            return at(DEVELOPMENT, location, () -> new TechBranchDef(key, branch.name()));
        });
        complete(DEVELOPMENT, "branches", seen, List.of(TechBranch.values()), TechBranch::key);
        return branches;
    }

    private static List<DevelopmentLevelDef> developmentLevels(ContentYaml.DevelopmentFile yaml) {
        TreeSet<Integer> seen = new TreeSet<>();
        List<DevelopmentLevelDef> levels = list(DEVELOPMENT, "levels", yaml.levels(), (location, level) -> {
            DevelopmentLevelDef def = at(
                    DEVELOPMENT,
                    location,
                    () -> new DevelopmentLevelDef(
                            level.level(),
                            level.name(),
                            level.description(),
                            required("weight", level.weight()),
                            required("quality", level.quality()),
                            level.tags()));
            at(DEVELOPMENT, location, () -> unique(seen, def.level(), value -> value));
            return def;
        });
        complete(DEVELOPMENT, "levels", seen, Development.levels(), value -> value);
        return levels;
    }

    private static List<NuclearStatusDef> nuclearStatuses(ContentYaml.NuclearFile yaml) {
        TreeSet<NuclearStatus> seen = new TreeSet<>();
        List<NuclearStatusDef> statuses = list(NUCLEAR, "statuses", yaml.statuses(), (location, status) -> {
            NuclearStatus key = at(
                    NUCLEAR,
                    location,
                    () -> unique(
                            seen,
                            ContentKeys.parse(
                                    "nuclear_status", NuclearStatus.values(), NuclearStatus::key, status.id()),
                            NuclearStatus::key));
            return at(
                    NUCLEAR,
                    location,
                    () -> new NuclearStatusDef(
                            key,
                            status.name(),
                            required("weight", status.weight()),
                            required("quality", status.quality()),
                            status.tags()));
        });
        complete(NUCLEAR, "statuses", seen, List.of(NuclearStatus.values()), NuclearStatus::key);
        return statuses;
    }

    private static List<GdpLevelDef> gdpLevels(ContentYaml.GdpFile yaml) {
        TreeSet<GdpLevelId> ids = new TreeSet<>();
        List<GdpLevelDef> levels = list(GDP, "levels", nonEmpty(GDP, "levels", yaml.levels()), (location, level) -> {
            GdpLevelId id = at(GDP, location, () -> unique(ids, new GdpLevelId(level.id())));
            return at(
                    GDP,
                    location,
                    () -> new GdpLevelDef(
                            id,
                            level.name(),
                            level.description(),
                            required("per_capita", level.perCapita()),
                            ContentKeys.parse("tier", OutcomeTier.values(), OutcomeTier::key, level.tier()),
                            required("weight", level.weight()),
                            required("quality", level.quality()),
                            level.tags()));
        });
        // Порядок перевіряється тут, щоб помилка вказувала на рівень, що стоїть не на своєму місці.
        for (int i = 1; i < levels.size(); i++) {
            GdpLevelDef previous = levels.get(i - 1);
            GdpLevelDef next = levels.get(i);
            at(GDP, "levels[" + i + "]", () -> {
                GdpLevelDef.checkFollows(previous, next);
                return next;
            });
        }
        return levels;
    }

    private static List<HdiLevelDef> hdiLevels(ContentYaml.HdiFile yaml) {
        TreeSet<HdiLevelId> ids = new TreeSet<>();
        List<HdiLevelDef> levels = list(HDI, "levels", nonEmpty(HDI, "levels", yaml.levels()), (location, level) -> {
            HdiLevelId id = at(HDI, location, () -> unique(ids, new HdiLevelId(level.id())));
            return at(
                    HDI,
                    location,
                    () -> new HdiLevelDef(
                            id,
                            level.name(),
                            level.description(),
                            required("hdi", level.hdi()),
                            ContentKeys.parse("tier", OutcomeTier.values(), OutcomeTier::key, level.tier()),
                            required("weight", level.weight()),
                            required("quality", level.quality()),
                            level.tags()));
        });
        // Порядок перевіряється тут, щоб помилка вказувала на рівень, що стоїть не на своєму місці.
        for (int i = 1; i < levels.size(); i++) {
            HdiLevelDef previous = levels.get(i - 1);
            HdiLevelDef next = levels.get(i);
            at(HDI, "levels[" + i + "]", () -> {
                HdiLevelDef.checkFollows(previous, next);
                return next;
            });
        }
        return levels;
    }

    private static List<ArmySizeDef> armySizes(ContentYaml.ArmyFile yaml) {
        TreeSet<ArmySizeId> ids = new TreeSet<>();
        List<ArmySizeDef> sizes = list(ARMY, "sizes", nonEmpty(ARMY, "sizes", yaml.sizes()), (location, size) -> {
            ArmySizeId id = at(ARMY, location, () -> unique(ids, new ArmySizeId(size.id())));
            return at(
                    ARMY,
                    location,
                    () -> new ArmySizeDef(
                            id,
                            size.name(),
                            size.description(),
                            required("share_bp", size.shareBp()),
                            ContentKeys.parse("tier", OutcomeTier.values(), OutcomeTier::key, size.tier()),
                            required("weight", size.weight()),
                            required("quality", size.quality()),
                            size.tags()));
        });
        // Порядок перевіряється тут, щоб помилка вказувала на рівень, що стоїть не на своєму місці.
        for (int i = 1; i < sizes.size(); i++) {
            ArmySizeDef previous = sizes.get(i - 1);
            ArmySizeDef next = sizes.get(i);
            at(ARMY, "sizes[" + i + "]", () -> {
                ArmySizeDef.checkFollows(previous, next);
                return next;
            });
        }
        return sizes;
    }

    private static List<TrainingLevelDef> trainingLevels(ContentYaml.ArmyFile yaml) {
        TreeSet<Integer> seen = new TreeSet<>();
        List<TrainingLevelDef> levels = list(ARMY, "training", yaml.training(), (location, level) -> {
            TrainingLevelDef def = at(
                    ARMY,
                    location,
                    () -> new TrainingLevelDef(
                            required("level", level.level()),
                            level.name(),
                            level.description(),
                            required("combat_modifier", level.combatModifier()),
                            ContentKeys.parse("tier", OutcomeTier.values(), OutcomeTier::key, level.tier()),
                            required("weight", level.weight()),
                            required("quality", level.quality()),
                            level.tags()));
            at(ARMY, location, () -> unique(seen, def.level(), value -> value));
            return def;
        });
        // Порядок перевіряється тут, щоб помилка вказувала на рівень, що стоїть не на своєму місці.
        for (int i = 1; i < levels.size(); i++) {
            TrainingLevelDef previous = levels.get(i - 1);
            TrainingLevelDef next = levels.get(i);
            at(ARMY, "training[" + i + "]", () -> {
                TrainingLevelDef.checkFollows(previous, next);
                return next;
            });
        }
        complete(ARMY, "training", seen, Training.levels(), value -> value);
        return levels;
    }

    private static List<PersonKindDef> personKinds(ContentYaml.PeopleFile yaml) {
        TreeSet<PersonKind> seen = new TreeSet<>();
        List<PersonKindDef> kinds = list(PEOPLE, "kinds", yaml.kinds(), (location, kind) -> {
            PersonKind key =
                    at(PEOPLE, location, () -> unique(seen, personKind("person_kind", kind.id()), PersonKind::key));
            return at(
                    PEOPLE,
                    location,
                    () -> new PersonKindDef(
                            key,
                            kind.name(),
                            kind.description(),
                            required("weight", kind.weight()),
                            new TreeMap<>(kind.weightTags()),
                            kind.tags()));
        });
        complete(PEOPLE, "kinds", seen, List.of(PersonKind.values()), PersonKind::key);
        return kinds;
    }

    private static List<TraitDef> traits(ContentYaml.PeopleFile yaml) {
        TreeSet<TraitId> ids = new TreeSet<>();
        List<TraitDef> traits = list(PEOPLE, "traits", nonEmpty(PEOPLE, "traits", yaml.traits()), (location, trait) -> {
            TraitId id = at(PEOPLE, location, () -> unique(ids, new TraitId(trait.id())));
            List<PersonKind> kinds = list(
                    PEOPLE,
                    location + ".kinds",
                    trait.kinds(),
                    (kindLocation, kind) -> at(PEOPLE, kindLocation, () -> personKind("trait.kinds", kind)));
            List<TraitId> incompatible = list(
                    PEOPLE,
                    location + ".incompatible",
                    trait.incompatible(),
                    (otherLocation, other) -> at(PEOPLE, otherLocation, () -> new TraitId(other)));
            List<ModifierDef> modifiers = modifiers(PEOPLE, location, trait.modifiers());
            return at(
                    PEOPLE,
                    location,
                    () -> new TraitDef(id, trait.name(), kinds, modifiers, trait.tags(), incompatible));
        });
        // Посилання між рисами — в межах файлу, тож місце помилки відоме точно.
        for (int i = 0; i < traits.size(); i++) {
            List<TraitId> incompatible = traits.get(i).incompatible();
            for (int j = 0; j < incompatible.size(); j++) {
                if (!ids.contains(incompatible.get(j))) {
                    throw invalid(
                            PEOPLE,
                            "traits[" + i + "].incompatible[" + j + "]",
                            new ValidationException(
                                    ErrorCode.UNKNOWN_REFERENCE,
                                    ErrorDetails.of("field", "trait", "value", incompatible.get(j))));
                }
            }
        }
        for (PersonKind kind : PersonKind.values()) {
            if (traits.stream().noneMatch(trait -> trait.allows(kind))) {
                throw invalid(
                        PEOPLE,
                        "traits",
                        new ValidationException(
                                ErrorCode.MISSING_DEFINITION, ErrorDetails.of("field", "traits", "value", kind.key())));
            }
        }
        return traits;
    }

    /** @param ideologies уже завантажені ідеології: форми державності посилаються на них */
    private static NameContent names(ContentYaml.NamesFile yaml, List<IdeologyDef> ideologies) {
        TreeSet<NameParadigmId> paradigmIds = new TreeSet<>();
        List<NameParadigmDef> paradigms =
                list(NAMES, "paradigms", nonEmpty(NAMES, "paradigms", yaml.paradigms()), (location, paradigm) -> {
                    NameParadigmId id =
                            at(NAMES, location, () -> unique(paradigmIds, new NameParadigmId(paradigm.id())));
                    return at(
                            NAMES,
                            location,
                            () -> new NameParadigmDef(
                                    id, gender(paradigm.gender()), cases("endings", paradigm.endings())));
                });

        TreeSet<NameStyleId> styleIds = new TreeSet<>();
        List<NameStyleDef> styles =
                list(NAMES, "styles", nonEmpty(NAMES, "styles", yaml.styles()), (location, style) -> {
                    NameStyleId id = at(NAMES, location, () -> unique(styleIds, new NameStyleId(style.id())));
                    List<NameFinalDef> finals =
                            list(NAMES, location + ".finals", style.finals(), (finalLocation, nameFinal) -> {
                                NameParadigmId paradigm =
                                        at(NAMES, finalLocation, () -> new NameParadigmId(nameFinal.paradigm()));
                                // Посилання в межах файлу: місце помилки відоме точно.
                                if (!paradigmIds.contains(paradigm)) {
                                    throw invalid(NAMES, finalLocation + ".paradigm", unknown("paradigm", paradigm));
                                }
                                return at(NAMES, finalLocation, () -> new NameFinalDef(nameFinal.text(), paradigm));
                            });
                    return at(
                            NAMES,
                            location,
                            () -> new NameStyleDef(
                                    id, style.name(), style.starts(), style.middles(), style.middleChanceBp(), finals));
                });

        TreeSet<IdeologyId> knownIdeologies = new TreeSet<>();
        TreeSet<SubIdeologyId> knownSubs = new TreeSet<>();
        for (IdeologyDef ideology : ideologies) {
            knownIdeologies.add(ideology.id());
            ideology.subIdeologies().forEach(sub -> knownSubs.add(sub.id()));
        }
        TreeSet<StateFormId> formIds = new TreeSet<>();
        List<StateFormDef> forms =
                list(NAMES, "state_forms", nonEmpty(NAMES, "state_forms", yaml.stateForms()), (location, form) -> {
                    StateFormId id = at(NAMES, location, () -> unique(formIds, new StateFormId(form.id())));
                    List<IdeologyId> formIdeologies =
                            list(NAMES, location + ".ideologies", form.ideologies(), (itemLocation, value) -> {
                                IdeologyId ideology = at(NAMES, itemLocation, () -> new IdeologyId(value));
                                if (!knownIdeologies.contains(ideology)) {
                                    throw invalid(NAMES, itemLocation, unknown("ideology", ideology));
                                }
                                return ideology;
                            });
                    List<SubIdeologyId> formSubs =
                            list(NAMES, location + ".sub_ideologies", form.subIdeologies(), (itemLocation, value) -> {
                                SubIdeologyId sub = at(NAMES, itemLocation, () -> new SubIdeologyId(value));
                                if (!knownSubs.contains(sub)) {
                                    throw invalid(NAMES, itemLocation, unknown("sub_ideology", sub));
                                }
                                return sub;
                            });
                    return at(
                            NAMES,
                            location,
                            () -> new StateFormDef(
                                    id, gender(form.gender()), cases("forms", form.forms()), formIdeologies, formSubs));
                });
        // Кожній підкласифікації — хоча б одна форма; перевіряється тут, щоб помилка вказувала на names.yaml.
        for (IdeologyDef ideology : ideologies) {
            for (SubIdeologyDef sub : ideology.subIdeologies()) {
                if (forms.stream().noneMatch(form -> form.appliesTo(ideology.id(), sub.id()))) {
                    throw invalid(
                            NAMES,
                            "state_forms",
                            new ValidationException(
                                    ErrorCode.MISSING_DEFINITION,
                                    ErrorDetails.of("field", "state_forms", "value", sub.id())));
                }
            }
        }
        List<PersonNameStyleDef> personStyles = personStyles(yaml, paradigms, styleIds);
        return at(NAMES, "", () -> new NameContent(paradigms, styles, forms, personStyles));
    }

    /**
     * @param paradigms уже прочитані парадигми з того самого файлу
     * @param styleIds id стилів назв держав: кожному відповідає рівно один стиль імен
     */
    private static List<PersonNameStyleDef> personStyles(
            ContentYaml.NamesFile yaml, List<NameParadigmDef> paradigms, TreeSet<NameStyleId> styleIds) {
        TreeMap<NameParadigmId, GrammaticalGender> genders = new TreeMap<>();
        paradigms.forEach(paradigm -> genders.put(paradigm.id(), paradigm.gender()));
        // Посилання в межах файлу: місце помилки відоме точно.
        ParadigmRef ref = (location, value, expected) -> {
            NameParadigmId id = at(NAMES, location, () -> new NameParadigmId(value));
            GrammaticalGender gender = genders.get(id);
            if (gender == null) {
                throw invalid(NAMES, location, unknown("paradigm", id));
            }
            if (gender != expected) {
                throw invalid(
                        NAMES,
                        location,
                        new ValidationException(
                                ErrorCode.NAME_GENDER_MISMATCH,
                                ErrorDetails.of("field", "paradigm", "value", id, "expected", expected.key())));
            }
            return id;
        };

        TreeSet<NameStyleId> ids = new TreeSet<>();
        List<PersonNameStyleDef> styles = list(
                NAMES, "person_styles", nonEmpty(NAMES, "person_styles", yaml.personStyles()), (location, style) -> {
                    NameStyleId id = at(NAMES, location, () -> unique(ids, new NameStyleId(style.id())));
                    if (!styleIds.contains(id)) {
                        throw invalid(NAMES, location + ".id", unknown("name_style", id));
                    }
                    ContentYaml.GivenNames given = required(NAMES, location, "given_names", style.givenNames());
                    ContentYaml.Surnames surnames = required(NAMES, location, "surnames", style.surnames());
                    String givenLocation = location + ".given_names";
                    NamePartsDef givenParts = at(
                            NAMES,
                            givenLocation,
                            () -> new NamePartsDef(given.starts(), given.middles(), given.middleChanceBp()));
                    List<NameFinalDef> male =
                            list(NAMES, givenLocation + ".male", given.male(), (finalLocation, nameFinal) -> {
                                NameParadigmId paradigm = ref.resolve(
                                        finalLocation + ".paradigm", nameFinal.paradigm(), GrammaticalGender.MASCULINE);
                                return at(NAMES, finalLocation, () -> new NameFinalDef(nameFinal.text(), paradigm));
                            });
                    List<NameFinalDef> female =
                            list(NAMES, givenLocation + ".female", given.female(), (finalLocation, nameFinal) -> {
                                NameParadigmId paradigm = ref.resolve(
                                        finalLocation + ".paradigm", nameFinal.paradigm(), GrammaticalGender.FEMININE);
                                return at(NAMES, finalLocation, () -> new NameFinalDef(nameFinal.text(), paradigm));
                            });
                    String surnameLocation = location + ".surnames";
                    NamePartsDef surnameParts = at(
                            NAMES,
                            surnameLocation,
                            () -> new NamePartsDef(surnames.starts(), surnames.middles(), surnames.middleChanceBp()));
                    List<SurnameFinalDef> surnameFinals = list(
                            NAMES, surnameLocation + ".finals", surnames.finals(), (finalLocation, surnameFinal) -> {
                                NameParadigmId maleParadigm = ref.resolve(
                                        finalLocation + ".male", surnameFinal.male(), GrammaticalGender.MASCULINE);
                                NameParadigmId femaleParadigm = ref.resolve(
                                        finalLocation + ".female", surnameFinal.female(), GrammaticalGender.FEMININE);
                                return at(
                                        NAMES,
                                        finalLocation,
                                        () -> new SurnameFinalDef(surnameFinal.text(), maleParadigm, femaleParadigm));
                            });
                    return at(
                            NAMES,
                            location,
                            () -> new PersonNameStyleDef(id, givenParts, male, female, surnameParts, surnameFinals));
                });
        for (NameStyleId style : styleIds) {
            if (!ids.contains(style)) {
                throw invalid(
                        NAMES,
                        "person_styles",
                        new ValidationException(
                                ErrorCode.MISSING_DEFINITION,
                                ErrorDetails.of("field", "person_styles", "value", style)));
            }
        }
        return styles;
    }

    @FunctionalInterface
    private interface ParadigmRef {
        NameParadigmId resolve(String location, String value, GrammaticalGender expected);
    }

    /** Обов'язковий вкладений блок: без нього — {@link ErrorCode#BLANK_VALUE} з місцем блоку. */
    private static <T> T required(String file, String owner, String field, T value) {
        if (value == null) {
            throw invalid(
                    file,
                    owner + "." + field,
                    new ValidationException(ErrorCode.BLANK_VALUE, ErrorDetails.of("field", field)));
        }
        return value;
    }

    /**
     * @param streaks уже завантажені колеса стріків, ідеології, рівні розвиненості, ядерні статуси, рівні ВВП, ІЛР,
     *     розміру й вишколу армії: їхні мітки — джерела міток в умовах фрагментів
     */
    private static BackstoryContent backstory(
            ContentYaml.BackstoryFile yaml,
            StreakContent streaks,
            List<IdeologyDef> ideologies,
            List<DevelopmentLevelDef> levels,
            List<NuclearStatusDef> nuclear,
            List<GdpLevelDef> gdp,
            List<HdiLevelDef> hdi,
            List<ArmySizeDef> army,
            List<TrainingLevelDef> training) {
        TreeSet<BackstoryFragmentId> ids = new TreeSet<>();
        List<BackstoryFragmentDef> fragments = list(
                BACKSTORY, "fragments", nonEmpty(BACKSTORY, "fragments", yaml.fragments()), (location, fragment) -> {
                    BackstoryFragmentId id =
                            at(BACKSTORY, location, () -> unique(ids, new BackstoryFragmentId(fragment.id())));
                    List<ModifierDef> modifiers = modifiers(BACKSTORY, location, fragment.modifiers());
                    return at(BACKSTORY, location, () -> fragment(id, fragment, modifiers));
                });
        BackstoryContent content = at(BACKSTORY, "", () -> new BackstoryContent(yaml.generationTags(), fragments));

        // Мітки в умовах мають джерело; перевіряється тут, щоб помилка вказувала на фрагмент.
        TreeSet<String> known = knownTags(content, streaks, ideologies, levels, nuclear, gdp, hdi, army, training);
        for (int i = 0; i < fragments.size(); i++) {
            for (String tag : fragments.get(i).referencedTags()) {
                if (!known.contains(tag)) {
                    throw invalid(BACKSTORY, "fragments[" + i + "]", unknown("tag", tag));
                }
            }
        }
        return content;
    }

    /**
     * Мітки, які може мати держава після генерації: з ладу, рівнів коліс генерації, фрагментів передісторії, коліс
     * стріків і словника міток коліс генерації.
     */
    private static TreeSet<String> knownTags(
            BackstoryContent backstory,
            StreakContent streaks,
            List<IdeologyDef> ideologies,
            List<DevelopmentLevelDef> levels,
            List<NuclearStatusDef> nuclear,
            List<GdpLevelDef> gdp,
            List<HdiLevelDef> hdi,
            List<ArmySizeDef> army,
            List<TrainingLevelDef> training) {
        TreeSet<String> known = new TreeSet<>(backstory.producedTags());
        known.addAll(streaks.producedTags());
        for (IdeologyDef ideology : ideologies) {
            known.addAll(ideology.tags());
            ideology.subIdeologies().forEach(sub -> known.addAll(sub.tags()));
        }
        levels.forEach(level -> known.addAll(level.tags()));
        nuclear.forEach(status -> known.addAll(status.tags()));
        gdp.forEach(level -> known.addAll(level.tags()));
        hdi.forEach(level -> known.addAll(level.tags()));
        army.forEach(size -> known.addAll(size.tags()));
        training.forEach(level -> known.addAll(level.tags()));
        return known;
    }

    /** Добавки до ваги типів постатей залежать лише від міток, які держава може мати. */
    private static void checkPersonKindTags(List<PersonKindDef> kinds, Set<String> known) {
        for (int i = 0; i < kinds.size(); i++) {
            for (String tag : kinds.get(i).weightTags().keySet()) {
                if (!known.contains(tag)) {
                    throw invalid(PEOPLE, "kinds[" + i + "].weight_tags", unknown("tag", tag));
                }
            }
        }
    }

    private static StreakContent streaks(ContentYaml.StreaksFile yaml) {
        TreeSet<StreakKind> seen = new TreeSet<>();
        List<StreakWheelDef> wheels = list(STREAKS, "wheels", yaml.wheels(), (location, wheel) -> {
            StreakKind kind = at(
                    STREAKS,
                    location,
                    () -> unique(
                            seen,
                            ContentKeys.parse("streak", StreakKind.values(), StreakKind::key, wheel.id()),
                            StreakKind::key));
            TreeSet<StreakRewardId> ids = new TreeSet<>();
            List<StreakRewardDef> rewards = list(
                    STREAKS,
                    location + ".rewards",
                    nonEmpty(STREAKS, location + ".rewards", wheel.rewards()),
                    (rewardLocation, reward) -> {
                        StreakRewardId id =
                                at(STREAKS, rewardLocation, () -> unique(ids, new StreakRewardId(reward.id())));
                        List<ModifierDef> modifiers = modifiers(STREAKS, rewardLocation, reward.modifiers());
                        return at(
                                STREAKS,
                                rewardLocation,
                                () -> new StreakRewardDef(
                                        id,
                                        reward.name(),
                                        reward.description(),
                                        required("weight", reward.weight()),
                                        reward.duration(),
                                        modifiers,
                                        reward.fateTokens(),
                                        reward.extraPeople(),
                                        reward.tags()));
                    });
            return at(
                    STREAKS,
                    location,
                    () -> new StreakWheelDef(kind, wheel.name(), wheel.description(), wheel.tags(), rewards));
        });
        complete(STREAKS, "wheels", seen, List.of(StreakKind.values()), StreakKind::key);
        return at(STREAKS, "", () -> new StreakContent(wheels));
    }

    private static ReligionContent religions(ContentYaml.ReligionsFile yaml) {
        TreeSet<ArchetypeId> archetypeIds = new TreeSet<>();
        List<ArchetypeDef> archetypes = list(
                RELIGIONS,
                "archetypes",
                nonEmpty(RELIGIONS, "archetypes", yaml.archetypes()),
                (location, archetype) -> {
                    ArchetypeId id =
                            at(RELIGIONS, location, () -> unique(archetypeIds, new ArchetypeId(archetype.id())));
                    return at(
                            RELIGIONS,
                            location,
                            () -> new ArchetypeDef(
                                    id,
                                    archetype.name(),
                                    archetype.description(),
                                    archetype.figure(),
                                    required("weight", archetype.weight()),
                                    archetype.tags()));
                });

        TreeSet<AspectId> aspectIds = new TreeSet<>();
        List<AspectDef> aspects =
                list(RELIGIONS, "aspects", nonEmpty(RELIGIONS, "aspects", yaml.aspects()), (location, aspect) -> {
                    AspectId id = at(RELIGIONS, location, () -> unique(aspectIds, new AspectId(aspect.id())));
                    return at(
                            RELIGIONS,
                            location,
                            () -> new AspectDef(
                                    id,
                                    aspect.name(),
                                    aspect.description(),
                                    required("weight", aspect.weight()),
                                    new TreeMap<>(aspect.weightTags()),
                                    aspect.tags()));
                });

        TreeSet<DogmaId> dogmaIds = new TreeSet<>();
        List<DogmaDef> dogmas =
                list(RELIGIONS, "dogmas", nonEmpty(RELIGIONS, "dogmas", yaml.dogmas()), (location, dogma) -> {
                    DogmaId id = at(RELIGIONS, location, () -> unique(dogmaIds, new DogmaId(dogma.id())));
                    List<DogmaId> incompatible = list(
                            RELIGIONS,
                            location + ".incompatible",
                            dogma.incompatible(),
                            (otherLocation, other) -> at(RELIGIONS, otherLocation, () -> new DogmaId(other)));
                    List<ModifierDef> modifiers = modifiers(RELIGIONS, location, dogma.modifiers());
                    return at(
                            RELIGIONS,
                            location,
                            () -> new DogmaDef(
                                    id,
                                    dogma.name(),
                                    dogma.description(),
                                    required("weight", dogma.weight()),
                                    new TreeMap<>(dogma.weightTags()),
                                    modifiers,
                                    dogma.tags(),
                                    incompatible));
                });
        // Посилання між догматами — в межах файлу, тож місце помилки відоме точно.
        for (int i = 0; i < dogmas.size(); i++) {
            List<DogmaId> incompatible = dogmas.get(i).incompatible();
            for (int j = 0; j < incompatible.size(); j++) {
                if (!dogmaIds.contains(incompatible.get(j))) {
                    throw invalid(
                            RELIGIONS,
                            "dogmas[" + i + "].incompatible[" + j + "]",
                            unknown("dogma", incompatible.get(j)));
                }
            }
        }

        TreeSet<ReligionPolityId> polityIds = new TreeSet<>();
        List<ReligionPolityDef> polities =
                list(RELIGIONS, "polities", nonEmpty(RELIGIONS, "polities", yaml.polities()), (location, polity) -> {
                    ReligionPolityId id =
                            at(RELIGIONS, location, () -> unique(polityIds, new ReligionPolityId(polity.id())));
                    List<ModifierDef> modifiers = modifiers(RELIGIONS, location, polity.modifiers());
                    return at(
                            RELIGIONS,
                            location,
                            () -> new ReligionPolityDef(
                                    id,
                                    polity.name(),
                                    polity.description(),
                                    required("weight", polity.weight()),
                                    new TreeMap<>(polity.weightTags()),
                                    modifiers,
                                    polity.tags()));
                });

        TreeSet<FaithFormId> formIds = new TreeSet<>();
        List<FaithFormDef> forms = list(
                RELIGIONS, "faith_forms", nonEmpty(RELIGIONS, "faith_forms", yaml.faithForms()), (location, form) -> {
                    FaithFormId id = at(RELIGIONS, location, () -> unique(formIds, new FaithFormId(form.id())));
                    List<ArchetypeId> formArchetypes =
                            list(RELIGIONS, location + ".archetypes", form.archetypes(), (itemLocation, value) -> {
                                ArchetypeId archetype = at(RELIGIONS, itemLocation, () -> new ArchetypeId(value));
                                if (!archetypeIds.contains(archetype)) {
                                    throw invalid(RELIGIONS, itemLocation, unknown("archetype", archetype));
                                }
                                return archetype;
                            });
                    return at(
                            RELIGIONS,
                            location,
                            () -> new FaithFormDef(
                                    id,
                                    gender(form.gender()),
                                    cases("forms", form.forms()),
                                    ContentKeys.parse(
                                            "figure_case",
                                            GrammaticalCase.values(),
                                            GrammaticalCase::key,
                                            form.figureCase()),
                                    formArchetypes));
                });
        // Кожному архетипу — хоча б одна форма; перевіряється тут, щоб помилка вказувала на список форм.
        for (ArchetypeDef archetype : archetypes) {
            if (forms.stream().noneMatch(form -> form.appliesTo(archetype.id()))) {
                throw invalid(
                        RELIGIONS,
                        "faith_forms",
                        new ValidationException(
                                ErrorCode.MISSING_DEFINITION,
                                ErrorDetails.of("field", "faith_forms", "value", archetype.id())));
            }
        }

        // Добавки до ваги — лише за мітки коліс, що крутяться раніше або те саме: архетип → аспекти → догмати → устрій.
        TreeSet<String> known = new TreeSet<>();
        archetypes.forEach(archetype -> known.addAll(archetype.tags()));
        aspects.forEach(aspect -> known.addAll(aspect.tags()));
        checkWeightTags("aspects", aspects, AspectDef::weightTags, known);
        dogmas.forEach(dogma -> known.addAll(dogma.tags()));
        checkWeightTags("dogmas", dogmas, DogmaDef::weightTags, known);
        polities.forEach(polity -> known.addAll(polity.tags()));
        checkWeightTags("polities", polities, ReligionPolityDef::weightTags, known);

        return at(RELIGIONS, "", () -> new ReligionContent(archetypes, aspects, dogmas, polities, forms));
    }

    private static <T> void checkWeightTags(
            String field, List<T> defs, Function<T, SortedMap<String, Integer>> weightTags, Set<String> known) {
        for (int i = 0; i < defs.size(); i++) {
            for (String tag : weightTags.apply(defs.get(i)).keySet()) {
                if (!known.contains(tag)) {
                    throw invalid(RELIGIONS, field + "[" + i + "].weight_tags", unknown("tag", tag));
                }
            }
        }
    }

    private static BackstoryFragmentDef fragment(
            BackstoryFragmentId id, ContentYaml.Fragment fragment, List<ModifierDef> modifiers) {
        if (fragment.quality() == null) {
            throw new ValidationException(ErrorCode.BLANK_VALUE, ErrorDetails.of("field", "quality"));
        }
        if (fragment.years() == null) {
            throw new ValidationException(ErrorCode.BLANK_VALUE, ErrorDetails.of("field", "years"));
        }
        String field = "backstory." + id;
        return new BackstoryFragmentDef(
                id,
                fragment.weight(),
                fragment.quality(),
                fragment.years().from(),
                fragment.years().to(),
                new TagCondition(fragment.requires(), fragment.requiresAny(), fragment.excludes()),
                new TreeMap<>(fragment.weightTags()),
                fragment.neighbor(),
                fragment.adds(),
                fragment.duration(),
                modifiers,
                new BackstoryText(field + ".text", fragment.text()));
    }

    private static BalanceDef balance(ContentYaml.BalanceFile yaml) {
        ContentYaml.WheelBalance wheelYaml = section("wheel", yaml.wheel());
        TreeMap<WheelKind, Integer> strengths = new TreeMap<>();
        wheelYaml.strength().forEach((kind, strength) -> {
            String location = "wheel.strength." + kind;
            strengths.put(
                    at(BALANCE, location, () -> new WheelKind(kind)),
                    at(BALANCE, location, () -> required(location, strength)));
        });
        WheelBalanceDef wheel = at(
                BALANCE,
                "wheel",
                () -> new WheelBalanceDef(
                        required("wheel.default_strength", wheelYaml.defaultStrength()),
                        strengths,
                        wheelYaml.investmentCurve()));

        ContentYaml.Streaks streaksYaml = section("streaks", yaml.streaks());
        StreakRulesDef streaks = at(
                BALANCE,
                "streaks",
                () -> new StreakRulesDef(
                        required("streaks.very_good_quality", streaksYaml.veryGoodQuality()),
                        required("streaks.very_bad_quality", streaksYaml.veryBadQuality()),
                        required("streaks.length", streaksYaml.length())));

        TreeSet<PowerCorridor> seen = new TreeSet<>();
        List<PowerCorridorDef> corridors =
                list(BALANCE, "power_corridors", yaml.powerCorridors(), (location, corridor) -> {
                    PowerCorridor key = at(
                            BALANCE,
                            location,
                            () -> unique(
                                    seen,
                                    ContentKeys.parse(
                                            "power_corridor",
                                            PowerCorridor.values(),
                                            PowerCorridor::key,
                                            corridor.id()),
                                    PowerCorridor::key));
                    MedianRange players = at(BALANCE, location + ".players", () -> median(corridor.players()));
                    MedianRange npc = at(BALANCE, location + ".npc", () -> median(corridor.npc()));
                    return at(BALANCE, location, () -> new PowerCorridorDef(key, players, npc));
                });
        complete(BALANCE, "power_corridors", seen, List.of(PowerCorridor.values()), PowerCorridor::key);

        ContentYaml.Generation generationYaml = section("generation", yaml.generation());
        CountRange fragments =
                at(BALANCE, "generation.backstory_fragments", () -> count(generationYaml.backstoryFragments()));
        CountRange people = at(BALANCE, "generation.notable_people", () -> count(generationYaml.notablePeople()));
        CountRange warheads = at(BALANCE, "generation.warheads", () -> count(generationYaml.warheads()));
        int energyAdvantage = at(
                BALANCE,
                "generation.nuclear_energy_advantage",
                () -> required("nuclear_energy_advantage", generationYaml.nuclearEnergyAdvantage()));
        int gdpAdvantage = at(
                BALANCE,
                "generation.gdp_development_advantage",
                () -> required("gdp_development_advantage", generationYaml.gdpDevelopmentAdvantage()));
        int hdiAdvantage = at(
                BALANCE,
                "generation.hdi_gdp_advantage",
                () -> required("hdi_gdp_advantage", generationYaml.hdiGdpAdvantage()));
        int armySizeAdvantage = at(
                BALANCE,
                "generation.army_size_gdp_advantage",
                () -> required("army_size_gdp_advantage", generationYaml.armySizeGdpAdvantage()));
        int trainingGdpAdvantage = at(
                BALANCE,
                "generation.army_training_gdp_advantage",
                () -> required("army_training_gdp_advantage", generationYaml.armyTrainingGdpAdvantage()));
        int trainingDevelopmentAdvantage = at(
                BALANCE,
                "generation.army_training_development_advantage",
                () -> required(
                        "army_training_development_advantage", generationYaml.armyTrainingDevelopmentAdvantage()));
        CountRange traitCount = at(BALANCE, "generation.person_traits", () -> count(generationYaml.personTraits()));
        CountRange age = at(BALANCE, "generation.person_age", () -> count(generationYaml.personAge()));
        int nameCandidates = at(
                BALANCE,
                "generation.name_candidates",
                () -> required("name_candidates", generationYaml.nameCandidates()));
        GenerationBalanceDef generation = at(
                BALANCE,
                "generation",
                () -> new GenerationBalanceDef(
                        fragments,
                        people,
                        warheads,
                        energyAdvantage,
                        gdpAdvantage,
                        hdiAdvantage,
                        armySizeAdvantage,
                        trainingGdpAdvantage,
                        trainingDevelopmentAdvantage,
                        traitCount,
                        age,
                        nameCandidates));

        ContentYaml.Religion religionYaml = section("religion", yaml.religion());
        CountRange aspects = at(BALANCE, "religion.aspects", () -> count(religionYaml.aspects()));
        CountRange dogmas = at(BALANCE, "religion.dogmas", () -> count(religionYaml.dogmas()));
        ReligionBalanceDef religion = at(BALANCE, "religion", () -> new ReligionBalanceDef(aspects, dogmas));

        return at(BALANCE, "", () -> BalanceDef.of(wheel, streaks, corridors, generation, religion));
    }

    /** Розділ файлу балансу; пропущений — помилка з назвою розділу як місцем. */
    private static <T> T section(String name, T value) {
        return at(BALANCE, name, () -> {
            if (value == null) {
                throw new ValidationException(ErrorCode.BLANK_VALUE, ErrorDetails.of("field", name));
            }
            return value;
        });
    }

    private static MedianRange median(ContentYaml.MedianRange range) {
        if (range == null) {
            throw new ValidationException(ErrorCode.BLANK_VALUE, ErrorDetails.of("field", "median_range"));
        }
        return new MedianRange(required("min_pct", range.minPct()), required("max_pct", range.maxPct()));
    }

    private static CountRange count(ContentYaml.Count count) {
        if (count == null) {
            throw new ValidationException(ErrorCode.BLANK_VALUE, ErrorDetails.of("field", "count"));
        }
        return new CountRange(required("min", count.min()), required("max", count.max()));
    }

    private static int required(String field, Integer value) {
        if (value == null) {
            throw new ValidationException(ErrorCode.BLANK_VALUE, ErrorDetails.of("field", field));
        }
        return value;
    }

    private static GrammaticalGender gender(String key) {
        return ContentKeys.parse("gender", GrammaticalGender.values(), GrammaticalGender::key, key);
    }

    private static List<String> cases(String field, ContentYaml.Cases cases) {
        if (cases == null) {
            throw new ValidationException(ErrorCode.BLANK_VALUE, ErrorDetails.of("field", field));
        }
        return cases.inOrder();
    }

    private static ValidationException unknown(String field, Object value) {
        return new ValidationException(ErrorCode.UNKNOWN_REFERENCE, ErrorDetails.of("field", field, "value", value));
    }

    private static PersonKind personKind(String field, String key) {
        return ContentKeys.parse(field, PersonKind.values(), PersonKind::key, key);
    }

    private static List<ModifierDef> modifiers(String file, String owner, List<ContentYaml.Modifier> yaml) {
        return list(
                file,
                owner + ".modifiers",
                yaml,
                (location, modifier) -> at(
                        file,
                        location,
                        () -> new ModifierDef(ModifierTargets.parse(modifier.target()), modifier.value())));
    }

    /** Перетворює кожен елемент списку, передаючи його місце у файлі, напр. {@code ideologies[2]}. */
    private static <Y, T> List<T> list(String file, String field, List<Y> items, Element<Y, T> convert) {
        List<T> result = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            String location = field + "[" + i + "]";
            Y item = items.get(i);
            if (item == null) {
                throw invalid(
                        file,
                        location,
                        new ValidationException(ErrorCode.BLANK_VALUE, ErrorDetails.of("field", field)));
            }
            result.add(convert.apply(location, item));
        }
        return result;
    }

    /** Верхній список файлу: порожній означає, що колесо генерації не матиме жодного сектора. */
    private static <Y> List<Y> nonEmpty(String file, String field, List<Y> items) {
        if (items.isEmpty()) {
            throw invalid(
                    file, field, new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", field)));
        }
        return items;
    }

    @FunctionalInterface
    private interface Element<Y, T> {
        T apply(String location, Y item);
    }

    private static <K extends Comparable<K>> K unique(TreeSet<K> seen, K id) {
        return unique(seen, id, key -> key);
    }

    /** @param display як показати ключ у подробицях помилки: ключ контенту, а не ім'я константи enum */
    private static <K extends Comparable<K>> K unique(TreeSet<K> seen, K id, Function<K, Object> display) {
        if (!seen.add(id)) {
            throw new ValidationException(
                    ErrorCode.DUPLICATE_ID, ErrorDetails.of("field", "id", "value", display.apply(id)));
        }
        return id;
    }

    /** Кожне значення з {@code expected} визначено у файлі; пропуск прив'язується до списку {@code field}. */
    private static <K extends Comparable<K>> void complete(
            String file, String field, TreeSet<K> seen, List<K> expected, Function<K, Object> display) {
        for (K key : expected) {
            if (!seen.contains(key)) {
                throw invalid(
                        file,
                        field,
                        new ValidationException(
                                ErrorCode.MISSING_DEFINITION,
                                ErrorDetails.of("field", field, "value", display.apply(key))));
            }
        }
    }

    // ---- Помилки ----

    /** Будує значення моделі; помилку значення прив'язує до файлу й місця в ньому. */
    private static <T> T at(String file, String location, Supplier<T> build) {
        try {
            return build.get();
        } catch (ValidationException e) {
            throw invalid(file, location, e);
        }
    }

    private static ContentException invalid(String file, String location, ValidationException cause) {
        TreeMap<String, Object> details = new TreeMap<>(cause.details());
        details.put("file", file);
        details.put("location", location);
        details.put("cause", cause.code().name().toLowerCase(Locale.ROOT));
        return new ContentException(ErrorCode.INVALID_CONTENT, details, cause);
    }

    /** Шлях у форматі {@code doctrines[0].name}, як у {@link #invalid}. */
    private static String path(List<JsonMappingException.Reference> references) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference reference : references) {
            if (reference.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    private static ContentException malformed(String file, JsonLocation location, String problem) {
        return malformed(file, location, problem, null);
    }

    private static ContentException malformed(String file, JsonLocation location, String problem, Throwable cause) {
        JsonLocation where = location == null ? JsonLocation.NA : location;
        return new ContentException(
                ErrorCode.CONTENT_MALFORMED,
                ErrorDetails.of(
                        "file",
                        file,
                        "line",
                        where.getLineNr(),
                        "column",
                        where.getColumnNr(),
                        "problem",
                        String.valueOf(problem)),
                cause);
    }

    private static YAMLMapper createMapper() {
        YAMLMapper mapper = YAMLMapper.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        // «value: "5"» чи «name: 5» — найімовірніше помилка автора контенту, а не намір.
        for (CoercionInputShape shape :
                List.of(CoercionInputShape.String, CoercionInputShape.Float, CoercionInputShape.Boolean)) {
            mapper.coercionConfigFor(LogicalType.Integer).setCoercion(shape, CoercionAction.Fail);
        }
        for (CoercionInputShape shape :
                List.of(CoercionInputShape.Integer, CoercionInputShape.Float, CoercionInputShape.Boolean)) {
            mapper.coercionConfigFor(LogicalType.Textual).setCoercion(shape, CoercionAction.Fail);
        }
        for (CoercionInputShape shape :
                List.of(CoercionInputShape.String, CoercionInputShape.Integer, CoercionInputShape.Float)) {
            mapper.coercionConfigFor(LogicalType.Boolean).setCoercion(shape, CoercionAction.Fail);
        }
        return mapper;
    }
}
