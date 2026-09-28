package kolo.content.loader;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.TreeMap;

/** Мінімальний валідний контент у пам'яті; тести замінюють окремі файли. */
final class Files {

    static final String IDEOLOGIES = """
            ideologies:
              - id: democracy
                name: Демократія
                tags: [democratic]
                modifiers:
                  - { target: "stat:hdi", value: 5 }
                sub_ideologies:
                  - id: liberal_democracy
                    name: Ліберальна демократія
                    modifiers:
                      - { target: "wheel:economic_cycle", value: 10 }
              - id: totalitarianism
                name: Тоталітаризм
                sub_ideologies:
                  - id: revanchism
                    name: Реваншизм
                    tags: [revanchism]
            """;

    static final String DOCTRINES = """
            doctrines:
              - id: armored
                name: Бронетанкова
                tags: [land]
            """;

    static final String RESOURCES = """
            resources:
              - id: iron
                name: Залізо
              - id: oil
                name: Нафта
                tags: [energy]
            """;

    private final TreeMap<String, String> files = new TreeMap<>();

    private Files() {
        files.put(ContentLoader.IDEOLOGIES, IDEOLOGIES);
        files.put(ContentLoader.DOCTRINES, DOCTRINES);
        files.put(ContentLoader.RESOURCES, RESOURCES);
    }

    static Files valid() {
        return new Files();
    }

    Files with(String file, String content) {
        files.put(file, content);
        return this;
    }

    Files without(String file) {
        files.remove(file);
        return this;
    }

    ContentSource source() {
        return name -> Optional.ofNullable(files.get(name)).map(text -> text.getBytes(StandardCharsets.UTF_8));
    }
}
