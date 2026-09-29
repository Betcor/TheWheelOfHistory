package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeSet;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.content.MapTemplateDef;
import kolo.engine.content.MapTemplateId;
import kolo.engine.generation.map.WorldSize;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.map.WorldSizeWheel;
import kolo.engine.generation.religion.StartReligions;
import kolo.engine.generation.religion.WorldReligionsWheel;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldLimits;
import org.junit.jupiter.api.Test;

/**
 * Розмір світу на вбудованому контенті: за будь-якої кількості гравців і частки NPC держав не більше 40, провінцій
 * — у межах балансу, кожен шаблон досяжний, а будь-яку кількість материків, яку дає якийсь шаблон, хост може
 * зафіксувати.
 */
class BundledWorldSizeIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();
    private static final int SEEDS = 60;

    @Test
    void everyPlayerCountAndShareGivesValidWorld() {
        TreeSet<MapTemplateId> templates = new TreeSet<>();
        for (int players = WorldLimits.MIN_PLAYERS; players <= WorldLimits.MAX_PLAYERS; players++) {
            for (NpcShare share : NpcShare.values()) {
                for (long seed = 0; seed < SEEDS; seed++) {
                    WorldSize size = WorldSizeWheel.generate(Rng.of(seed), PACK, WorldSizeInput.of(players, share));
                    MapTemplateDef template =
                            PACK.map().template(size.template()).orElseThrow();
                    templates.add(template.id());

                    assertThat(size.countries()).isBetween(players + 1, WorldLimits.MAX_COUNTRIES);
                    assertThat(size.provinces()).isBetween(400, 3500);
                    assertThat(template.continents().contains(size.continents()))
                            .isTrue();
                    assertThat(size.unclaimedBp()).isBetween(500, 1500);
                }
            }
        }

        assertThat(templates)
                .containsExactlyInAnyOrderElementsOf(
                        PACK.map().templates().stream().map(MapTemplateDef::id).toList());
    }

    @Test
    void hostCanFixAnyContinentCountThatSomeTemplateGives() {
        for (int continents = 1; continents <= 8; continents++) {
            WorldSize size = WorldSizeWheel.generate(
                    Rng.of(continents),
                    PACK,
                    new WorldSizeInput(4, NpcShare.NORMAL, Optional.empty(), OptionalInt.of(continents)));
            MapTemplateDef template = PACK.map().template(size.template()).orElseThrow();

            assertThat(size.continents()).isEqualTo(continents);
            assertThat(template.continents().contains(continents)).isTrue();
        }
    }

    @Test
    void worldSizeFeedsWorldReligions() {
        for (long seed = 0; seed < SEEDS; seed++) {
            Rng world = Rng.of(seed);
            WorldSize size =
                    WorldSizeWheel.generate(world.fork("world_size"), PACK, WorldSizeInput.of(8, NpcShare.MANY));
            StartReligions religions = WorldReligionsWheel.generate(world.fork("religions"), PACK, size.countries());

            assertThat(religions.religions()).isNotEmpty();
        }
    }
}
