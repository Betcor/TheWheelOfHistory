package kolo.engine.generation.country;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.engine.content.AreaLevelId;
import kolo.engine.content.CoastLevelId;
import kolo.engine.content.ContentPack;
import kolo.engine.content.ModifierDef;
import kolo.engine.content.TestMaps;
import kolo.engine.generation.map.FertilityMap;
import kolo.engine.generation.name.TestNames;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierSource;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.modifier.SourceKind;
import kolo.engine.state.Terrain;

/**
 * Фікстура колеса населення: пакет з {@link TestMaps#POPULATION} (рівні 100 / 1000 / 5000 / 20 000 / 100 000 тисяч,
 * ваги — базисні пункти) і маленька держава, складена вручну.
 */
final class TestPopulation {

    static final ContentPack PACK = TestNames.PACK;

    static final String[] IDS = {"tiny", "small", "medium", "large", "huge"};
    static final int[] WEIGHTS = {1_000, 2_000, 4_000, 2_000, 1_000};

    static final AreaLevelId SMALL = new AreaLevelId("small");
    static final AreaLevelId MEDIUM = new AreaLevelId("medium");
    static final AreaLevelId LARGE = new AreaLevelId("large");

    /**
     * Суходіл карти: комірки 0–3 — держава (родючість 0, 50, 100, 20; комірка 1 — на березі), комірка 4 — чужа
     * (родючість 30). Середня родючість світу — 40, держави — 42; ваги провінцій 10 / 80 / 110 / 30.
     */
    static final FertilityMap FERTILITY = new FertilityMap(new TreeMap<>(Map.of(0, 0, 1, 50, 2, 100, 3, 20, 4, 30)));

    static final StartGeography GEOGRAPHY = new StartGeography(
            List.of(0, 1, 2, 3),
            List.of(1),
            new CoastLevelId("coastal"),
            List.of(0),
            new TreeMap<>(Map.of(Terrain.PLAIN, 3, Terrain.HILLS, 1)),
            Terrain.PLAIN,
            42,
            new TreeSet<>(List.of("coastal")));

    private TestPopulation() {}

    /** Модифікатор переваги колеса населення від довільної події. */
    static Modifier modifier(String id, int value) {
        return new ModifierDef(ModifierTarget.wheel(PopulationWheel.KIND), value)
                .toModifier(id, new ModifierSource(SourceKind.EVENT, "test"), null, "event.test");
    }
}
