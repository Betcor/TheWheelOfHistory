package kolo.server.session;

/** Стан сесії: {@code LOBBY → GENERATING → RUNNING ⇄ PAUSED → CLOSED}. */
public enum SessionState {
    /** Сесія створена, світу ще немає. */
    LOBBY,
    /** Сервер генерує світ і створює його файл. */
    GENERATING,
    /** Світ живе: роки йдуть фазами ({@link kolo.protocol.message.YearPhase}). */
    RUNNING,
    /** Рік не вдалося розв'язати чи зберегти: стан лишився на попередньому році, роки не йдуть. */
    PAUSED,
    /** Сесію закрито: файл світу закрито, потік зупинено. */
    CLOSED
}
