package kolo.client.map;

import java.util.Locale;

/** Режим карти (GD §22.2): що показує заливка провінцій. Решта режимів з'явиться разом зі своїми системами. */
public enum MapMode {
    /** Держави й нічийні землі. */
    POLITICAL,
    /** Тип місцевості. */
    TERRAIN,
    /** Кліматичні пояси. */
    CLIMATE,
    /** Родючість від безплідної до житниці. */
    FERTILITY;

    /** Ключ тексту: {@code map.mode.<ключ>}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
