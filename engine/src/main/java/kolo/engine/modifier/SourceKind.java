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
    EVENT,
    GLOBAL_EVENT,
    TREATY,
    ERA
}
