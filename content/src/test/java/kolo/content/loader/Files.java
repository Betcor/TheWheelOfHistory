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
                weight: 300
                tags: [democratic]
                modifiers:
                  - { target: "stat:hdi", value: 5 }
                sub_ideologies:
                  - id: liberal_democracy
                    name: Ліберальна демократія
                    weight: 100
                    modifiers:
                      - { target: "wheel:economic_cycle", value: 10 }
              - id: totalitarianism
                name: Тоталітаризм
                weight: 100
                sub_ideologies:
                  - id: revanchism
                    name: Реваншизм
                    weight: 100
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

    static final String DEVELOPMENT = """
            branches:
              - { id: economy, name: Економіка }
              - { id: military, name: Військо }
              - { id: society, name: Суспільство }
              - { id: energy_science, name: Енергетика й наука }
            levels:
              - { level: -3, name: Глибоке відставання, description: Лише базові технології., weight: 8, quality: 5, tags: [backward] }
              - { level: -2, name: Відставання, description: Частини технологій немає., weight: 14, quality: 15 }
              - { level: -1, name: Легке відставання, description: Окремих технологій бракує., weight: 22, quality: 35 }
              - { level: 0, name: Світовий рівень, description: Усі технології 1970 року., weight: 30, quality: 50 }
              - { level: 1, name: Передовий, description: На 1–2 технології попереду., weight: 18, quality: 75 }
              - { level: 2, name: Лідер, description: Кілька технологій попереду., weight: 8, quality: 95 }
            """;

    static final String NUCLEAR = """
            statuses:
              - id: none
                name: Без ядерної зброї
                weight: 80
                quality: 50
              - id: program
                name: Ядерна програма
                weight: 13
                quality: 70
              - id: arsenal
                name: Ядерний арсенал
                weight: 7
                quality: 90
                tags: [nuclear_power]
            """;

    static final String PEOPLE = """
            kinds:
              - { id: scientist, name: Вчений, description: Прискорює дослідження. }
              - { id: general, name: Генерал, description: Командує фронтом., tags: [military] }
              - { id: admiral, name: Адмірал, description: Командує флотом. }
              - { id: diplomat, name: Дипломат, description: Веде переговори. }
              - { id: magnate, name: Магнат, description: Власник капіталу. }
              - { id: prophet, name: Пророк, description: Проповідник. }
              - { id: dissident, name: Дисидент, description: Голос опозиції. }
              - { id: artist, name: Митець, description: Формує культуру. }
              - { id: pretender, name: Диктатор-претендент, description: Прагне влади. }
            traits:
              - id: loyal
                name: Відданий
                tags: [positive]
                incompatible: [treacherous]
              - id: treacherous
                name: Підступний
                tags: [negative]
              - id: genius
                name: Геній
                kinds: [scientist]
            """;

    static final String NAMES = """
            paradigms:
              - id: masc_hard
                gender: masculine
                endings: { nominative: "", genitive: у, dative: у, accusative: "", instrumental: ом, locative: і, vocative: е }
              - id: person_masc
                gender: masculine
                endings: { nominative: "", genitive: а, dative: ові, accusative: а, instrumental: ом, locative: ові, vocative: е }
              - id: fem_hard
                gender: feminine
                endings: { nominative: а, genitive: и, dative: і, accusative: у, instrumental: ою, locative: і, vocative: о }
              - id: fixed_fem
                gender: feminine
                endings: { nominative: "", genitive: "", dative: "", accusative: "", instrumental: "", locative: "", vocative: "" }
            styles:
              - id: northern
                name: Північний
                middle_chance_bp: 3000
                starts: [вел, тор]
                middles: [ім]
                finals:
                  - { text: ор, paradigm: masc_hard }
            state_forms:
              - id: republic
                gender: feminine
                ideologies: [democracy]
                forms:
                  nominative: Республіка {root}
                  genitive: Республіки {root}
                  dative: Республіці {root}
                  accusative: Республіку {root}
                  instrumental: Республікою {root}
                  locative: Республіці {root}
                  vocative: Республіко {root}
              - id: state
                gender: feminine
                sub_ideologies: [revanchism]
                forms:
                  nominative: Держава {root}
                  genitive: Держави {root}
                  dative: Державі {root}
                  accusative: Державу {root}
                  instrumental: Державою {root}
                  locative: Державі {root}
                  vocative: Державо {root}
            person_styles:
              - id: northern
                given_names:
                  starts: [ал, дар]
                  middles: [ен]
                  middle_chance_bp: 1000
                  male:
                    - { text: ор, paradigm: person_masc }
                  female:
                    - { text: ін, paradigm: fem_hard }
                surnames:
                  starts: [торв]
                  middle_chance_bp: 0
                  finals:
                    - { text: ер, male: person_masc, female: fixed_fem }
            """;

    static final String BACKSTORY = """
            generation_tags:
              large_army: Велика армія.
            fragments:
              - id: lost_war
                weight: 80
                quality: 10
                years: { from: 1945, to: 1966 }
                neighbor: true
                weight_tags: { revanchism: 400, large_army: 100 }
                adds: [lost_war]
                duration: 10
                modifiers:
                  - { target: "stat:stability", value: -5 }
                text: >-
                  У {year} році країна програла війну {neighbor.dative}.
              - id: reparations
                weight: 150
                quality: 5
                years: { from: 1946, to: 1968 }
                requires: [lost_war]
                requires_any: [revanchism, democratic]
                excludes: [nuclear_power]
                text: Контрибуції задушили економіку {country.genitive}.
            """;

    static final String BALANCE = """
            wheel:
              default_strength: 50
              strength:
                economic_cycle: 80
              investment_curve: [20, 12, 7, 4]
            streaks:
              very_good_quality: 85
              very_bad_quality: 15
              length: 3
            power_corridors:
              - id: equal_chances
                players: { min_pct: 75, max_pct: 133 }
                npc: { min_pct: 50, max_pct: 200 }
              - id: classic
                players: { min_pct: 50, max_pct: 200 }
                npc: { min_pct: 33, max_pct: 300 }
              - id: full_chaos
                players: { min_pct: 20, max_pct: 500 }
                npc: { min_pct: 10, max_pct: 1000 }
            generation:
              backstory_fragments: { min: 2, max: 4 }
              notable_people: { min: 1, max: 3 }
              warheads: { min: 2, max: 10 }
              nuclear_energy_advantage: 10
            """;

    private final TreeMap<String, String> files = new TreeMap<>();

    private Files() {
        files.put(ContentLoader.IDEOLOGIES, IDEOLOGIES);
        files.put(ContentLoader.DOCTRINES, DOCTRINES);
        files.put(ContentLoader.RESOURCES, RESOURCES);
        files.put(ContentLoader.DEVELOPMENT, DEVELOPMENT);
        files.put(ContentLoader.NUCLEAR, NUCLEAR);
        files.put(ContentLoader.PEOPLE, PEOPLE);
        files.put(ContentLoader.NAMES, NAMES);
        files.put(ContentLoader.BACKSTORY, BACKSTORY);
        files.put(ContentLoader.BALANCE, BALANCE);
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
