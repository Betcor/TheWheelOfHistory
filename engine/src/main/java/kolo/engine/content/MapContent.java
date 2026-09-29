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

/**
 * Контент карти (GD §3.3–3.5): шаблони, сітка комірок, материки, рельєф і клімат; родовища додадуться з генератором
 * карти.
 */
public final class MapContent {

    private final List<MapTemplateDef> templates;
    private final SortedMap<MapTemplateId, MapTemplateDef> templatesById;
    private final MapGridDef grid;
    private final ContinentsDef continents;
    private final ReliefDef relief;
    private final ClimateDef climate;

    /**
     * @param templates шаблони в порядку контенту (порядок секторів колеса шаблону)
     * @param grid числа сітки комірок Вороного
     * @param continents числа генерації материків
     * @param relief числа генерації рельєфу й рівні рельєфу
     * @param climate числа генерації клімату, пояси й покриви
     * @throws ValidationException якщо шаблонів немає або id повторюється
     */
    public MapContent(
            List<MapTemplateDef> templates,
            MapGridDef grid,
            ContinentsDef continents,
            ReliefDef relief,
            ClimateDef climate) {
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
        this.grid = Objects.requireNonNull(grid, "map_grid");
        this.continents = Objects.requireNonNull(continents, "continents");
        this.relief = Objects.requireNonNull(relief, "relief");
        this.climate = Objects.requireNonNull(climate, "climate");
    }

    /** Шаблони в порядку контенту — порядок секторів колеса шаблону. */
    public List<MapTemplateDef> templates() {
        return templates;
    }

    public Optional<MapTemplateDef> template(MapTemplateId id) {
        return Optional.ofNullable(templatesById.get(id));
    }

    public MapGridDef grid() {
        return grid;
    }

    public ContinentsDef continents() {
        return continents;
    }

    public ReliefDef relief() {
        return relief;
    }

    public ClimateDef climate() {
        return climate;
    }
}
