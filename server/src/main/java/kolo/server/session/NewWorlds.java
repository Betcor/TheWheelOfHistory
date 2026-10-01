package kolo.server.session;

import kolo.engine.content.ContentPack;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.world.StartWorld;
import kolo.engine.generation.world.WorldGenerator;
import kolo.engine.generation.world.WorldStates;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldState;

/** Новий світ за параметрами хоста (GD §3.4). Той самий seed і параметри — той самий світ. */
public final class NewWorlds {

    private NewWorlds() {}

    /**
     * @param seed seed світу
     * @param players кількість гравців
     * @param npcShare частка NPC-держав
     * @throws kolo.engine.error.ValidationException якщо параметри світу поза межами
     * @throws kolo.engine.error.InvariantViolationException якщо генерація порушила інваріант
     */
    public static WorldState generate(ContentPack content, long seed, int players, NpcShare npcShare) {
        StartWorld world = WorldGenerator.generate(Rng.of(seed), content, WorldSizeInput.of(players, npcShare));
        return WorldStates.of(seed, content, world);
    }
}
