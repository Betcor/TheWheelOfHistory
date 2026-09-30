package kolo.server.persistence;

import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.world.WorldGenerator;
import kolo.engine.generation.world.WorldStates;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldState;

/**
 * Світи з вбудованого контенту для тестів снапшотів. Згенерований стан має все, що дає генерація (модифікатори,
 * записи коліс, релігії, людей), тож знімок перевіряється на справжніх даних, а не на вигаданих.
 */
final class TestWorlds {

    static final ContentPack PACK = ContentLoader.loadBundled();

    private static final WorldState SMALL = state(1, 1, NpcShare.FEW);

    private TestWorlds() {}

    static WorldState state(long seed, int players, NpcShare share) {
        return WorldStates.of(
                seed, PACK, WorldGenerator.generate(Rng.of(seed), PACK, WorldSizeInput.of(players, share)));
    }

    /** Малий світ; щоразу незалежна копія (карта й релігії спільні — вони незмінні). */
    static WorldState small() {
        return SMALL.deepCopy();
    }
}
