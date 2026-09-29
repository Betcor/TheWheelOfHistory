package kolo.engine.generation.map;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.engine.content.ContentPack;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldLimits;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class WorldSizeWheelPropertiesTest {

    private static final ContentPack PACK = TestNames.PACK;

    @Property
    void sameSeedGivesSameWorld(
            @ForAll long seed,
            @ForAll @IntRange(min = 1, max = WorldLimits.MAX_PLAYERS) int players,
            @ForAll NpcShare share) {
        WorldSizeInput input = WorldSizeInput.of(players, share);

        assertThat(WorldSizeWheel.generate(Rng.of(seed), PACK, input))
                .isEqualTo(WorldSizeWheel.generate(Rng.of(seed), PACK, input));
    }

    @Property
    void worldStaysWithinLimits(
            @ForAll long seed,
            @ForAll @IntRange(min = 1, max = WorldLimits.MAX_PLAYERS) int players,
            @ForAll NpcShare share) {
        WorldSize size = WorldSizeWheel.generate(Rng.of(seed), PACK, WorldSizeInput.of(players, share));
        MapTemplateDef template = PACK.map().template(size.template()).orElseThrow();
        int extraMin = PACK.balance().world().npcExtra(share).min();

        assertThat(size.countries()).isLessThanOrEqualTo(WorldLimits.MAX_COUNTRIES);
        assertThat(size.npc()).isEqualTo(Math.min(size.npc(), WorldLimits.MAX_COUNTRIES - players));
        assertThat(size.npc())
                .isGreaterThanOrEqualTo(Math.min(players + extraMin, WorldLimits.MAX_COUNTRIES - players));
        assertThat(size.provinces()).isBetween(100, 3000);
        assertThat(template.continents().contains(size.continents())).isTrue();
        assertThat(size.rolls()).hasSize(5);
    }
}
