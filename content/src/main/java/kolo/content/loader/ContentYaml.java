package kolo.content.loader;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Структура YAML-файлів контенту. Лише форма даних: значення перевіряють конструктори моделі рушія. Відсутні
 * списки стають порожніми.
 */
final class ContentYaml {

    private ContentYaml() {}

    record IdeologiesFile(List<Ideology> ideologies) {
        IdeologiesFile {
            ideologies = orEmpty(ideologies);
        }
    }

    record Ideology(
            String id,
            String name,
            Integer weight,
            List<String> tags,
            List<Modifier> modifiers,
            @JsonProperty("sub_ideologies") List<SubIdeology> subIdeologies) {
        Ideology {
            tags = orEmpty(tags);
            modifiers = orEmpty(modifiers);
            subIdeologies = orEmpty(subIdeologies);
        }
    }

    record SubIdeology(String id, String name, Integer weight, List<String> tags, List<Modifier> modifiers) {
        SubIdeology {
            tags = orEmpty(tags);
            modifiers = orEmpty(modifiers);
        }
    }

    record DoctrinesFile(List<Doctrine> doctrines) {
        DoctrinesFile {
            doctrines = orEmpty(doctrines);
        }
    }

    record Doctrine(String id, String name, List<String> tags, List<Modifier> modifiers) {
        Doctrine {
            tags = orEmpty(tags);
            modifiers = orEmpty(modifiers);
        }
    }

    record ResourcesFile(List<Resource> resources) {
        ResourcesFile {
            resources = orEmpty(resources);
        }
    }

    record Resource(String id, String name, List<String> tags, Deposits deposits) {
        Resource {
            tags = orEmpty(tags);
        }
    }

    /**
     * @param terrains ключ типу місцевості → придатність
     * @param climates ключ поясу → множник, %
     * @param fertilityFrom поріг родючості; виключає {@code terrains} і {@code climates}
     */
    record Deposits(
            Map<String, Integer> terrains,
            Map<String, Integer> climates,
            @JsonProperty("fertility_from") Integer fertilityFrom) {
        Deposits {
            terrains = terrains == null ? Map.of() : terrains;
            climates = climates == null ? Map.of() : climates;
        }
    }

    record DevelopmentFile(List<Branch> branches, List<Level> levels) {
        DevelopmentFile {
            branches = orEmpty(branches);
            levels = orEmpty(levels);
        }
    }

    /** @param id ключ галузі, напр. {@code energy_science} */
    record Branch(String id, String name) {}

    record Level(int level, String name, String description, Integer weight, Integer quality, List<String> tags) {
        Level {
            tags = orEmpty(tags);
        }
    }

    record NuclearFile(List<NuclearStatus> statuses) {
        NuclearFile {
            statuses = orEmpty(statuses);
        }
    }

    /** @param id ключ статусу: {@code none}, {@code program} або {@code arsenal} */
    record NuclearStatus(String id, String name, Integer weight, Integer quality, List<String> tags) {
        NuclearStatus {
            tags = orEmpty(tags);
        }
    }

    record GdpFile(List<GdpLevel> levels) {
        GdpFile {
            levels = orEmpty(levels);
        }
    }

    /**
     * @param perCapita ВВП на душу, умовні долари 1970 року
     * @param tier ключ рівня результату: {@code crit_fail}, {@code fail}, {@code partial}, {@code success} або
     *     {@code crit_success}
     */
    record GdpLevel(
            String id,
            String name,
            String description,
            @JsonProperty("per_capita") Integer perCapita,
            String tier,
            Integer weight,
            Integer quality,
            List<String> tags) {
        GdpLevel {
            tags = orEmpty(tags);
        }
    }

    record HdiFile(List<HdiLevel> levels) {
        HdiFile {
            levels = orEmpty(levels);
        }
    }

