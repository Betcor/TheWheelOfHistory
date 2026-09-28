package kolo.content.loader;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.List;

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
            List<String> tags,
            List<Modifier> modifiers,
            @JsonProperty("sub_ideologies") List<SubIdeology> subIdeologies) {
        Ideology {
            tags = orEmpty(tags);
            modifiers = orEmpty(modifiers);
            subIdeologies = orEmpty(subIdeologies);
        }
    }

    record SubIdeology(String id, String name, List<String> tags, List<Modifier> modifiers) {
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

    record Level(int level, String name, String description) {}

    record NuclearFile(List<NuclearStatus> statuses) {
        NuclearFile {
            statuses = orEmpty(statuses);
        }
    }

    /** @param id ключ статусу: {@code none}, {@code program} або {@code arsenal} */
    record NuclearStatus(String id, String name, List<String> tags) {
        NuclearStatus {
            tags = orEmpty(tags);
        }
    }

    record PeopleFile(List<PersonKind> kinds, List<Trait> traits) {
        PeopleFile {
            kinds = orEmpty(kinds);
            traits = orEmpty(traits);
        }
    }

    /** @param id ключ типу постаті, напр. {@code pretender} */
    record PersonKind(String id, String name, String description, List<String> tags) {
        PersonKind {
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
            @JsonProperty("state_forms") List<StateForm> stateForms) {
        NamesFile {
            paradigms = orEmpty(paradigms);
            styles = orEmpty(styles);
            stateForms = orEmpty(stateForms);
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
