package kolo.engine.generation.country;

import kolo.engine.content.ContentPack;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.generation.map.ClimateGenerator;
import kolo.engine.generation.map.ClimateMap;
import kolo.engine.generation.map.ContinentGenerator;
import kolo.engine.generation.map.ContinentMap;
import kolo.engine.generation.map.FertilityGenerator;
import kolo.engine.generation.map.FertilityMap;
import kolo.engine.generation.map.MapGrid;
import kolo.engine.generation.map.PlacementGenerator;
import kolo.engine.generation.map.PlacementMap;
import kolo.engine.generation.map.ReliefGenerator;
import kolo.engine.generation.map.ReliefMap;
import kolo.engine.generation.map.RiverGenerator;
import kolo.engine.generation.map.RiverMap;
import kolo.engine.generation.map.SeaGenerator;
import kolo.engine.generation.map.SeaMap;
import kolo.engine.generation.map.VoronoiGrid;
import kolo.engine.generation.map.WorldSize;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.map.WorldSizeWheel;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;

/** Цілий світ на тестовому контенті карти — від розміру світу до розміщення держав — для коліс з території. */
final class TestTerritory {

    private TestTerritory() {}

    /** Світ за seed: 1–3 гравці, частка NPC за seed. */
    static World world(ContentPack pack, long seed) {
        int players = 1 + Math.floorMod(seed, 3);
        NpcShare share = NpcShare.values()[Math.floorMod(seed, NpcShare.values().length)];
        Rng rng = Rng.of(seed);
        WorldSize size = WorldSizeWheel.generate(rng.fork("world_size"), pack, WorldSizeInput.of(players, share));
        MapTemplateDef template = pack.map().template(size.template()).orElseThrow();
        MapGrid grid = VoronoiGrid.generate(rng.fork("grid"), pack.map().grid(), template.gridCells(size.provinces()));
        ContinentMap continents = ContinentGenerator.generate(rng.fork("continents"), pack, size, grid);
        ReliefMap relief = ReliefGenerator.generate(rng.fork("relief"), pack, grid, continents);
        ClimateMap climate = ClimateGenerator.generate(rng.fork("climate"), pack, grid, continents, relief);
        SeaMap sea = SeaGenerator.generate(rng.fork("sea"), pack, grid, continents);
        RiverMap rivers = RiverGenerator.generate(pack, grid, relief, climate, sea);
        FertilityMap fertility = FertilityGenerator.generate(pack, climate, rivers);
        PlacementMap placement = PlacementGenerator.generate(rng.fork("placement"), pack, size, grid, continents);
        return new World(grid, climate, sea, fertility, placement);
    }

    record World(MapGrid grid, ClimateMap climate, SeaMap sea, FertilityMap fertility, PlacementMap placement) {

        /** Географія держави з номером {@code country}. */
        StartGeography geography(ContentPack pack, int country) {
            return Geography.generate(pack, placement.countries().get(country).cells(), sea, climate, fertility);
        }
    }
}