    /**
     * @param hdi стартовий ІЛР держави на рівні, 0..100
     * @param tier ключ рівня результату, як у {@link GdpLevel}
     */
    record HdiLevel(
            String id,
            String name,
            String description,
            Integer hdi,
            String tier,
            Integer weight,
            Integer quality,
            List<String> tags) {
        HdiLevel {
            tags = orEmpty(tags);
        }
    }

    record ArmyFile(List<ArmySize> sizes, List<TrainingLevel> training) {
        ArmyFile {
            sizes = orEmpty(sizes);
            training = orEmpty(training);
        }
    }

    /**
     * @param shareBp частка населення під зброєю, базисні пункти
     * @param tier ключ рівня результату, як у {@link GdpLevel}
     */
    record ArmySize(
            String id,
            String name,
            String description,
            @JsonProperty("share_bp") Integer shareBp,
            String tier,
            Integer weight,
            Integer quality,
            List<String> tags) {
        ArmySize {
            tags = orEmpty(tags);
        }
    }

    /**
     * @param level рівень вишколу {@code 1..5}
     * @param combatModifier модифікатор вишколу в боях
     * @param tier ключ рівня результату, як у {@link GdpLevel}
     */
    record TrainingLevel(
            Integer level,
            String name,
            String description,
            @JsonProperty("combat_modifier") Integer combatModifier,
            String tier,
            Integer weight,
            Integer quality,
            List<String> tags) {
        TrainingLevel {
            tags = orEmpty(tags);
        }
    }

    record PeopleFile(List<PersonKind> kinds, List<Trait> traits) {
        PeopleFile {
            kinds = orEmpty(kinds);
            traits = orEmpty(traits);
        }
    }

    /**
     * @param id ключ типу постаті, напр. {@code pretender}
     * @param weightTags мітка → добавка до ваги
     */
    record PersonKind(
            String id,
            String name,
            String description,
            Integer weight,
            @JsonProperty("weight_tags") Map<String, Integer> weightTags,
            List<String> tags) {
        PersonKind {
            weightTags = weightTags == null ? Map.of() : weightTags;
            tags = orEmpty(tags);
        }
    }

    /**
     * @param kinds ключі типів постатей; порожньо — будь-який тип
     * @param incompatible id несумісних рис
     */
    record Trait(
            String id,
            String name,
            List<String> kinds,
            List<String> tags,
            List<Modifier> modifiers,
            List<String> incompatible) {
        Trait {
            kinds = orEmpty(kinds);
            tags = orEmpty(tags);
            modifiers = orEmpty(modifiers);
            incompatible = orEmpty(incompatible);
        }
    }

    record NamesFile(
            List<Paradigm> paradigms,
            List<NameStyle> styles,
            @JsonProperty("state_forms") List<StateForm> stateForms,
            @JsonProperty("person_styles") List<PersonStyle> personStyles) {
        NamesFile {
            paradigms = orEmpty(paradigms);
            styles = orEmpty(styles);
            stateForms = orEmpty(stateForms);
            personStyles = orEmpty(personStyles);
        }
    }

    /** @param gender ключ роду: {@code masculine}, {@code feminine}, {@code neuter} або {@code plural} */
    record Paradigm(String id, String gender, Cases endings) {}

    record NameStyle(
            String id,
            String name,
            List<String> starts,
            List<String> middles,
            @JsonProperty("middle_chance_bp") int middleChanceBp,
            List<NameFinal> finals) {
        NameStyle {
            starts = orEmpty(starts);
            middles = orEmpty(middles);
            finals = orEmpty(finals);
        }
    }

    /** @param paradigm id парадигми відмінювання */
    record NameFinal(String text, String paradigm) {}

    /** @param id id стилю назв держав, якому відповідає стиль імен */
    record PersonStyle(
            String id, @JsonProperty("given_names") GivenNames givenNames, Surnames surnames) {}

