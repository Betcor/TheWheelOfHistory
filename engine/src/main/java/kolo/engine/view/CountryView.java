package kolo.engine.view;

import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.state.LocalizedName;

/**
 * Держава на карті.
 *
 * @param number номер держави на карті — порядок генерації, гравці першими
 * @param name назва з формами відмінків
 * @param player чи держава гравця
 * @param provinces скільки провінцій у держави
 */
public record CountryView(int number, LocalizedName name, boolean player, int provinces) {

    public CountryView {
        Checks.inRange("number", number, 0, Integer.MAX_VALUE);
        Objects.requireNonNull(name, "name");
        Checks.inRange("provinces", provinces, 1, Integer.MAX_VALUE);
    }
}
