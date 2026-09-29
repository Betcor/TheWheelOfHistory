package kolo.engine.content;

import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.state.Climate;

/**
 * Кліматичний пояс у контенті (GD §3.5): назва й опис. Пороги поясів — у {@link ClimateDef}.
 *
 * @param name назва українською
 * @param description опис для енциклопедії й підказок
 */
public record ClimateZoneDef(Climate climate, String name, String description) {

    public ClimateZoneDef {
        Objects.requireNonNull(climate, "climate");
        Checks.notBlank("climate." + climate.key() + ".name", name);
        Checks.notBlank("climate." + climate.key() + ".description", description);
    }
}
