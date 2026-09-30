package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.FertilityDef;

/** Перевірки родючості, спільні для тестів рушія. */
public final class FertilityChecks {

    private FertilityChecks() {}

    /** Родючість є рівно в кожної комірки суходолу й дорівнює таблиці за поясом, місцевістю, вологою й річкою. */
    public static void assertValid(ClimateMap climate, RiverMap rivers, FertilityMap fertility, FertilityDef def) {
        assertThat(fertility.fertilities().keySet())
                .isEqualTo(climate.climates().keySet());
        for (int cell : fertility.fertilities().keySet()) {
            int expected = Math.clamp(
                    def.climates().get(climate.climates().get(cell))
                            + def.terrains().get(climate.terrains().get(cell))
                            + climate.moistures().get(cell) * def.moisturePct() / 100
                            + (rivers.hasRiver(cell) ? def.river() : 0),
                    0,
                    FertilityDef.MAX_VALUE);
            assertThat(fertility.fertility(cell))
                    .as("родючість комірки %d", cell)
                    .hasValue(expected);
        }
    }
}
