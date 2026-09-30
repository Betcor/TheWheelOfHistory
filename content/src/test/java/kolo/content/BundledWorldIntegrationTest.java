package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.generation.map.PlacementMap;
import kolo.engine.generation.map.WorldMap;
import kolo.engine.generation.map.WorldSizeInput;
import kolo.engine.generation.world.StartWorld;
import kolo.engine.generation.world.WorldGenerator;
import kolo.engine.rng.Rng;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldLimits;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Цілий світ на вбудованому контенті: у світах усіх розмірів кожна релігія має святий центр на суходолі — у держави
 * своєї віри або на нічийній землі; центри тяжіють до родючих земель і річок, а нічийна земля бере їх рідко; світ
 * відтворюється за seed-ом; найбільший світ вкладається в бюджет генерації карти.
 */
class BundledWorldIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();
    private static final int WORLDS = 40;

    @Test
    void holyCentersStandOnFollowersOrUnclaimedLand() {
        Stats stats = new Stats();
        for (long seed = 0; seed < WORLDS; seed++) {
            int players = 1 + (int) (seed % WorldLimits.MAX_PLAYERS);
            NpcShare share = NpcShare.values()[(int) (seed % NpcShare.values().length)];
            StartWorld world = WorldGenerator.generate(Rng.of(seed), PACK, WorldSizeInput.of(players, share));
            check(world, stats);
        }

        assertThat(stats.religions).isGreaterThan(WORLDS * 3);
        // Нічийна земля важить 20% провінції держави: у релігії з вірянами — рідкість, але не виняток.
        assertThat(stats.followedUnclaimed * 100).isBetween(stats.followed * 3, stats.followed * 25);
        // Святі місця — землі обітовані: родючіші й частіше при річці, ніж суходіл у середньому.
        assertThat(stats.centerFertility * stats.land).isGreaterThan(stats.landFertility * stats.religions);
        assertThat(stats.centerRivers * stats.land).isGreaterThan(stats.landRivers * stats.religions);
    }

    @Test
    void sameSeedGivesSameWorld() {
        WorldSizeInput input = WorldSizeInput.of(4, NpcShare.NORMAL);

        assertThat(WorldGenerator.generate(Rng.of(42), PACK, input))
                .isEqualTo(WorldGenerator.generate(Rng.of(42), PACK, input));
    }

    @Test
    @Tag("budget")
    void largestWorldFitsBudget() {
        WorldSizeInput input = WorldSizeInput.of(WorldLimits.MAX_PLAYERS, NpcShare.MANY);

        Budget.Timed<StartWorld> timed = Budget.best(() -> WorldGenerator.generate(Rng.of(1), PACK, input));

        // 16 гравців і «багато» NPC: 36–40 держав — колесо NPC обрізане межею світу.
        assertThat(timed.result().countries()).hasSizeGreaterThan(2 * WorldLimits.MAX_PLAYERS);
        // Бюджет генерації карти — 2 с; держави й святі центри мусять вкластися разом з нею.
        assertThat(timed.millis()).isLessThan(2000);
    }

    private static void check(StartWorld world, Stats stats) {
        WorldMap map = world.map();
        assertThat(world.countries()).hasSize(map.countries());
        assertThat(world.countries())
                .extracting(country -> country.name().name().fullName().nominative())
                .doesNotHaveDuplicates();
        for (int r = 0; r < world.religions().religions().size(); r++) {
            int cell = world.holyCenters().cell(r);
            int owner = map.placement().country(cell);
            assertThat(map.fertility().fertility(cell)).isPresent();
            if (world.followers(r).isEmpty()) {
                assertThat(owner).isEqualTo(PlacementMap.NONE);
            } else {
                stats.followed++;
                if (owner == PlacementMap.NONE) {
                    stats.followedUnclaimed++;
                } else {
                    assertThat(world.followers(r)).contains(owner);
                }
            }
            stats.religions++;
            stats.centerFertility += map.fertility().fertility(cell).orElseThrow();
            stats.centerRivers += map.rivers().hasRiver(cell) ? 1 : 0;
        }
        for (int cell : map.fertility().fertilities().keySet()) {
            stats.land++;
            stats.landFertility += map.fertility().fertility(cell).orElseThrow();
            stats.landRivers += map.rivers().hasRiver(cell) ? 1 : 0;
        }
    }

    /** Лічильники по всіх світах. */
    private static final class Stats {
        long religions;
        long followed;
        long followedUnclaimed;
        long centerFertility;
        long centerRivers;
        long land;
        long landFertility;
        long landRivers;
    }
}
