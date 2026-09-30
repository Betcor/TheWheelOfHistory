package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import kolo.engine.error.Checks;

/**
 * Природний ресурс, родовища якого бувають у провінціях (GD §4.5).
 *
 * @param name назва українською
 * @param tags мітки, напр. {@code energy}, {@code metal}
 * @param deposits де трапляються родовища; порожньо — ресурс не буває на карті від генерації й з'являється лише
 *     іншими шляхами (торгівля, події)
 */
public record ResourceDef(ResourceId id, String name, List<String> tags, Optional<DepositDef> deposits) {

    public ResourceDef {
        Objects.requireNonNull(id, "id");
        Checks.notBlank("resource." + id + ".name", name);
        tags = Defs.tags("resource." + id + ".tags", tags);
        Objects.requireNonNull(deposits, "deposits");
    }

    /** Ресурс без родовищ від генерації. */
    public ResourceDef(ResourceId id, String name, List<String> tags) {
        this(id, name, tags, Optional.empty());
    }
}
