package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;

/**
 * Підкласифікація ідеології (GD §4.2): уточнення режиму, напр. {@code revanchism} у тоталітаризмі.
 *
 * @param name назва українською
 * @param weight вага в колесі підкласифікації, {@code 1..}{@value IdeologyDef#MAX_WEIGHT}; відносна — колесо
 *     нормалізує ваги підкласифікацій своєї ідеології
 * @param modifiers постійні модифікатори держави з цією підкласифікацією (додаються до модифікаторів ідеології)
 * @param tags мітки для подальших генерацій (передісторія, події)
 */
public record SubIdeologyDef(
        SubIdeologyId id, String name, int weight, List<ModifierDef> modifiers, List<String> tags) {

    public SubIdeologyDef {
        Objects.requireNonNull(id, "id");
        Checks.notBlank("sub_ideology." + id + ".name", name);
        Checks.inRange("sub_ideology." + id + ".weight", weight, 1, IdeologyDef.MAX_WEIGHT);
        modifiers = List.copyOf(modifiers);
        tags = Defs.tags("sub_ideology." + id + ".tags", tags);
    }
}
