package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.state.NuclearStatus;

/**
 * Ядерний статус у контенті (GD §4.6).
 *
 * @param name назва українською
 * @param tags мітки держави з цим статусом, напр. {@code nuclear_power} для арсеналу
 */
public record NuclearStatusDef(NuclearStatus status, String name, List<String> tags) {

    public NuclearStatusDef {
        Objects.requireNonNull(status, "status");
        Checks.notBlank("nuclear_status." + status.key() + ".name", name);
        tags = Defs.tags("nuclear_status." + status.key() + ".tags", tags);
    }
}
