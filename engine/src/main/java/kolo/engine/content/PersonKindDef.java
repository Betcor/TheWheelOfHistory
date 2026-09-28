package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.state.PersonKind;

/**
 * Тип відомої постаті в контенті (GD §12.3).
 *
 * @param name назва українською, напр. «Генерал»
 * @param description роль постаті, для підказки гравцеві
 * @param tags мітки для генерації постатей і подій
 */
public record PersonKindDef(PersonKind kind, String name, String description, List<String> tags) {

    public PersonKindDef {
        Objects.requireNonNull(kind, "kind");
        Checks.notBlank("person_kind." + kind.key() + ".name", name);
        Checks.notBlank("person_kind." + kind.key() + ".description", description);
        tags = Defs.tags("person_kind." + kind.key() + ".tags", tags);
    }
}
