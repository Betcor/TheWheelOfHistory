# Як писати події

Звід правил для авторів подій «Колеса Історії». Ігровий зміст подій — GAME_DESIGN §26–27 (`docs/design/26-events.md`, `27-extraordinary-events.md`), технічна архітектура —
ADR 0018.

> **Стан:** рушій подій ще не реалізовано. Формат нижче — узгоджений проєкт; поля й словники можуть
> уточнитися під час реалізації, і тоді цей документ оновиться першим. Колонка «Доступно» в таблицях показує,
> з якою системою з'являється кожна перевірка й ефект.

---

## 1. Де лежать події

```
content/src/main/resources/content/
├─ events/
│  ├─ internal/          # внутрішні події держав (щорічний пул)
│  ├─ global/            # глобальні події (колесо раз на 3–10 років)
│  ├─ religion/          # релігійні події, розколи, випробування
│  ├─ eras/              # події переходу епох
│  └─ extraordinary/     # надзвичайні: пекло, марсіани…
└─ templates/
   └─ countries/         # шаблони держав, яких створюють події
```

- Вкладеність каталогів довільна; гра читає всі `*.yaml` у `events/`.
- **Один файл — один сюжет або одна тема**: `extraordinary/hell_breach.yaml` містить знамення, прорив, закриття
  порталу й наслідки. Так ланцюг видно цілком.
- Файл — UTF-8, у корені ключ `events:` зі списком подій. Коментарі (`#`) — українською, пояснюють задум.

## 2. Анатомія події

```yaml
events:
  - id: machine_cult_awakening          # snake_case, унікальний на всю гру
    scope: country                      # world | country | province | religion
    trigger:
      pool: internal                    # §3
    conditions:                         # §4
      all:
        - sub_ideology: technocracy
        - religion_aspect: [knowledge, order]
        - development: { branch: energy_science, at_least: 1 }
        - not: { flag: machine_cult_decided }
    weight: 40                          # 1..10000, відносна
    weight_tags: { advanced: 20 }       # добавки до ваги за мітки області
    once: true                          # не більше разу на область
    visibility: public                  # public | involved | secret
    importance: 3                       # 1..3: чи анімувати колесо, чи виділяти в хроніці
    title: Машина як одкровення
    text: >
      Інженери {country.genitive} дедалі частіше говорять про обчислювальні машини мовою проповіді.
      Священники {religion.genitive} вимагають від уряду відповіді.
    choices:                            # §5; без choices ефекти застосовуються одразу
      - id: embrace
        text: Визнати машину проявом божества
        ai_weight: 60
        outcome: { wheel: machine_cult_path }
      - id: suppress
        text: Заборонити єресь
        ai_weight: 40
        default: true                   # якщо гравець не обрав
        effects:
          - add_modifier: { target: "stat:stability", value: -5, duration: 3 }
          - set_flag: machine_cult_decided
```

| Поле | Обов'язкове | Що означає |
|---|---|---|
| `id` | так | `snake_case`, унікальний. **Після випуску не змінювати й не видаляти** (§10) |
| `scope` | так | для кого подія: `world` — один раз на світ; `country` / `province` / `religion` — для кожної області окремо |
| `trigger` | так | як подія настає (§3) |
| `conditions` | ні | дерево умов (§4); без нього — завжди виконано |
| `weight` | для пулів | відносна вага в колесі пулу 1..10000 |
| `weight_tags` | ні | добавки до ваги за мітки області, можуть бути від'ємні; підсумок обрізається до 0..10000 |
| `once` | ні | `true` — щонайбільше раз на область; `once: world` — раз на весь світ |
| `cooldown` | ні | скільки років подія не може повторитися в тій самій області |
| `exclusive_with` | ні | список подій, з якими вона взаємовиключна (після однієї інші не настають) |
| `visibility` | ні | `public` (за замовчуванням), `involved` — лише учасникам, `secret` — видно з розвідкою «повний» |
| `importance` | ні | 1..3, за замовчуванням 1 |
| `extraordinary` | ні | `true` — надзвичайна подія (§8) |
| `title`, `text` | так | заголовок і текст (§7) |
| `choices` | ні | варіанти вибору (§5) |
| `outcome` | ні | колесо наслідків без вибору (§5.2) |
| `effects` | ні | ефекти, що настають одразу (§6) |
| `deprecated` | ні | `true` — подія більше не настає, але збереження з нею відкриваються |

## 3. Тригери: як подія настає

