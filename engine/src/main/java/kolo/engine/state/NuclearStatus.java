package kolo.engine.state;

import java.util.Locale;

/** Ядерний статус держави (GD §4.6, §9.4). */
public enum NuclearStatus {
    /** Ядерної зброї немає. */
    NONE,
    /** Проєкт ядерної зброї частково виконаний. */
    PROGRAM,
    /** Є боєголовки. */
    ARSENAL;

    /** Ключ у контенті, напр. {@code arsenal}. */
    public String key() {
        // Locale.ROOT: інакше в турецькій локалі «I» перетворюється на «ı».
        return name().toLowerCase(Locale.ROOT);
    }
}
