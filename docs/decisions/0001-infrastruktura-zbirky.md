# 0001. Інфраструктура збірки

- **Статус:** прийнято
- **Дата:** 2026-09-28
- **Пункт 7 частково змінено:** [ADR 0002](0002-model-kontentu-v-rushii.md) — модель контенту в рушії

## Контекст

Початкова інфраструктура: багатомодульний Gradle-проєкт, спільні налаштування, тести,
форматування, CI. Тут — рішення, які виявилися неочевидними при ініціалізації.

## Рішення

1. **Gradle 9.8 + wrapper, Kotlin DSL, version catalog** (`gradle/libs.versions.toml`). Спільна
   конфігурація — convention-плагіни в `build-logic/` (included build):
   `kolo.java-conventions`, `kolo.java-library`, `kolo.java-application`.
2. **JDK 25 через toolchain** з автозавантаженням (`foojay-resolver-convention`). Gradle може
   працювати на будь-якому JDK 17+, компіляція й тести — завжди на 25. Додатково `--release 25`.
3. **`-Xlint:all -Werror`** — попередження компілятора ламають збірку з першого дня, поки їх нуль.
4. **JUnit 5.14, а не 6.x.** jqwik 1.10 і ArchUnit 1.5 зібрані під JUnit Platform 1.x;
   перехід — коли обидва офіційно підтримають Platform 6.
5. **AtlantaFX 2.1.0, а не 3.0.0.** 3.0.0 зібрана під JavaFX 27 і транзитивно піднімала JavaFX
   з 25 до 27. 2.1.0 зібрана під JavaFX 23 і працює з 25. Повернемося до 3.x разом із
   переходом на наступний LTS JavaFX.
6. **Configuration cache увімкнено**, але `:client:run` позначено несумісним: плагін
   `org.openjfx.javafxplugin` 0.1.0 звертається до `project` під час виконання.
7. **Межі модулів.** Більшість меж забезпечує сам граф залежностей Gradle. ArchUnit
   перевіряє те, чого Gradle не бачить: `engine` не використовує `kolo.content.loader`/`validation`,
   `content.model` не залежить від завантажувача й Jackson, `client` із `kolo.server` бачить лише
   `EmbeddedServer`. Правила дозволяють порожній набір класів (`allowEmptyShould`), доки модулі
   порожні.
8. **Плагіни оголошені в кореневому `build.gradle.kts` з `apply false`**, щоб усі модулі ділили
   один classloader: інакше спільний build service Spotless ламається в модулі `client`,
   де застосовано додатковий плагін.

## Наслідки

- Перший запуск завантажує JDK 25 (~200 МБ) у `~/.gradle/jdks`, якщо його немає.
- Оновлення JUnit, AtlantaFX і плагіна JavaFX треба перевіряти вручну за пунктами 4–6.
