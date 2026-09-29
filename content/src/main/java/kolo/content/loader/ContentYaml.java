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

    record Resource(String id, String name, List<String> tags) {
        Resource {
            tags = orEmpty(tags);
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

    /**
     * Числа — {@code Integer}, а не {@code int}: пропущене поле має стати помилкою, а не тихим нулем.
     *
     * @param powerCorridors по одному на кожен варіант коридору
     */
    record BalanceFile(
            WheelBalance wheel,
            Streaks streaks,
            @JsonProperty("power_corridors") List<PowerCorridor> powerCorridors,
            Generation generation) {
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

    record Count(Integer min, Integer max) {}

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
