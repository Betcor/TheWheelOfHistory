-- Таймер ходу (GD §6.1): режим і тривалість фази наказів, які обрав хост, і межа фази наказів поточного року —
-- щоб після перезапуску сервера рік не отримував часу заново. Межа діє лише для свого року (deadline_turn).
-- Пропуски (GD §6.3): скільки років поспіль гравець не натиснув «Готово» й за нього діяв автопілот.

CREATE TABLE turn_timer (
    id            INTEGER PRIMARY KEY CHECK (id = 1),
    mode          TEXT    NOT NULL CHECK (mode IN ('manual', 'live', 'async')),
    seconds       INTEGER NOT NULL CHECK (seconds >= 0),
    deadline_turn INTEGER CHECK (deadline_turn >= 0),
    deadline_at   TEXT,
    CHECK ((deadline_turn IS NULL) = (deadline_at IS NULL))
);

INSERT INTO turn_timer (id, mode, seconds) VALUES (1, 'manual', 0);

ALTER TABLE players ADD COLUMN missed_turns INTEGER NOT NULL DEFAULT 0 CHECK (missed_turns >= 0);
