package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.ContentPack;
import kolo.engine.content.ResourceDef;

/** Перевірки придатності до родовищ, спільні для тестів рушія. */
public final class ResourceSuitabilityChecks {

    private ResourceSuitabilityChecks() {}

    /**
     * Придатність є рівно в кожної комірки суходолу й дорівнює таблиці ресурсу за поясом, місцевістю й родючістю;
     * нульова не зберігається, ресурси без родовищ відсутні.
     */
    public static void assertValid(
            ContentPack content, ClimateMap climate, FertilityMap fertility, ResourceSuitabilityMap suitability) {
        assertThat(suitability.suitabilities().keySet())
                .isEqualTo(climate.climates().keySet());
        for (int cell : suitability.suitabilities().keySet()) {
            for (ResourceDef resource : content.resources().values()) {
                int expected = resource.deposits()
                        .map(def -> def.suitability(
                                climate.climates().get(cell),
                                climate.terrains().get(cell),
                                fertility.fertility(cell).orElseThrow()))
                        .orElse(0);
                assertThat(suitability.suitability(cell, resource.id()))
                        .as("придатність комірки %d до %s", cell, resource.id())
                        .isEqualTo(expected);
                assertThat(suitability.suitabilities().get(cell).containsKey(resource.id()))
                        .isEqualTo(expected > 0);
            }
        }
    }
}
