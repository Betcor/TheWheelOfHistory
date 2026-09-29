package kolo.engine.content;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/** Контент карти (GD §3.3–3.5): поки лише шаблони; рельєф, клімат і родовища додадуться з генератором карти. */
public final class MapContent {

    private final List<MapTemplateDef> templates;
    private final SortedMap<MapTemplateId, MapTemplateDef> templatesById;

    /**
     * @param templates шаблони в порядку контенту (порядок секторів колеса шаблону)
     * @throws ValidationException якщо шаблонів немає або id повторюється
     */
    public MapContent(List<MapTemplateDef> templates) {
        if (templates.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", "map_templates"));
        }
        TreeMap<MapTemplateId, MapTemplateDef> byId = new TreeMap<>();
        for (MapTemplateDef template : templates) {
            Objects.requireNonNull(template, "map_template");
            if (byId.putIfAbsent(template.id(), template) != null) {
                throw new ValidationException(
                        ErrorCode.DUPLICATE_ID, ErrorDetails.of("field", "map_template.id", "value", template.id()));
            }
        }
        this.templates = List.copyOf(templates);
        this.templatesById = Collections.unmodifiableSortedMap(byId);
    }

    /** Шаблони в порядку контенту — порядок секторів колеса шаблону. */
    public List<MapTemplateDef> templates() {
        return templates;
    }

    public Optional<MapTemplateDef> template(MapTemplateId id) {
        return Optional.ofNullable(templatesById.get(id));
    }
}
