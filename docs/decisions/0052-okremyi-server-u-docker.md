# 0052. Окремий сервер у Docker

- **Статус:** прийнято
- **Дата:** 2026-10-01

## Контекст

Етап 4: окремий сервер — headless і в Docker (GD §21: «окремий сервер, наприклад VPS; світ живе, навіть коли хост не в
мережі»). Сам сервер уже є (`DedicatedServerMain`, ADR 0044, 0050): аргументи `--port`, `--worlds`, `--no-discovery`,
зупинка сигналом процесу. Бракувало образу й способу тримати світи поза контейнером.

## Рішення

- **Тека `docker/`:** `Dockerfile`, `Dockerfile.dockerignore`, `compose.yaml`, `README.md` (запуск, налаштування,
  перенесення світів). Контекст збірки — корінь репозиторію.
- **Багатоетапна збірка в образі:** етап `eclipse-temurin:25-jdk` виконує `./gradlew :server:installDist` (без тестів —
  це справа `check`; кеш Gradle — BuildKit `--mount=type=cache`), етап запуску `eclipse-temurin:25-jre` отримує лише
  `server/build/install/server` у `/opt/kolo`. Образ збирається без JDK на комп'ютері. Не Alpine: нативна бібліотека
  sqlite-jdbc і glibc-образ temurin — перевірений шлях.
- **Allowlist контексту** (`Dockerfile.dockerignore` поруч із Dockerfile, корінь репозиторію не засмічується): лише
  Gradle-файли й модулі; `build/`, `.gradle/` і файли світів — ні. Модулі `client` і `tools` потрібні, бо їх перелічує
  `settings.gradle.kts`, але не збираються.
- **Світи — у томі `/data`:** `WORKDIR /data`, тож типова тека `worlds` — це `/data/worlds`; аргумент `--worlds` не
  потрібен. Сервер працює від користувача `kolo` (не root); тека тому належить йому.
- **Аргументи:** `ENTRYPOINT` — скрипт `installDist` (`exec java`, тож SIGTERM від `docker stop` доходить до JVM і
  спрацьовує `kolo-shutdown`), `CMD ["--no-discovery"]` — у контейнері без host-мережі UDP-пошук марний (ADR 0050).
  Власні аргументи замінюють `CMD` цілком — так задокументовано.
- **Зупинка:** `GameServer.close` дає сесіям до 30 с на крок, тож у `compose.yaml` `stop_grace_period: 90s` замість
  типових 10 с. Рік пишеться однією транзакцією (ADR 0042), тож `SIGKILL` втрачає щонайбільше накази поточного року.
- **JVM:** `JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"` — частка пам'яті контейнера й перезапуск
  замість напівмертвого процесу. `--enable-native-access=ALL-UNNAMED` — у `applicationDefaultJvmArgs` модуля `server`
  (усі дистрибутиви, не лише Docker): sqlite-jdbc завантажує нативну бібліотеку.
- **Без `HEALTHCHECK`:** перевірка TCP-з'єднанням створювала б порожні розмови в журналі сервера, а порт може бути
  іншим; живість видно з `docker ps` і журналу. Окрема перевірка — коли знадобиться (наприклад, команда сервера).

## Наслідки

- Тест `DockerfileTest` (без Docker) тримає образ узгодженим із кодом: `EXPOSE` і порт у `compose.yaml` =
  `Protocol.DEFAULT_PORT`, точка входу — скрипт `installDist`, типові `CMD` розбираються `DedicatedServerMain.options`
  (порт за замовчуванням, пошук вимкнено, світи в `/data/worlds`). Тека `docker/` — вхід задачі `:server:test`.
- Збірку й запуск образу `check` не перевіряє (Docker є не всюди); перевірено вручну: образ збирається, клієнт по TCP
  створює світ і проводить рік, файл — у томі й переживає новий контейнер, `docker stop` завершує сервер одразу.
- Образ ≈ 0,5 ГБ (JRE на Ubuntu + нативні бібліотеки sqlite-jdbc для всіх платформ). Зменшити — `jlink` чи
  distroless, якщо стане важливо.
- Файл світу переноситься між режимами копіюванням у том (`docker compose cp` + `chown`), див. `docker/README.md`.
- TLS не додано (pitfalls §14: опційний) — за потреби через зворотний проксі.