    /**
     * @param male кінцівки чоловічих імен
     * @param female кінцівки жіночих імен
     */
    record GivenNames(
            List<String> starts,
            List<String> middles,
            @JsonProperty("middle_chance_bp") int middleChanceBp,
            List<NameFinal> male,
            List<NameFinal> female) {
        GivenNames {
            starts = orEmpty(starts);
            middles = orEmpty(middles);
            male = orEmpty(male);
            female = orEmpty(female);
        }
    }

    record Surnames(
            List<String> starts,
            List<String> middles,
            @JsonProperty("middle_chance_bp") int middleChanceBp,
            List<SurnameFinal> finals) {
        Surnames {
            starts = orEmpty(starts);
            middles = orEmpty(middles);
            finals = orEmpty(finals);
        }
    }

    /**
     * @param male id парадигми чоловічого прізвища
     * @param female id парадигми жіночого прізвища
     */
    record SurnameFinal(String text, String male, String female) {}

    /** @param forms шаблони назви за відмінками, з {@code {root}} */
    record StateForm(
            String id,
            String gender,
            List<String> ideologies,
            @JsonProperty("sub_ideologies") List<String> subIdeologies,
            Cases forms) {
        StateForm {
            ideologies = orEmpty(ideologies);
            subIdeologies = orEmpty(subIdeologies);
        }
    }

    /** @param generationTags мітка → коли її дає колесо генерації */
    record BackstoryFile(
            @JsonProperty("generation_tags") Map<String, String> generationTags, List<Fragment> fragments) {
        BackstoryFile {
            generationTags = generationTags == null ? Map.of() : generationTags;
            fragments = orEmpty(fragments);
        }
    }

    /**
     * @param quality обов'язкова: пропуск не повинен тихо ставати нулем
     * @param weightTags мітка → добавка до ваги
     * @param neighbor без поля — фрагмент без сусіда
     * @param duration роки дії модифікаторів; без поля — постійно
     */
    record Fragment(
            String id,
            int weight,
            Integer quality,
            Years years,
            List<String> requires,
            @JsonProperty("requires_any") List<String> requiresAny,
            List<String> excludes,
            @JsonProperty("weight_tags") Map<String, Integer> weightTags,
            Boolean neighbor,
            List<String> adds,
            Integer duration,
            List<Modifier> modifiers,
            String text) {
        Fragment {
            requires = orEmpty(requires);
            requiresAny = orEmpty(requiresAny);
            excludes = orEmpty(excludes);
            weightTags = weightTags == null ? Map.of() : weightTags;
            adds = orEmpty(adds);
            neighbor = neighbor != null && neighbor;
            duration = duration == null ? 0 : duration;
            modifiers = orEmpty(modifiers);
        }
    }

    record Years(int from, int to) {}

    record StreaksFile(List<StreakWheel> wheels) {
        StreaksFile {
            wheels = orEmpty(wheels);
        }
    }

    /** @param id ключ виду стріку, напр. {@code golden_age} */
    record StreakWheel(String id, String name, String description, List<String> tags, List<StreakReward> rewards) {
        StreakWheel {
            tags = orEmpty(tags);
            rewards = orEmpty(rewards);
        }
    }

    /**
     * @param duration роки дії модифікаторів; без поля — постійно
     * @param fateTokens без поля — жодного жетона
     * @param extraPeople без поля — жодної додаткової постаті
     */
    record StreakReward(
            String id,
            String name,
            String description,
            Integer weight,
            Integer duration,
            List<Modifier> modifiers,
            @JsonProperty("fate_tokens") Integer fateTokens,
            @JsonProperty("extra_people") Integer extraPeople,
            List<String> tags) {
        StreakReward {
            duration = duration == null ? 0 : duration;
            modifiers = orEmpty(modifiers);
            fateTokens = fateTokens == null ? 0 : fateTokens;
            extraPeople = extraPeople == null ? 0 : extraPeople;
            tags = orEmpty(tags);
        }
    }

