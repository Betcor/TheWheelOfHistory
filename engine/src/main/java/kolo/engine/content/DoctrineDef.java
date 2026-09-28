package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;

/**
 * Військова доктрина — спеціалізація армії (GD §4.4).
 *
 * @param name назва українською
 * @param modifiers постійні модифікатори держави з цією доктриною
 * @param tags мітки для подальших генерацій і систем війни
 */
public record DoctrineDef(DoctrineId id, String name, List<ModifierDef> modifiers, List<String> tags) {

    public DoctrineDef {
        Objects.requireNonNull(id, "id");
        Checks.notBlank("doctrine." + id + ".name", name);
        modifiers = List.copyOf(modifiers);
        tags = Defs.tags("doctrine." + id + ".tags", tags);
    }
}
