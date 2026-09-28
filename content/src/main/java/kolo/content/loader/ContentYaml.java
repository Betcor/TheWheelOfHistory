package kolo.content.loader;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    /** @param target {@code stat:<показник>} або {@code wheel:<тип колеса>} */
    record Modifier(String target, int value) {}

    // Не List.copyOf: null-елементи мають дійти до перевірки й стати помилкою з місцем у файлі, а не NPE.
    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