    record ReligionsFile(
            List<Archetype> archetypes,
            List<Aspect> aspects,
            List<Dogma> dogmas,
            List<Polity> polities,
            @JsonProperty("faith_forms") List<FaithForm> faithForms,
            @JsonProperty("state_religion") StateReligion stateReligion) {
        ReligionsFile {
            archetypes = orEmpty(archetypes);
            aspects = orEmpty(aspects);
            dogmas = orEmpty(dogmas);
            polities = orEmpty(polities);
            faithForms = orEmpty(faithForms);
        }
    }

    /**
     * @param figure роль постаті, чиїм ім'ям зветься віра
     * @param figureSexes ключі статей постаті, напр. {@code male}
     */
    record Archetype(
            String id,
            String name,
            String description,
            String figure,
            @JsonProperty("figure_sexes") List<String> figureSexes,
            Integer weight,
            List<String> tags) {
        Archetype {
            figureSexes = orEmpty(figureSexes);
            tags = orEmpty(tags);
        }
    }

    /** @param weightTags мітка → добавка до ваги */
    record Aspect(
            String id,
            String name,
            String description,
            Integer weight,
            @JsonProperty("weight_tags") Map<String, Integer> weightTags,
            List<String> tags) {
        Aspect {
            weightTags = weightTags == null ? Map.of() : weightTags;
            tags = orEmpty(tags);
        }
    }

    /**
     * @param weightTags мітка → добавка до ваги
     * @param incompatible id несумісних догматів
     */
    record Dogma(
            String id,
            String name,
            String description,
            Integer weight,
            @JsonProperty("weight_tags") Map<String, Integer> weightTags,
            List<Modifier> modifiers,
            List<String> tags,
            List<String> incompatible) {
        Dogma {
            weightTags = weightTags == null ? Map.of() : weightTags;
            modifiers = orEmpty(modifiers);
            tags = orEmpty(tags);
            incompatible = orEmpty(incompatible);
        }
    }

    /** @param weightTags мітка → добавка до ваги */
    record Polity(
            String id,
            String name,
            String description,
            Integer weight,
            @JsonProperty("weight_tags") Map<String, Integer> weightTags,
            List<Modifier> modifiers,
            List<String> tags) {
        Polity {
            weightTags = weightTags == null ? Map.of() : weightTags;
            modifiers = orEmpty(modifiers);
            tags = orEmpty(tags);
        }
    }

    /** @param religionWeight вага кожної релігії світу в колесі релігії держави */
    record StateReligion(@JsonProperty("religion_weight") Integer religionWeight, Secular secular) {}

    /** @param weightTags мітка ладу → добавка до ваги */
    record Secular(
            String name,
            String description,
            Integer weight,
            @JsonProperty("weight_tags") Map<String, Integer> weightTags,
            List<String> requires,
            @JsonProperty("requires_any") List<String> requiresAny,
            List<String> excludes,
            List<String> tags) {
        Secular {
            weightTags = weightTags == null ? Map.of() : weightTags;
            requires = orEmpty(requires);
            requiresAny = orEmpty(requiresAny);
            excludes = orEmpty(excludes);
            tags = orEmpty(tags);
        }
    }

    /**
     * @param figureCase ключ відмінка імені постаті в назві, напр. {@code genitive}
     * @param forms шаблони назви віри за відмінками, з {@code {figure}}
     */
    record FaithForm(
            String id,
            String gender,
            @JsonProperty("figure_case") String figureCase,
            List<String> archetypes,
            Cases forms) {
        FaithForm {
            archetypes = orEmpty(archetypes);
        }
    }

    /**
     * Числа — {@code Integer}, а не {@code int}: пропущене поле має стати помилкою, а не тихим нулем.
     *
     * @param powerCorridors по одному на кожен варіант коридору
     */
    record BalanceFile(
            WheelBalance wheel,
            Streaks streaks,
            @JsonProperty("power_corridors") List<PowerCorridor> powerCorridors,
            Generation generation,
            Religion religion,
            World world,
            Resources resources) {
        BalanceFile {
            powerCorridors = orEmpty(powerCorridors);
        }
    }

