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
                deposits:
                  terrains: { mountains: 40, hills: 20 }
                  climates: { arid: 150 }
              - id: oil
                name: Нафта
                tags: [energy]
              - id: fertile_land
                name: Родючі землі
                deposits:
                  fertility_from: 60
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

    static final String GDP = """
            levels:
              - { id: poor, name: Бідність, description: Аграрна економіка., per_capita: 250, tier: crit_fail, weight: 30, quality: 20, tags: [poor] }
              - { id: middle, name: Середній дохід, description: Індустріальна економіка., per_capita: 1000, tier: partial, weight: 40, quality: 50 }
              - { id: rich, name: Заможність, description: Високі зарплати., per_capita: 3500, tier: crit_success, weight: 30, quality: 80, tags: [rich] }
            """;

    static final String HDI = """
            levels:
              - { id: low, name: Низький розвиток, description: Мало шкіл і лікарень., hdi: 30, tier: crit_fail, weight: 30, quality: 20 }
              - { id: middle, name: Середній розвиток, description: Базова медицина., hdi: 60, tier: partial, weight: 40, quality: 50 }
              - { id: high, name: Високий розвиток, description: Довге життя., hdi: 80, tier: crit_success, weight: 30, quality: 80, tags: [educated] }
            """;

    static final String ARMY = """
            sizes:
              - { id: small, name: Мала армія, description: Кілька бригад., share_bp: 40, tier: crit_fail, weight: 30, quality: 30, tags: [small_army] }
              - { id: regular, name: Звичайна армія, description: Армія мирного часу., share_bp: 150, tier: partial, weight: 40, quality: 50 }
              - { id: large, name: Велика армія, description: Загальний призов., share_bp: 300, tier: crit_success, weight: 30, quality: 60, tags: [large_army] }
            training:
              - { level: 1, name: Ополчення, description: Новобранці., combat_modifier: -20, tier: crit_fail, weight: 10, quality: 10 }
              - { level: 2, name: Резервісти, description: Запас., combat_modifier: -10, tier: fail, weight: 25, quality: 30 }
              - { level: 3, name: Регулярна армія, description: Кадрові частини., combat_modifier: 0, tier: partial, weight: 35, quality: 50 }
              - { level: 4, name: Ветерани, description: Бойовий досвід., combat_modifier: 10, tier: success, weight: 20, quality: 70 }
              - { level: 5, name: Еліта, description: Професіонали., combat_modifier: 20, tier: crit_success, weight: 10, quality: 90, tags: [elite_army] }
            """;

    static final String PEOPLE = """
            kinds:
              - { id: scientist, name: Вчений, description: Прискорює дослідження., weight: 10 }
              - { id: general, name: Генерал, description: Командує фронтом., weight: 10, weight_tags: { educated: 5 }, tags: [military] }
              - { id: admiral, name: Адмірал, description: Командує флотом., weight: 10 }
              - { id: diplomat, name: Дипломат, description: Веде переговори., weight: 10 }
              - { id: magnate, name: Магнат, description: Власник капіталу., weight: 10 }
              - { id: prophet, name: Пророк, description: Проповідник., weight: 10 }
              - { id: dissident, name: Дисидент, description: Голос опозиції., weight: 10 }
              - { id: artist, name: Митець, description: Формує культуру., weight: 10 }
              - { id: pretender, name: Диктатор-претендент, description: Прагне влади., weight: 10 }
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
              gdp_development_advantage: 10
              hdi_gdp_advantage: 15
              army_size_gdp_advantage: 10
              army_training_gdp_advantage: 10
              army_training_development_advantage: 10
              person_traits: { min: 1, max: 3 }
              person_age: { min: 25, max: 70 }
              name_candidates: 5
            religion:
              count:
                - { max_countries: 8, min: 3, max: 4 }
                - { max_countries: 40, min: 4, max: 6 }
              aspects: { min: 2, max: 3 }
              dogmas: { min: 2, max: 4 }
            world:
              npc_extra:
                few: { min: 1, max: 3 }
                normal: { min: 2, max: 6 }
                many: { min: 4, max: 10 }
              provinces_per_country: { min: 60, max: 100, step: 5 }
              unclaimed_bp: { min: 500, max: 1500, step: 100 }
              provinces: { min: 400, max: 3500 }
            resources:
              count:
                - { max_provinces: 50, min: 1, max: 2 }
                - { max_provinces: 1000, min: 3, max: 5 }
            """;

    static final String MAP = """
            templates:
              - id: pangaea
                name: Пангея
                description: Один материк.
                weight: 20
                provinces_pct: 90
                land_pct: 40
                continents: { min: 1, max: 1 }
              - id: archipelago
                name: Архіпелаг
                description: Багато островів.
                weight: 15
                provinces_pct: 120
                land_pct: 30
                continents: { min: 5, max: 8 }
            grid:
              cell_size: 100
              aspect: { width: 2, height: 1 }
              relaxation: 2
            continents:
              size_weight: { min: 1, max: 4 }
              min_provinces: 20
              roughness: 50
              noise_cells: 6
            sea:
              min_cells: 8
              zone_cells: 30
            rivers:
              min_flow: 400
              min_cells: 2
            fertility:
              climates: { polar: 0, boreal: 20, temperate: 60, arid: 10, tropical: 40 }
              terrains: { plain: 5, hills: 0, mountains: -40, forest: -10, desert: -30, tundra: -20, swamp: -15 }
              moisture_pct: 30
              river: 15
            geography:
              coast:
                - id: inland
                  name: Без моря
                  description: Жодного берега.
                  min_pct: 0
                  tags: [inland]
                - id: seaside
                  name: Морська
                  description: Має берег.
                  min_pct: 1
                  tags: [seaside]
              terrains:
                - terrains: [hills, mountains]
                  min_pct: 40
                  tags: [highland]
            population:
              area_advantage: 25
              fertility_advantage: 50
              province_base: 12
              coast_bonus: 8
              levels:
                - id: few
                  name: Мало людей
                  description: Малолюдна.
                  population_k: 800
                  tier: fail
                  weight: 40
                  quality: 25
                  tags: [few_people]
                - id: many
                  name: Багато людей
                  description: Багатолюдна.
                  population_k: 40000
                  tier: success
                  weight: 60
                  quality: 75
            placement:
              min_provinces: 4
              roughness: 30
              noise_cells: 5
              areas:
                - id: small
                  name: Мала держава
                  description: Менша за сусідів.
                  share_pct: 60
                  weight: 30
                  quality: 30
                  tags: [tiny_land]
                - id: large
                  name: Велика держава
                  description: Більша за сусідів.
                  share_pct: 150
                  weight: 20
                  quality: 70
            relief:
              ridges: { min: 1, max: 3 }
              ridge_min_provinces: 40
              ridge_length_pct: 70
              ridge_wander: 30
              ridge_height: 70
              ridge_falloff: 20
              base_height: 20
              noise_amplitude: 25
              noise_cells: 4
              levels:
                - id: plain
                  name: Рівнина
                  description: Відкрита місцевість.
                  min_height: 0
                - id: hills
                  name: Пагорби
                  description: Горбиста місцевість.
                  min_height: 40
                - id: mountains
                  name: Гори
                  description: Гірські хребти.
                  min_height: 70
            climate:
              worlds:
                - id: cold
                  name: Холодний світ
                  description: Холодніше.
                  weight: 25
                  temperature_shift: -8
                - id: temperate
                  name: Помірний світ
                  description: Як на Землі.
                  weight: 50
                  temperature_shift: 0
              temperature: { equator: 90, pole: 0, height_cooling: 30, noise_amplitude: 10 }
              moisture: { coast: 80, inland_drying: 6, noise_amplitude: 30 }
              noise_cells: 8
              polar_below: 15
              boreal_below: 35
              tropical_from: 70
              arid_below: 30
              zones:
                - id: polar
                  name: Полярний
                  description: Мерзлота.
                - id: boreal
                  name: Бореальний
                  description: Тайга.
                - id: temperate
                  name: Помірний
                  description: Чотири сезони.
                - id: arid
                  name: Посушливий
                  description: Мало води.
                - id: tropical
                  name: Тропічний
                  description: Спека й зливи.
              covers:
                - id: tundra
                  name: Тундра
                  description: Мерзла рівнина.
                  climates: [polar]
                  reliefs: [plain, hills]
                  moisture: { min: 0, max: 100 }
                  height: { min: 0, max: 100 }
                - id: swamp
                  name: Болото
                  description: Драговина.
                  climates: [boreal, temperate, tropical]
                  reliefs: [plain]
                  moisture: { min: 80, max: 100 }
                  height: { min: 0, max: 30 }
                - id: desert
                  name: Пустеля
                  description: Пісок.
                  climates: [arid]
                  reliefs: [plain, hills]
                  moisture: { min: 0, max: 15 }
                  height: { min: 0, max: 100 }
                - id: forest
                  name: Ліс
                  description: Густий ліс.
                  climates: [boreal, temperate, tropical]
                  reliefs: [plain, hills]
                  moisture: { min: 55, max: 100 }
                  height: { min: 0, max: 100 }
            """;

    static final String RELIGIONS = """
            archetypes:
              - id: monotheism
                name: Монотеїзм
                description: Віра в єдиного бога.
                figure: Єдиний Бог
                figure_sexes: [male]
                weight: 20
                tags: [archetype_monotheism]
              - id: polytheism
                name: Політеїзм
                description: Віра в багатьох богів.
                figure: Верховне божество
                figure_sexes: [male, female]
                weight: 20
                tags: [archetype_polytheism]
            aspects:
              - id: war
                name: Війна
                description: Божество битв.
                weight: 100
                weight_tags: { archetype_polytheism: 30 }
                tags: [religion_war]
              - id: knowledge
                name: Знання
                description: Божество мудрості.
                weight: 100
                tags: [religion_knowledge]
            dogmas:
              - id: holy_war
                name: Священна війна
                description: Війна за віру — обов'язок.
                weight: 60
                weight_tags: { religion_war: 150 }
                modifiers:
                  - { target: "stat:war_weariness", value: -5 }
                tags: [dogma_holy_war]
                incompatible: [pacifism]
              - id: pacifism
                name: Ненасильство
                description: Проливати кров — гріх.
                weight: 60
                tags: [dogma_pacifism]
            polities:
              - id: single_church
                name: Єдина церква
                description: Одна ієрархія.
                weight: 100
                weight_tags: { archetype_monotheism: 100, dogma_holy_war: 10 }
                modifiers:
                  - { target: "stat:stability", value: 5 }
                tags: [polity_single_church]
              - id: communities
                name: Незалежні громади
                description: Кожна громада сама по собі.
                weight: 100
            faith_forms:
              - id: path
                gender: masculine
                figure_case: genitive
                archetypes: [monotheism, polytheism]
                forms:
                  nominative: "Шлях {figure}"
                  genitive: "Шляху {figure}"
                  dative: "Шляхові {figure}"
                  accusative: "Шлях {figure}"
                  instrumental: "Шляхом {figure}"
                  locative: "Шляху {figure}"
                  vocative: "Шляху {figure}"
            state_religion:
              religion_weight: 100
              secular:
                name: Світська держава
                description: Без державної релігії.
                weight: 50
                weight_tags: { democratic: 50 }
                excludes: [revanchism]
                tags: [secular]
            """;

    static final String STREAKS = """
            wheels:
              - id: golden_age
                name: Золота доба
                description: Усе вдається поспіль.
                tags: [golden_age, world_attention]
                rewards:
                  - id: national_pride
                    name: Національна гордість
                    description: Успіхи згуртували країну.
                    weight: 100
                    duration: 10
                    modifiers:
                      - { target: "stat:stability", value: 10 }
                  - id: great_figure
                    name: Видатна постать
                    description: Ще одна відома людина.
                    weight: 100
                    extra_people: 1
              - id: underdog
                name: Андердог
                description: Доля била державу знову й знову.
                tags: [underdog]
                rewards:
                  - id: second_chance
                    name: Другий шанс
                    description: Два жетони долі.
                    weight: 100
                    fate_tokens: 2
            """;

    private final TreeMap<String, String> files = new TreeMap<>();

    private Files() {
        files.put(ContentLoader.IDEOLOGIES, IDEOLOGIES);
        files.put(ContentLoader.DOCTRINES, DOCTRINES);
        files.put(ContentLoader.RESOURCES, RESOURCES);
        files.put(ContentLoader.DEVELOPMENT, DEVELOPMENT);
        files.put(ContentLoader.NUCLEAR, NUCLEAR);
        files.put(ContentLoader.GDP, GDP);
        files.put(ContentLoader.HDI, HDI);
        files.put(ContentLoader.ARMY, ARMY);
        files.put(ContentLoader.PEOPLE, PEOPLE);
        files.put(ContentLoader.NAMES, NAMES);
        files.put(ContentLoader.BACKSTORY, BACKSTORY);
        files.put(ContentLoader.STREAKS, STREAKS);
        files.put(ContentLoader.RELIGIONS, RELIGIONS);
        files.put(ContentLoader.MAP, MAP);
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