| Тригер | Приклад | Коли перевіряється |
|---|---|---|
| `pool: internal` | страйк, скандал | щороку 1–2 колеса внутрішніх подій кожної держави |
| `pool: global` | пандемія, нафтова криза | колесо глобальної події, коли настав її рік (§17) |
| `pool: religion` | диво, єресь | щорічне колесо релігійних подій держави з вірою |
| `on: <сигнал>` | `on: holy_center_destroyed` | одразу, коли система подала сигнал; область і змінні — від сигналу |
| `queued` | другий крок ланцюга | лише коли інша подія поставила її в чергу (`queue_event`) |
| `watch: { chance_bp: 30 }` | прибуття марсіан | щороку для кожної області з виконаними умовами — колесо шансу (30 bp = 0,3%) |

Сигнали (`on:`) і змінні, які вони дають:

| Сигнал | Змінні | Доступно |
|---|---|---|
| `war_declared` | `attacker`, `defender` | війна |
| `province_occupied` | `province`, `occupier`, `owner` | окупація |
| `capitulation` | `loser`, `winner` | окупація |
| `revolution` | `country`, `old_ideology`, `new_ideology` | внутрішня політика |
| `tech_researched` | `country`, `tech` | наука |
| `nuclear_strike` | `attacker`, `province` | ядерна зброя |
| `holy_center_captured` / `holy_center_destroyed` | `religion`, `province`, `actor` | релігії + війна |
| `person_died` | `person`, `country` | відомі люди |
| `era_changed` | `old_era`, `new_era` | епохи |
| `country_spawned` | `country`, `template` | події (§6) |

**Порада:** «рандомний момент часу» — це `watch` з малим `chance_bp`. Середнє очікування — `10000 / chance_bp`
років: 50 bp ≈ раз на 200 років, якщо умови виконано весь час.

## 4. Умови

Умова — дерево. Вузол — або логічна операція, або одна перевірка:

```yaml
conditions:
  all:                                  # усі
    - has_tag: revanchism
    - any:                              # хоча б одна
        - stat: { stability: { below: 30 } }
        - ideology: totalitarianism
    - not: { flag: portal_opened }      # заперечення
```

Список у корені (`conditions: [ … ]`) означає `all`.

### 4.1. Перевірки

| Перевірка | Приклад | Доступно |
|---|---|---|
| `has_tag` / `has_any_tag` / `lacks_tag` | `has_tag: lost_war` | зараз (мітки) |
| `stat` | `stat: { hdi: { at_least: 60 } }` (також `below`, `between: [a, b]`) | показники |
| `ideology` / `sub_ideology` | `sub_ideology: technocracy` (або список — будь-яка) | зараз |
| `development` | `development: { branch: military, at_least: 1 }` | зараз |
| `nuclear` | `nuclear: arsenal` | зараз |
| `religion` / `secular` | `religion: $religion` / `secular: true` | релігії |
| `religion_aspect` / `religion_dogma` | `religion_aspect: [war, death]` (будь-який зі списку) | релігії |
| `favor` | `favor: { at_least: 70 }` — прихильність бога | релігії |
| `has_tech` | `has_tech: radio_astronomy` | наука |
| `at_war` / `at_war_with` | `at_war_with: $attacker` | війна |
| `era` | `era: [cold_peace, detente]` | епохи |
| `year` / `turn` | `year: { at_least: 1985 }` | зараз |
| `flag` / `world_flag` | `world_flag: portal_opened` | рушій подій |
| `counter` / `world_counter` | `world_counter: { martian_presence: { at_least: 2 } }` | рушій подій |
| `event_fired` | `event_fired: { id: hell_breach, within_years: 20 }` (область — світ або ця ж) | рушій подій |
| `is_player` / `is_npc` | `is_player: true` | мережа й сесії |
| `chance_bp` | лише в `watch` тригера | рушій подій |

### 4.2. Інші області в умовах

Перевірки застосовуються до області події. Щоб перевірити іншу — квантори й змінні:

```yaml
- any_country:                          # хоча б одна держава світу відповідає умові
    has_tech: radio_astronomy
- count_countries:                      # кількість держав
    where: { nuclear: arsenal }
    at_least: 3
- in: $religion.holy_center.owner                # перевірки для власника святого центру
    ideology: theocracy
```

Квантори: `any_country`, `all_countries`, `count_countries`, `any_neighbor`, `any_province` (провінції області),
`any_religion`. Змінні — §6.2.

## 5. Вибір і колеса наслідків

### 5.1. Варіанти

```yaml
choices:
  - id: seal_portal
    text: Кинути армію на портал
    requires: { stat: { stability: { at_least: 40 } } }   # умова доступності варіанта
    cost: { treasury: 500, fate_tokens: 0 }                 # ціна (необов'язково)
    ai_weight: 30                                           # вага для NPC і автопілоту
    outcome: { wheel: seal_portal_attempt }
  - id: pray
    text: Молитися
    ai_weight: 70
    default: true
    effects: [ { change_favor: 10 } ]
```

