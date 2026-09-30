package kolo.engine.modifier;

/** Вид джерела модифікатора (GD §2.3). */
public enum SourceKind {
    IDEOLOGY,
    DOCTRINE,
    TECH,
    BUILDING,
    PERSON,
    BACKSTORY,
    /** Колесо стріку генерації: «Золота доба» чи «Андердог» (GD §4.10). */
    STREAK,
    /** Догмат чи устрій державної релігії (GD §25.2). */
    RELIGION,
    /** Коридор бюджету сили генерації (GD §4.11): зсув коліс держави, що вийшла за межі. */
    POWER_BUDGET,
    EVENT,
    GLOBAL_EVENT,
    TREATY,
    ERA
}
