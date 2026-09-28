package kolo.engine.content;

import kolo.engine.error.Checks;
import kolo.engine.state.Development;

/**
 * Рівень технологічної розвиненості в галузі (GD §4.3).
 *
 * @param level {@link Development#MIN}..{@link Development#MAX}
 * @param name назва українською, напр. «Легке відставання»
 * @param description що означає рівень, для підказки гравцеві
 */
public record DevelopmentLevelDef(int level, String name, String description) {

    public DevelopmentLevelDef {
        Development.check("development_level.level", level);
        Checks.notBlank("development_level." + level + ".name", name);
        Checks.notBlank("development_level." + level + ".description", description);
    }
}
