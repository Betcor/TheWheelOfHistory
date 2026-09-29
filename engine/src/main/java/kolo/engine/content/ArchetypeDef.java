package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;

/**
 * Архетип релігії (GD §25.1, колесо 1): монотеїзм, політеїзм, дуалізм… Задає форму назви віри, а через свої мітки —
 * ваги аспектів, догматів і устрою.
 *
 * @param name назва українською, напр. «Монотеїзм»
 * @param description що це за віра, для підказки гравцеві
 * @param figure роль постаті, чиїм ім'ям зветься віра, напр. «Єдиний Бог» чи «Засновник»; саме ім'я дає генератор
 * @param weight вага в колесі архетипу, {@code 1..}{@value #MAX_WEIGHT}; відносна — колесо нормалізує ваги
 * @param tags мітки релігії з цим архетипом, напр. {@code archetype_dualism}
 */
public record ArchetypeDef(
        ArchetypeId id, String name, String description, String figure, int weight, List<String> tags) {

    /** Найбільша відносна вага архетипу. */
    public static final int MAX_WEIGHT = 10_000;

    public ArchetypeDef {
        Objects.requireNonNull(id, "id");
        String field = "archetype." + id;
        Checks.notBlank(field + ".name", name);
        Checks.notBlank(field + ".description", description);
        Checks.notBlank(field + ".figure", figure);
        Checks.inRange(field + ".weight", weight, 1, MAX_WEIGHT);
        tags = Defs.tags(field + ".tags", tags);
    }
}
