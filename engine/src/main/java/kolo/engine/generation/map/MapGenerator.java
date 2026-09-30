package kolo.engine.generation.map;

import java.util.Objects;
import kolo.engine.content.ContentPack;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.InvariantViolationException;
import kolo.engine.error.ValidationException;
import kolo.engine.rng.Rng;

/**
 * Уся генерація карти (GD §3.3–3.6) по черзі: розмір світу → сітка → материки → рельєф → клімат → море → річки →
 * родючість → придатність до родовищ → розміщення держав.
 */
public final class MapGenerator {

    private MapGenerator() {}

    /**
     * @param rng потік карти світу; розгалужується за етапами ({@code world_size}, {@code grid}, {@code continents},
     *     {@code relief}, {@code climate}, {@code sea}, {@code placement}), тож зміна одного етапу не зсуває кидків
     *     інших; річки, родючість і придатність кидків не мають
     * @param input що зафіксував хост
     * @throws ValidationException якщо вхід не узгоджується з контентом (див. {@link WorldSizeWheel})
     * @throws InvariantViolationException якщо якийсь етап не зміг розкласти карту
     */
    public static WorldMap generate(Rng rng, ContentPack content, WorldSizeInput input) {
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(input, "input");
        WorldSize size = WorldSizeWheel.generate(rng.fork("world_size"), content, input);
        MapTemplateDef template = content.map()
                .template(size.template())
                .orElseThrow(() -> new InvariantViolationException(ErrorDetails.of(
                        "field", "template", "value", size.template().value())));
        MapGrid grid =
                VoronoiGrid.generate(rng.fork("grid"), content.map().grid(), template.gridCells(size.provinces()));
        ContinentMap continents = ContinentGenerator.generate(rng.fork("continents"), content, size, grid);
        ReliefMap relief = ReliefGenerator.generate(rng.fork("relief"), content, grid, continents);
        ClimateMap climate = ClimateGenerator.generate(rng.fork("climate"), content, grid, continents, relief);
        SeaMap sea = SeaGenerator.generate(rng.fork("sea"), content, grid, continents);
        RiverMap rivers = RiverGenerator.generate(content, grid, relief, climate, sea);
        FertilityMap fertility = FertilityGenerator.generate(content, climate, rivers);
        ResourceSuitabilityMap suitability = ResourceSuitabilityGenerator.generate(content, climate, fertility);
        PlacementMap placement = PlacementGenerator.generate(rng.fork("placement"), content, size, grid, continents);
        return new WorldMap(size, grid, continents, relief, climate, sea, rivers, fertility, suitability, placement);
    }
}
