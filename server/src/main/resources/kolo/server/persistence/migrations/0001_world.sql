-- Файл світу: метадані, карта, роки й снапшоти стану. Стан не нормалізується — лише канонічні снапшоти (gzip).
-- Таблиці гравців, наказів, подій, коліс, пропозицій і чату з'являться новими міграціями разом зі своїми даними.

CREATE TABLE world_meta (
    id           INTEGER PRIMARY KEY CHECK (id = 1),
    name         TEXT    NOT NULL,
    seed         INTEGER NOT NULL,
    content_hash TEXT    NOT NULL,
    map_hash     TEXT    NOT NULL,
    created_at   TEXT    NOT NULL
);

-- Карта незмінна після генерації: пишеться один раз, снапшоти стану посилаються на її хеш.
CREATE TABLE world_map (
    id     INTEGER PRIMARY KEY CHECK (id = 1),
    map_gz BLOB    NOT NULL
);

CREATE TABLE turns (
    turn       INTEGER PRIMARY KEY CHECK (turn >= 0),
    state_hash TEXT    NOT NULL,
    saved_at   TEXT    NOT NULL
);

-- Снапшот є не для кожного року: проміжні роки можна відтворити реплеєм.
CREATE TABLE snapshots (
    turn     INTEGER PRIMARY KEY REFERENCES turns (turn),
    state_gz BLOB    NOT NULL
);
