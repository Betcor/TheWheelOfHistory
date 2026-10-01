# Архітектурні рішення (ADR)

Один рядок на рішення: номер, тема, де в коді. Новий ADR — `NNNN-назва-латиницею.md` і рядок у цій таблиці.

| ADR | Рішення | Де в коді |
|---|---|---|
| [0001](0001-infrastruktura-zbirky.md) | Інфраструктура збірки: Gradle, модулі, Spotless, тести | `build-logic`, `gradle/libs.versions.toml` |
| [0002](0002-model-kontentu-v-rushii.md) | Модель контенту в рушії, YAML-завантажувач окремо | `engine/content`, `content/loader` |
| [0003](0003-nazvy-z-vidminkamy.md) | Назви держав з відмінками, парадигми в контенті | `NounPhrase`, `CountryNames`, `names.yaml` |
| [0004](0004-peredistoriia-za-mitkamy.md) | Передісторія: фрагменти з умовами за мітками | `TagCondition`, `BackstoryFragmentDef` |
| [0005](0005-imena-liudei.md) | Імена людей у мовному стилі держави | `PersonNames`, `PersonNameStyleDef` |
| [0006](0006-balans-u-kontenti.md) | Загальні числа балансу — у `balance.yaml` | `BalanceDef` |
| [0007](0007-koleso-peredistorii.md) | Колесо передісторії | `BackstoryWheel` |
| [0008](0008-koleso-ladu.md) | Колеса ідеології й підкласифікації | `RegimeWheel` |
| [0009](0009-koleso-rozvynenosti.md) | Колесо розвиненості галузей | `DevelopmentWheel` |
| [0010](0010-koleso-yadernoho-statusu.md) | Колесо ядерного статусу й боєголовок | `NuclearWheel` |
| [0011](0011-koleso-vvp.md) | Колесо ВВП на душу | `GdpWheel`, `gdp.yaml` |
| [0012](0012-koleso-ilr.md) | Колесо ІЛР | `HdiWheel`, `hdi.yaml` |
| [0013](0013-koleso-rozmiru-armii.md) | Колесо розміру армії | `ArmySizeWheel`, `army.yaml` |
| [0014](0014-koleso-vyshkolu-armii.md) | Колесо вишколу армії | `ArmyTrainingWheel`, `Training` |
| [0015](0015-koleso-vidomykh-liudei.md) | Колесо відомих людей | `PeopleWheel`, `people.yaml` |
| [0016](0016-koleso-nazvy.md) | Колесо назви з готовими кандидатами | `NameWheel` |
| [0017](0017-stryky-heneratsii.md) | Стріки «Золота доба» / «Андердог» | `Streaks`, `StreakWheel`, `streaks.yaml` |
| [0018](0018-systema-podii-yak-dani.md) | Події як дані (запропоновано, етап 4а) | `docs/EVENTS.md`, `content/events/**` |
| [0019](0019-shablon-relihii.md) | Шаблон релігій у контенті | `ReligionContent`, `religions.yaml` |
| [0020](0020-heneratsiia-relihii-svitu.md) | Генерація релігій світу | `ReligionWheel`, `WorldReligionsWheel` |
| [0021](0021-koleso-relihii-derzhavy.md) | Колесо релігії держави / світська держава | `StateReligionWheel` |
| [0022](0022-lantsiuzhok-kolis-heneratsii.md) | Ланцюжок коліс генерації держави | `CountryGenerator`, `StartCountry` |
| [0023](0023-cli-heneratsii-derzhavy.md) | CLI генерації | `tools/sim`: `SimMain`, `CountryCommand` |
| [0024](0024-rozmir-svitu.md) | Розмір світу: NPC, шаблон карти, материки, провінції | `WorldSizeWheel`, `map.yaml` |
| [0025](0025-sitka-voronoho.md) | Сітка комірок Вороного | `VoronoiGrid`, `MapGrid` |
| [0026](0026-materyky.md) | Материки | `ContinentGenerator`, `ValueNoise` |
| [0027](0027-relief.md) | Рельєф: хребти + шум | `ReliefGenerator` |
| [0028](0028-klimat.md) | Клімат і покрив | `ClimateGenerator`, `ClimateDef` |
| [0029](0029-more-i-morski-zony.md) | Море, озера й морські зони | `SeaGenerator` |
| [0030](0030-richky.md) | Річки | `RiverGenerator` |
| [0031](0031-rodiuchist.md) | Родючість провінцій | `FertilityGenerator`, `FertilityDef` |
| [0032](0032-rodovyshcha.md) | Родовища ресурсів | `ResourceSuitabilityGenerator`, `ResourceWheel` |
| [0033](0033-rozmishchennia-derzhav.md) | Розміщення держав: материк, площа, територія | `PlacementGenerator` |
| [0034](0034-heohrafiia-i-naselennia.md) | Географія й населення держави | `Geography`, `PopulationWheel` |
| [0035](0035-lantsiuzhok-z-kartoiu.md) | Ланцюжок коліс з картою | `MapGenerator`, `CountryGenerationInput` |
| [0036](0036-sviati-tsentry-relihii.md) | Святі центри й генерація цілого світу | `HolyCenterWheel`, `WorldGenerator` |
| [0037](0037-biudzhet-syly.md) | Бюджет сили й коридори | `PowerBudget`, `PowerBudgetDef` |
| [0038](0038-rendir-karty-v-kliienti.md) | Представлення карти й рендер у клієнті | `engine/view`, `client/map` |
| [0039](0039-balans-heneratsii-derzhav.md) | Баланс генерації: стріки, уран, арсенали, коридори | контент (`balance.yaml`, `resources.yaml`, `nuclear.yaml`) |
| [0040](0040-stan-svitu.md) | Стан світу `WorldState` зі згенерованого світу | `engine/state`, `WorldStates` |
| [0041](0041-snapshoty-svitu.md) | Снапшоти: канонічний JSON і хеш стану | `server/persistence`: `StateSnapshot`, `MapSnapshot` |
| [0042](0042-fail-svitu-sqlite.md) | Файл світу SQLite і мігратор | `WorldStore`, `Migrator` |
| [0043](0043-protokol.md) | Протокол: повідомлення, JSON, фрейми | `protocol`: `MessageJson`, `Handshake`, `MapChunks` |
| [0044](0044-transport.md) | Транспорт: LocalChannel і TCP | `server/transport`, `client/net` |
| [0045](0045-aktor-sesii-i-porozhni-roky.md) | Актор сесії, фази року, порожні роки | `SessionActor`, `TurnPipeline` |
| [0046](0046-lobi-i-hravtsi.md) | Лобі, гравці, токени повернення | `SessionActor`, `PlayerTokens`, `LobbyScreen` |
| [0047](0047-zavantazhennia-svitu.md) | Завантаження світу, місця гравців | `WorldDirectory`, `TokenStore`, `LoadScreen` |
| [0048](0048-taimery-khodu.md) | Таймери ходу, «Завершити рік», автопілот | `TurnTimer`, `SessionActor`, `SessionClock`, `WorldStore.timer` |
