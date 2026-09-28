package kolo.engine.wheel;

import java.util.Objects;

/**
 * Внесок одного джерела в перевагу колеса — рядок пояснення «+15 вишкіл армії».
 *
 * @param sourceId звідки взявся внесок (id модифікатора, будівлі, людини…)
 * @param descriptionKey ключ i18n тексту пояснення
 * @param value внесок у перевагу, може бути від'ємним
 */
public record AppliedModifier(String sourceId, String descriptionKey, int value) {

    public AppliedModifier {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(descriptionKey, "descriptionKey");
    }
}
