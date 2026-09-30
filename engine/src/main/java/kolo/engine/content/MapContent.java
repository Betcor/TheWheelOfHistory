package kolo.engine.content;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;

/**
 * Контент карти (GD §3.3–3.6): шаблони, сітка комірок, материки, рельєф, клімат, море, річки, родючість, розміщення
 * держав і те, що держава бере з території, — географія й населення (колеса 1–4 GD §4.1). Придатність до родовищ — у
 * ресурсах ({@link ResourceDef#deposits()}).
 */
public final class MapContent {

    private final List<MapTemplateDef> templates;
    private final SortedMap<MapTemplateId, MapTemplateDef> templatesById;
    private final MapGridDef grid;
    private final ContinentsDef continents;
    private final ReliefDef relief;
    private final ClimateDef climate;
    private final SeaDef sea;
    private final RiverDef rivers;
    private final FertilityDef fertility;
    private final PlacementDef placement;
    private final GeographyDef geography;
    private final PopulationDef population;

    /**
     * @param templates шаблони в порядку контенту (порядок секторів колеса шаблону)
     * @param grid числа сітки комірок Вороного
     * @param continents числа генерації материків
     * @param relief числа генерації рельєфу й рівні рельєфу
     * @param climate числа генерації клімату, пояси й покриви
     * @param sea числа генерації моря й морських зон
     * @param rivers числа генерації річок
     * @param fertility таблиця родючості провінцій
     * @param placement рівні площі й числа розміщення держав
     * @param geography рівні виходу до моря й мітки переважної місцевості держави
     * @param population рівні колеса населення й розподіл населення по провінціях
     * @throws ValidationException якщо шаблонів немає або id повторюється
     */
    public MapContent(
            List<MapTemplateDef> templates,
            MapGridDef grid,
            ContinentsDef continents,
            ReliefDef relief,
            ClimateDef climate,
            SeaDef sea,
            RiverDef rivers,
            FertilityDef fertility,
            PlacementDef placement,
            GeographyDef geography,
            PopulationDef population) {
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
        this.sea = Objects.requireNonNull(sea, "sea");
        this.rivers = Objects.requireNonNull(rivers, "rivers");
        this.fertility = Objects.requireNonNull(fertility, "fertility");
        this.placement = Objects.requireNonNull(placement, "placement");
        this.geography = Objects.requireNonNull(geography, "geography");
        this.population = Objects.requireNonNull(population, "population");
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

    public SeaDef sea() {
        return sea;
    }

    public RiverDef rivers() {
        return rivers;
    }

    public FertilityDef fertility() {
        return fertility;
    }

    public PlacementDef placement() {
        return placement;
    }

    public GeographyDef geography() {
        return geography;
    }

    public PopulationDef population() {
        return population;
    }

    /** Мітки, які держава може отримати з карти: рівні площі, географія й рівні населення. */
    public TreeSet<String> producedTags() {
        TreeSet<String> tags = placement.producedTags();
        tags.addAll(geography.producedTags());
        tags.addAll(population.producedTags());
        return tags;
    }
}
