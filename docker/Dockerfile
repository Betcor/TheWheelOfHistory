# syntax=docker/dockerfile:1
# Окремий (headless) сервер «Колеса Історії». Збирати з кореня репозиторію:
#   docker build -f docker/Dockerfile -t kolo-server .
# Запуск і перенесення світів — docker/README.md.

# Збірка: сервер разом із залежностями (installDist), без тестів — їх проганяє `./gradlew check` до образу.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --console=plain :server:installDist

# Запуск: лише JRE і зібраний сервер.
FROM eclipse-temurin:25-jre
ENV LANG=C.UTF-8 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
RUN groupadd --system kolo \
    && useradd --system --gid kolo --home-dir /data --no-create-home kolo \
    && mkdir -p /data/worlds \
    && chown -R kolo:kolo /data
COPY --from=build /src/server/build/install/server /opt/kolo
USER kolo
# Тека світів типово — ./worlds, тобто /data/worlds; том зберігає світи між перезапусками й оновленнями образу.
WORKDIR /data
VOLUME ["/data"]
EXPOSE 19700/tcp
# Скрипт запуску робить exec java: SIGTERM від `docker stop` доходить до JVM, сервер закриває сесії.
ENTRYPOINT ["/opt/kolo/bin/server"]
# UDP-пошук у локальній мережі в контейнер без --network host не доходить, тож типово вимкнений (ADR 0050).
# Власні аргументи замінюють цей рядок повністю.
CMD ["--no-discovery"]