    /** @param strength тип колеса → окрема сила переваги */
    record WheelBalance(
            @JsonProperty("default_strength") Integer defaultStrength,
            Map<String, Integer> strength,
            @JsonProperty("investment_curve") List<Integer> investmentCurve) {
        WheelBalance {
            strength = strength == null ? Map.of() : strength;
            investmentCurve = orEmpty(investmentCurve);
        }
    }

    record Streaks(
            @JsonProperty("very_good_quality") Integer veryGoodQuality,
            @JsonProperty("very_bad_quality") Integer veryBadQuality,
            Integer length) {}

    /** @param id ключ варіанта: {@code equal_chances}, {@code classic} або {@code full_chaos} */
    record PowerCorridor(String id, MedianRange players, MedianRange npc) {}

    record MedianRange(
            @JsonProperty("min_pct") Integer minPct,
            @JsonProperty("max_pct") Integer maxPct) {}

    record Generation(
            @JsonProperty("backstory_fragments") Count backstoryFragments,
            @JsonProperty("notable_people") Count notablePeople,
            Count warheads,
            @JsonProperty("nuclear_energy_advantage") Integer nuclearEnergyAdvantage,
            @JsonProperty("gdp_development_advantage") Integer gdpDevelopmentAdvantage,
            @JsonProperty("hdi_gdp_advantage") Integer hdiGdpAdvantage,
            @JsonProperty("army_size_gdp_advantage") Integer armySizeGdpAdvantage,
            @JsonProperty("army_training_gdp_advantage") Integer armyTrainingGdpAdvantage,

            @JsonProperty("army_training_development_advantage")
            Integer armyTrainingDevelopmentAdvantage,

            @JsonProperty("person_traits") Count personTraits,
            @JsonProperty("person_age") Count personAge,
            @JsonProperty("name_candidates") Integer nameCandidates) {}

    /** @param count таблиця кількості релігій за кількістю держав */
    record Religion(List<ReligionCount> count, Count aspects, Count dogmas) {
        Religion {
            count = orEmpty(count);
        }
    }

    record ReligionCount(@JsonProperty("max_countries") Integer maxCountries, Integer min, Integer max) {}

    record Resources(List<ResourceCount> count) {
        Resources {
            count = orEmpty(count);
        }
    }

    record ResourceCount(@JsonProperty("max_provinces") Integer maxProvinces, Integer min, Integer max) {}

    record Count(Integer min, Integer max) {}

    /** @param npcExtra ключ частки NPC ({@code few}, {@code normal}, {@code many}) → діапазон колеса */
    record World(
            @JsonProperty("npc_extra") Map<String, Count> npcExtra,
            @JsonProperty("provinces_per_country") Step provincesPerCountry,
            @JsonProperty("unclaimed_bp") Step unclaimedBp,
            Count provinces) {
        World {
            npcExtra = npcExtra == null ? Map.of() : npcExtra;
        }
    }

    record Step(Integer min, Integer max, Integer step) {}

    record MapFile(
            List<MapTemplate> templates,
            MapGrid grid,
            Continents continents,
            Relief relief,
            Climate climate,
            Sea sea,
            Rivers rivers,
            Fertility fertility) {
        MapFile {
            templates = orEmpty(templates);
        }
    }

    record MapGrid(@JsonProperty("cell_size") Integer cellSize, MapAspect aspect, Integer relaxation) {}

    record MapAspect(Integer width, Integer height) {}

    record Continents(
            @JsonProperty("size_weight") Count sizeWeight,
            @JsonProperty("min_provinces") Integer minProvinces,
            Integer roughness,
            @JsonProperty("noise_cells") Integer noiseCells) {}

    record Sea(
            @JsonProperty("min_cells") Integer minCells,
            @JsonProperty("zone_cells") Integer zoneCells) {}

