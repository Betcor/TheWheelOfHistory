# Окремий сервер у Docker

Образ містить лише окремий (headless) сервер: без клієнта, без JDK — JRE 25 і зібраний сервер. Команди — з кореня
репозиторію.

## Запуск

```
docker compose -f docker/compose.yaml up -d --build   # зібрати й запустити
docker compose -f docker/compose.yaml logs -f         # журнал сервера
docker compose -f docker/compose.yaml down            # зупинити (світи лишаються в томі)
```

Гравці підключаються в клієнті через «Знайти за адресою»: `адреса-сервера:19700`. Пошук у локальній мережі в
контейнері вимкнено (`--no-discovery`): UDP-широкомовлення без `--network host` до контейнера не доходить.

Без compose:

```
docker build -f docker/Dockerfile -t kolo-server .
docker run -d --name kolo -p 19700:19700 -v kolo-worlds:/data --stop-timeout 90 kolo-server
```

## Налаштування

- **Аргументи сервера** (`--port N`, `--worlds DIR`, `--no-discovery`) — після імені образу або в `command:` у
  `compose.yaml`. Власні аргументи замінюють типові повністю: щоб пошук лишився вимкненим, додайте `--no-discovery`.
- **Пошук у локальній мережі:** з `--network host` (лише Linux) і аргументами без `--no-discovery`, наприклад
  `docker run --network host -v kolo-worlds:/data kolo-server --port 19700`.
- **Пам'ять:** `JAVA_OPTS` (типово `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError`) — частка пам'яті, яку
  контейнерові дозволено (`--memory`).
- **Зупинка:** `docker stop` надсилає SIGTERM — сервер закриває з'єднання й сесії. Рік записується у файл світу однією
  транзакцією, тож примусова зупинка втрачає щонайбільше накази поточного року; час на зупинку — `stop_grace_period`
  (у `compose.yaml` — 90 с).

## Світи

Світи — у томі `/data/worlds` (файли `*.koloworld`); том переживає перезапуск і оновлення образу.

Перенести світ з одиночної гри чи LAN (файл з `~/.kolo/worlds`, у Windows — `%APPDATA%\Kolo\worlds`) на сервер:

```
docker compose -f docker/compose.yaml cp world-1970.koloworld server:/data/worlds/
docker compose -f docker/compose.yaml exec -u root server chown kolo:kolo /data/worlds/world-1970.koloworld
```

Забрати назад — `cp server:/data/worlds/world-1970.koloworld .` (сесію світу перед тим краще закрити).

Замість тому можна змонтувати теку комп'ютера: `-v ./worlds:/data/worlds --user "$(id -u):$(id -g)"` — тоді файли
належать вашому користувачеві.
