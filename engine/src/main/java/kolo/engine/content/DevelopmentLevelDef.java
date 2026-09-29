package kolo.engine.content;

import java.util.List;
import kolo.engine.error.Checks;
import kolo.engine.state.Development;

/**
 * Рівень технологічної розвиненості в галузі (GD §4.3) і його сектор у колесі розвиненості.
 *
 * @param level {@link Development#MIN}..{@link Development#MAX}
 * @param name назва українською, напр. «Легке відставання»
 * @param description що означає рівень, для підказки гравцеві
 * @param weight базова вага в колесі розвиненості, {@code 1..}{@value #MAX_WEIGHT}; відносна — колесо нормалізує
 *     ваги всіх рівнів, а перевага ладу зсуває їх
 * @param quality наскільки рівень добрий для держави, {@code 0..100} (стріки генерації)
 * @param tags мітки, які рівень дає державі (напр. {@code backward}), — вхід для наступних коліс
 */
public record DevelopmentLevelDef(
        int level, String name, String description, int weight, int quality, List<String> tags) {

    /** Найбільша відносна вага рівня в колесі розвиненості. */
    public static final int MAX_WEIGHT = 10_000;

    public DevelopmentLevelDef {
        Development.check("development_level.level", level);
        Checks.notBlank("development_level." + level + ".name", name);
        Checks.notBlank("development_level." + level + ".description", description);
        Checks.inRange("development_level." + level + ".weight", weight, 1, MAX_WEIGHT);
        Checks.inRange("development_level." + level + ".quality", quality, 0, 100);
        tags = Defs.tags("development_level." + level + ".tags", tags);
    }
}