    record Rivers(
            @JsonProperty("min_flow") Integer minFlow,
            @JsonProperty("min_cells") Integer minCells) {}

    /**
     * @param climates ключ поясу → основа
     * @param terrains ключ типу місцевості → поправка
     */
    record Fertility(
            Map<String, Integer> climates,
            Map<String, Integer> terrains,
            @JsonProperty("moisture_pct") Integer moisturePct,
            Integer river) {
        Fertility {
            climates = climates == null ? Map.of() : climates;
            terrains = terrains == null ? Map.of() : terrains;
        }
    }

    record Relief(
            Count ridges,
            @JsonProperty("ridge_min_provinces") Integer ridgeMinProvinces,
            @JsonProperty("ridge_length_pct") Integer ridgeLengthPct,
            @JsonProperty("ridge_wander") Integer ridgeWander,
            @JsonProperty("ridge_height") Integer ridgeHeight,
            @JsonProperty("ridge_falloff") Integer ridgeFalloff,
            @JsonProperty("base_height") Integer baseHeight,
            @JsonProperty("noise_amplitude") Integer noiseAmplitude,
            @JsonProperty("noise_cells") Integer noiseCells,
            List<ReliefLevel> levels) {
        Relief {
            levels = orEmpty(levels);
        }
    }

    record ReliefLevel(
            String id,
            String name,
            String description,
            @JsonProperty("min_height") Integer minHeight) {}

    record Climate(
            List<WorldClimate> worlds,
            ClimateTemperature temperature,
            ClimateMoisture moisture,
            @JsonProperty("noise_cells") Integer noiseCells,
            @JsonProperty("polar_below") Integer polarBelow,
            @JsonProperty("boreal_below") Integer borealBelow,
            @JsonProperty("tropical_from") Integer tropicalFrom,
            @JsonProperty("arid_below") Integer aridBelow,
            List<ClimateZone> zones,
            List<Cover> covers) {
        Climate {
            worlds = orEmpty(worlds);
            zones = orEmpty(zones);
            covers = orEmpty(covers);
        }
    }

    record WorldClimate(
            String id,
            String name,
            String description,
            Integer weight,
            @JsonProperty("temperature_shift") Integer temperatureShift) {}

    record ClimateTemperature(
            Integer equator,
            Integer pole,
            @JsonProperty("height_cooling") Integer heightCooling,
            @JsonProperty("noise_amplitude") Integer noiseAmplitude) {}

    record ClimateMoisture(
            Integer coast,
            @JsonProperty("inland_drying") Integer inlandDrying,
            @JsonProperty("noise_amplitude") Integer noiseAmplitude) {}

    record ClimateZone(String id, String name, String description) {}

    record Cover(
            String id,
            String name,
            String description,
            List<String> climates,
            List<String> reliefs,
            Count moisture,
            Count height) {
        Cover {
            climates = orEmpty(climates);
            reliefs = orEmpty(reliefs);
        }
    }

    record MapTemplate(
            String id,
            String name,
            String description,
            Integer weight,
            @JsonProperty("provinces_pct") Integer provincesPct,
            @JsonProperty("land_pct") Integer landPct,
            Count continents) {}

    /** По рядку на відмінок; порядок полів — порядок {@link kolo.engine.state.GrammaticalCase}. */
    record Cases(
            String nominative,
            String genitive,
            String dative,
            String accusative,
            String instrumental,
            String locative,
            String vocative) {

        /** Форми в порядку відмінків; {@code null} — пропущений відмінок, його відловить перевірка моделі. */
        List<String> inOrder() {
            return Arrays.asList(nominative, genitive, dative, accusative, instrumental, locative, vocative);
        }
    }

    /** @param target {@code stat:<показник>} або {@code wheel:<тип колеса>} */
    record Modifier(String target, int value) {}

    // Не List.copyOf: null-елементи мають дійти до перевірки й стати помилкою з місцем у файлі, а не NPE.
    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
