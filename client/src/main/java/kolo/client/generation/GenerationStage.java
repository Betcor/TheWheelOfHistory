package kolo.client.generation;

import java.util.Locale;

/** Етап показу генерації — один екран (GD §4.12), у порядку показу. */
public enum GenerationStage {
    /** Материк, площа, населення. */
    LAND,
    /** Ідеологія, підкласифікація, релігія. */
    REGIME,
    /** Галузі розвиненості, ВВП, ІЛР. */
    DEVELOPMENT,
    /** Розмір армії, вишкіл. */
    ARMY,
    /** Родовища, ядерний статус. */
    RESOURCES,
    /** Передісторія. */
    HISTORY,
    NAME,
    /** Відомі люди. */
    PEOPLE;

    /** Ключ тексту: {@code generation.stage.<ключ>}. */
    public String key() {
        // Locale.ROOT: інакше в турецькій локалі «I» перетворюється на «ı».
        return name().toLowerCase(Locale.ROOT);
    }
}
