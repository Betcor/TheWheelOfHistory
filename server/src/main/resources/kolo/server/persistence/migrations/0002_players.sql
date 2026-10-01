-- Гравці світу: хто якою державою грає. Токен гравця не зберігається — лише його хеш (SHA-256): з файлом світу
-- не можна видати себе за гравця. Нікнейми унікальні без огляду на регістр — це перевіряє сервер (NOCASE SQLite
-- знає лише латиницю).

CREATE TABLE players (
    id           INTEGER PRIMARY KEY CHECK (id >= 1),
    nickname     TEXT    NOT NULL,
    token_hash   TEXT    NOT NULL,
    country      INTEGER NOT NULL UNIQUE CHECK (country >= 0),
    is_host      INTEGER NOT NULL CHECK (is_host IN (0, 1)),
    last_seen_at TEXT    NOT NULL
);
