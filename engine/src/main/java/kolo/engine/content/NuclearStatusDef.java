package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.state.NuclearStatus;

/**
 * Ядерний статус у контенті (GD §4.6) і його сектор у колесі ядерного статусу.
 *
 * @param name назва українською
 * @param weight базова вага в колесі ядерного статусу, {@code 1..}{@value #MAX_WEIGHT}; відносна — колесо
 *     нормалізує ваги всіх статусів, а перевага зсуває їх
 * @param quality наскільки статус добрий для держави, {@code 0..100} (стріки генерації)
 * @param tags мітки держави з цим статусом, напр. {@code nuclear_power} для арсеналу
 */
public record NuclearStatusDef(NuclearStatus status, String name, int weight, int quality, List<String> tags) {

    /** Найбільша відносна вага статусу в колесі ядерного статусу. */
    public static final int MAX_WEIGHT = 10_000;

    public NuclearStatusDef {
        Objects.requireNonNull(status, "status");
        Checks.notBlank("nuclear_status." + status.key() + ".name", name);
        Checks.inRange("nuclear_status." + status.key() + ".weight", weight, 1, MAX_WEIGHT);
        Checks.inRange("nuclear_status." + status.key() + ".quality", quality, 0, 100);
        tags = Defs.tags("nuclear_status." + status.key() + ".tags", tags);
    }
}
