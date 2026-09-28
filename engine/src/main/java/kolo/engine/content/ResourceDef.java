package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;

/**
 * Природний ресурс, родовища якого бувають у провінціях (GD §4.5).
 *
 * @param name назва українською
 * @param tags мітки, напр. {@code energy}, {@code metal}
 */
public record ResourceDef(ResourceId id, String name, List<String> tags) {

    public ResourceDef {
        Objects.requireNonNull(id, "id");
        Checks.notBlank("resource." + id + ".name", name);
        tags = Defs.tags("resource." + id + ".tags", tags);
    }
}
