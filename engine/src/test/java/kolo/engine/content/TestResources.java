package kolo.engine.content;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import kolo.engine.state.Climate;
import kolo.engine.state.Terrain;

/** Ресурси й баланс колеса ресурсів для тестів рушія. */
public final class TestResources {

    public static final ResourceId ORE = new ResourceId("ore");
    public static final ResourceId WOOD = new ResourceId("wood");
    public static final ResourceId GRAIN = new ResourceId("grain");
    public static final ResourceId SALT = new ResourceId("salt");
    public static final ResourceId SPICE = new ResourceId("spice");

    /** Руда — гори й пагорби; ліс — ліс, у тропіках удвічі; зерно — родючість від 40. */
    public static final List<ResourceDef> RESOURCES = List.of(
            new ResourceDef(
                    ORE,
                    "Руда",
                    List.of("metal"),
                    Optional.of(DepositDef.byTerrain(
                            terrains(Map.of(Terrain.MOUNTAINS, 40, Terrain.HILLS, 20)), new TreeMap<>()))),
            new ResourceDef(
                    WOOD,
                    "Деревина",
                    List.of(),
                    Optional.of(DepositDef.byTerrain(
                            terrains(Map.of(Terrain.FOREST, 50)), new TreeMap<>(Map.of(Climate.TROPICAL, 200))))),
            new ResourceDef(GRAIN, "Зерно", List.of("food"), Optional.of(DepositDef.byFertility(40))),
            new ResourceDef(
                    SALT,
                    "Сіль",
                    List.of(),
                    Optional.of(DepositDef.byTerrain(
                            terrains(Map.of(Terrain.PLAIN, 10, Terrain.DESERT, 30)), new TreeMap<>()))),
            // Лише торгівлею: на карті не буває.
            new ResourceDef(SPICE, "Прянощі", List.of()));

    /** До 20 провінцій — 1–2 родовища, до 100 — 2–3. */
    public static final ResourceBalanceDef BALANCE = balance(new CountRange(1, 2), new CountRange(2, 3));

    private TestResources() {}

    /** Два рядки: до 20 провінцій — {@code small}, більше — {@code large}. */
    public static ResourceBalanceDef balance(CountRange small, CountRange large) {
        return new ResourceBalanceDef(List.of(new ResourceCountDef(20, small), new ResourceCountDef(100, large)));
    }

    public static TreeMap<Terrain, Integer> terrains(Map<Terrain, Integer> values) {
        return new TreeMap<>(values);
    }
}