- Варіантів — від 2 до 5. **Рівно один** має `default: true`, і він мусить бути доступний завжди (без `requires`
  і `cost`): його обирає автопілот, якщо гравець не відповів.
- `ai_weight` — обов'язковий: NPC обирають варіант колесом за цими вагами серед доступних.
- Вибір гравець робить у фазі наказів **наступного року**, наслідки — під час розрахунку того ж наступного
  року (GD §26.4). Пишіть текст так, щоб затримка на рік не виглядала дивно.

### 5.2. Колеса наслідків

Колеса описуються в тому ж файлі під ключем `wheels:` і використовуються через `outcome: { wheel: <id> }`:

```yaml
wheels:
  - id: seal_portal_attempt
    advantage:                          # звідки перевага (GD §2.3); обрізається до −100..100
      - { stat: military_power, per: 10, value: 5 }   # +5 за кожні 10 одиниць
      - { has_tag: holy_warriors, value: 15 }
    sectors:
      - { id: sealed,   tier: CRIT_SUCCESS, weight: 5,  quality: 95, effects: [ … ] }
      - { id: pushed,   tier: SUCCESS,      weight: 25, quality: 70, effects: [ … ] }
      - { id: held,     tier: PARTIAL,      weight: 35, quality: 50, effects: [ … ] }
      - { id: repelled, tier: FAIL,         weight: 25, quality: 25, effects: [ … ] }
      - { id: massacre, tier: CRIT_FAIL,    weight: 10, quality: 5,  effects: [ … ] }
```

- Ваги — відносні, колесо нормалізує їх до 10 000 bp; **КП і КУ завжди ≥ 1%** (GD §2.3) — рушій дотягне, але
  краще не ставити їм 0.
- `tier` визначає, як перевага змінює сектор; `quality` — для хроніки й стріків.
- Сектори без рівнів (`PARTIAL` у всіх) — «чистий жереб», перевага на них не діє.

## 6. Ефекти

Ефекти — список, виконуються по черзі. Ефект, який не може виконатися (держави вже немає), пропускається і
записується у звіт, а не ламає рік.

### 6.1. Словник

| Ефект | Приклад | Доступно |
|---|---|---|
| `add_tag` / `remove_tag` | `add_tag: machine_cult` | рушій подій |
| `set_flag` / `clear_flag` (області) | `set_flag: machine_cult_decided` | рушій подій |
| `set_world_flag` / `clear_world_flag` | `set_world_flag: portal_opened` | рушій подій |
| `change_counter` / `change_world_counter` | `change_world_counter: { martian_presence: 1 }` | рушій подій |
| `add_modifier` | `add_modifier: { target: "stat:stability", value: -10, duration: 5 }` (0 — постійно) | рушій подій |
| `change_stat` | `change_stat: { stability: -15 }` — разова зміна бази | економіка |
| `fate_tokens` | `fate_tokens: 1` (ліміт 3 діє) | рушій подій |
| `queue_event` | `queue_event: { id: hell_breach_omen_2, delay: { from: 1, to: 3 }, scope: $religion }` | рушій подій |
| `trigger_event` | `trigger_event: { id: crusade_call, scope: $religion.holy_center.owner }` — цього ж року, глибина ≤ 3 | рушій подій |
| `spawn_person` / `kill_person` | `spawn_person: { kind: prophet, traits: [fanatic] }` | відомі люди |
| `change_ideology` / `revolution` | `revolution: {}` — колесо нової ідеології | внутрішня політика |
| `set_religion` / `make_secular` | `set_religion: $new_religion` | релігії |
| `change_favor` | `change_favor: -20` | релігії |
| `create_religion` | `create_religion: { schism_of: $religion, change: [aspect], save_as: new_religion }` | релігії |
| `grant_gift` | `grant_gift: blessing_of_harvest` — дар бога без колеса | релігії |
| `unlock_tech_branch` / `grant_tech` | `unlock_tech_branch: { branch: martian, for: $country }` | наука |
| `casus_belli` | `casus_belli: { holder: all_countries, against: $legion }` | дипломатія |
| `change_reputation` | `change_reputation: { of: $actor, in_eyes_of: all_countries, value: -30 }` | дипломатія |
| `declare_war` | `declare_war: { attacker: $legion, defender: $religion.holy_center.owner }` | війна |
| `transfer_provinces` | `transfer_provinces: { from: any, to: $martians, count: { from: 2, to: 4 }, prefer: unclaimed }` | окупація |
| `spawn_country` | `spawn_country: { template: infernal_legion, at: $religion.holy_center, save_as: legion }` | події + карта + війна |
| `spawn_army` | `spawn_army: { owner: $legion, at: $religion.holy_center, strength: 50000, training: 5 }` | військо |
| `set_era` | `set_era: first_contact` | епохи |
| `global_modifier` | `global_modifier: { target: "stat:stability", value: -5, duration: 3 }` | рушій подій |
| `every_country` / `every_province` | `every_country: { where: { religion: $religion }, effects: [ … ] }` | рушій подій |

### 6.2. Змінні

- Змінні пишуться з `$`: `$country`, `$religion`, `$province`, `$attacker`.
- Звідки беруться: область події (`$country` / `$province` / `$religion`), змінні сигналу (§3), `save_as` у
  ефектах, що створюють сутність.
- Змінні передаються далі ланцюгом через `queue_event` / `trigger_event` (усі, що були на момент виклику).
- У властивості змінної — через крапку: `$province.owner`, `$country.capital`, `$religion.holy_center`.
- Посилання на змінну, якої в цьому місці ланцюга немає, — помилка завантаження.

## 7. Тексти

Правила ті самі, що в передісторії (`backstory.yaml`):

- Змінні в тексті — у фігурних дужках **без `$`**: `{country}` — коротка назва, `{country_full}` — повна,
  відмінок через крапку: `{country.genitive}`, `{religion.instrumental}`. Без відмінка — називний.
- Відмінки: `nominative`, `genitive`, `dative`, `accusative`, `instrumental`, `locative`, `vocative`.
- Рід назви шаблон не знає: підмет речення — «країна», «віряни», «уряд», а назва стоїть у непрямих відмінках.
- Уникайте прийменника «в/у» перед назвою («в Віланорії») — перебудуйте речення.
- `title` — до 60 символів; `text` — 1–4 речення; текст варіанта — до 60 символів, дієслово в інфінітиві.
- Текст пишеться як запис хроніки: минулий час, без звертання до гравця («ви»).
- Не прив'язуйте текст до конкретних реальних країн, релігій чи людей — лише до згенерованих.

## 8. Надзвичайні події

Позначаються `extraordinary: true` і лежать в `events/extraordinary/`. Додаткові правила (GD §27):

1. **Рідкість.** Лише тригери `watch` (з `chance_bp` ≤ 50) або `on:` з додатковим колесом. Рушій сам дотримується
   лімітів балансу (не раніше N-го року, не частіше ніж раз на M років, одна надзвичайна сила одночасно) — не
   дублюйте їх в умовах.
2. **Знамення.** Перед появою надзвичайної сили — хоча б одна подія-попередження через `queue_event` із затримкою
   1–5 років. Гравці мають встигнути підготуватися.
3. **Опір.** Разом із загрозою сюжет дає відповідь: гілку технологій, casus belli, прапорець для ООН, подію
   «закриття» загрози. Лінтер попередить, якщо `spawn_country` у сюжеті немає події з `on: capitulation` чи
   іншим завершенням.
4. **Прозорість.** Сила надзвичайної держави — лише в її шаблоні (`templates/countries/`), не в прихованих
   модифікаторах.
5. **Слід у світі.** Навіть після поразки загрози щось лишається: течія, технологія, мітка світу.

### Шаблон держави

```yaml
# templates/countries/infernal_legion.yaml
id: infernal_legion
name: { root: азмор, form: legion }       # або готова назва з відмінками
ideology: infernal                         # особлива ідеологія, недоступна в генерації
control: npc
personality: { aggression: 100, trade: 0, caution: 0 }
stats: { stability: 100, legitimacy: 50 }
tech_branches: [infernal]
army: { strength: 200000, training: 5 }
tags: [extraordinary, infernal]
```

## 9. Як будувати нелінійні сюжети

| Прийом | Як |
|---|---|
| **Знамення → подія** | подія A (`watch`) → `queue_event: B, delay 1–5` → B показує наслідок |
| **Розвилка** | варіанти вибору або сектори колеса ставлять різні прапорці чи ставлять у чергу різні події |
| **Наростання** | лічильник `change_world_counter: { x: 1 }` + події з умовою `world_counter: { x: { at_least: N } }` |
| **Пам'ять** | прапорець області читають події через роки: «ті, хто колись придушив Культ Машини…» |
| **Реакція світу** | `every_country` з `where` — одна подія зачіпає всіх, хто відповідає умові |
| **Взаємовиключність** | `exclusive_with` або спільний прапорець, який кожна гілка ставить і перевіряє |
| **Перехресні сюжети** | мітки: одна подія ставить мітку, будь-яка інша подія з іншого файлу її читає |

Прапорці й лічильники називайте з префіксом сюжету: `hell_portal_open`, `martian_presence` — щоб не зіткнутися
з іншим файлом.

## 10. Правила, які не можна порушувати

1. **Id не змінюються й не видаляються** після того, як подія потрапила в гру — лише `deprecated: true`.
   Збереження посилаються на id.
2. Жодних чисел з комою: лише цілі; шанси — у bp (1% = 100).
3. Кожна подія з `choices` має рівно один `default` без умов і ціни.
4. Кожна подія має бути досяжною: або пул/`watch`/`on:`, або на неї хтось ставить `queue_event`.
5. Кожен прапорець, який ставлять, хтось читає (інакше він зайвий), і навпаки.
6. Числа балансу, спільні для багатьох подій (ліміти надзвичайних подій, частота пулів), — у `balance.yaml`,
   не в подіях.
7. Не ламати гру компанії: подія, що може знищити державу гравця, мусить мати знамення або вибір.

## 11. Перевірка

```
./gradlew check                                   # завантаження контенту з усіма перевірками
./gradlew :tools:sim:run --args="events --check"  # лінтер: досяжність, прапорці, змінні
./gradlew :tools:sim:run --args="events --simulate 200 --runs 100"   # частота подій за 200 років
```

Типові помилки завантаження:

| Код | Причина |
|---|---|
| `CONTENT_MALFORMED` | помилка YAML, невідоме поле, дублікат ключа |
| `INVALID_CONTENT` | значення поза межами (вага 0, `chance_bp` > 10000) |
| `MISSING_DEFINITION` | посилання на неіснуючу подію, колесо, шаблон, технологію |
| `INVALID_TEMPLATE` | помилка в тексті: невідома змінна чи відмінок |
| `SELF_REFERENCE` | подія ставить у чергу саму себе без затримки |

Подробиці помилки називають файл і місце в ньому (`events/extraordinary/hell_breach.yaml`, `events[2].choices[0]`).

## 12. Приклад: прорив пекла

```yaml
# events/extraordinary/hell_breach.yaml
# Сюжет: руйнування святого центру → знамення → прорив → легіон → закриття порталу.

events:
  - id: hell_breach_tremor
    scope: religion
    extraordinary: true
    trigger: { on: holy_center_destroyed }
    conditions:
      - not: { world_flag: hell_portal_open }
    title: Земля тремтить
    text: >
      Над руїнами святині {religion.genitive} третю ніч поспіль стоїть червоне світло. Віряни кажуть, що
      руйнівники розбудили те, що спало під храмом.
    outcome: { wheel: hell_breach_fate }

  - id: hell_breach_opens
    scope: religion
    extraordinary: true
    trigger: queued
    importance: 3
    title: Прорив
    text: >
      Земля над святинею {religion.genitive} розкололася. З розлому вийшло військо, якого не знав жоден
      генеральний штаб.
    effects:
      - set_world_flag: hell_portal_open
      - spawn_country: { template: infernal_legion, at: $province, save_as: legion }
      - casus_belli: { holder: all_countries, against: $legion }
      - unlock_tech_branch: { branch: anti_infernal, for: all_countries }
      - create_religion: { schism_of: $religion, change: [aspect], add_aspect: war, save_as: militant_order }

wheels:
  - id: hell_breach_fate
    sectors:
      - { id: calm,  tier: PARTIAL, weight: 70, quality: 50 }
      - { id: omen,  tier: PARTIAL, weight: 30, quality: 30,
          effects: [ { queue_event: { id: hell_breach_opens, delay: { from: 1, to: 3 } } } ] }

# Далі в цьому ж файлі — закриття порталу (on: province_occupied, коли провінцію $province забирають у легіону)
# і подія «Слід прориву», що лишає світові мітку й течію навіть після поразки легіону.
```

## 13. Чек-лист перед тим, як додати подію

- [ ] `id` унікальний, `snake_case`, з префіксом сюжету.
- [ ] Подія досяжна; ймовірність розумна (перевірено симуляцією).
- [ ] Умови не надто широкі: подія не випадає кожні кілька років у кожної держави.
- [ ] Є `default` серед варіантів; `ai_weight` у кожного.
- [ ] Тексти з відмінками, без «в/у» перед назвами, минулий час.
- [ ] Прапорці й лічильники з префіксом сюжету; кожен ставлять і читають.
- [ ] Для надзвичайної події: знамення, опір, завершення, слід у світі.
- [ ] `./gradlew check` і лінтер зелені.
