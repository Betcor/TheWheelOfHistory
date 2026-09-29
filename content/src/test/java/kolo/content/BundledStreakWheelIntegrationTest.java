package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.IntPredicate;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.content.ResourceId;
import kolo.engine.content.StreakKind;
import kolo.engine.content.StreakRewardDef;
import kolo.engine.content.StreakRulesDef;
import kolo.engine.content.StreakWheelDef;
import kolo.engine.generation.country.CountryGenerationInput;
import kolo.engine.generation.country.CountryGenerator;
import kolo.engine.generation.country.StreakBonus;
import kolo.engine.generation.country.StreakWheel;
import kolo.engine.generation.religion.StartReligion;
import kolo.engine.generation.religion.WorldReligionsWheel;
import kolo.engine.modifier.Modifier;
import kolo.engine.rng.Rng;
import org.junit.jupiter.api.Test;

/**
 * Колеса стріків на вбудованому контенті: обидва стріки можливі в порядку коліс генерації й трапляються не в кожної
 * держави; кожна нагорода досяжна, «Золота доба» завжди привертає увагу світу, «Андердог» дає жетони долі.
 */
class BundledStreakWheelIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();
    private static final int SEEDS = 5_000;
    private static final List<StartReligion> RELIGIONS =
            WorldReligionsWheel.generate(Rng.of(0), PACK, 20).religions();

    @Test
    void goldenAgeAlwaysDrawsWorldAttention() {
        assertThat(PACK.streaks().wheel(StreakKind.GOLDEN_AGE).tags()).contains("world_attention");
        assertThat(PACK.streaks().wheel(StreakKind.UNDERDOG).tags()).doesNotContain("world_attention");
    }

    @Test
    void underdogCompensatesWithFateTokens() {
        // GD §4.10: «Андердог» — компенсація; серед нагород є жетони долі.
        assertThat(PACK.streaks().wheel(StreakKind.UNDERDOG).rewards())
                .anySatisfy(reward -> assertThat(reward.fateTokens()).isPositive());
        assertThat(PACK.streaks().wheel(StreakKind.GOLDEN_AGE).rewards())
                .allSatisfy(reward -> assertThat(reward.fateTokens()).isZero());
    }

    @Test
    void everyRewardIsReachableAndGivesValidModifiers() {
        for (StreakWheelDef wheel : PACK.streaks().wheels().values()) {
            TreeSet<String> rolled = new TreeSet<>();
            for (long seed = 0; seed < 2_000; seed++) {
                StreakBonus bonus = StreakWheel.generate(Rng.of(seed), PACK, wheel.kind());
                rolled.add(bonus.reward().id().value());
                assertThat(bonus.tags()).containsAll(wheel.tags());
                for (Modifier modifier : bonus.modifiers()) {
                    assertThat(modifier.isActiveAt(0)).isTrue();
                }
            }

            assertThat(rolled)
                    .as(wheel.kind().key())
                    .containsExactlyInAnyOrderElementsOf(wheel.rewards().stream()
                            .map(reward -> reward.id().value())
                            .toList());
        }
    }

    @Test
    void rewardsHaveTexts() {
        for (StreakWheelDef wheel : PACK.streaks().wheels().values()) {
            assertThat(wheel.name()).isNotBlank();
            for (StreakRewardDef reward : wheel.rewards()) {
                assertThat(reward.name()).isNotBlank();
                assertThat(reward.description()).isNotBlank();
            }
        }
    }

    @Test
    void bothStreaksArePossibleInChainOrder() {
        // Три колеса поспіль у порядку GD §4.1, кожне з яких може дати дуже добрий (поганий) результат. Розвиненість
        // рахується середньою за галузями, тож її межі — межі рівнів; передісторія — середньою за фрагментами.
        StreakRulesDef rules = PACK.balance().streaks();
        List<List<Integer>> wheels = List.of(
                PACK.developmentLevels().values().stream()
                        .map(level -> level.quality())
                        .toList(),
                PACK.gdpLevels().stream().map(level -> level.quality()).toList(),
                PACK.hdiLevels().stream().map(level -> level.quality()).toList(),
                PACK.armySizes().stream().map(size -> size.quality()).toList(),
                PACK.trainingLevels().values().stream()
                        .map(level -> level.quality())
                        .toList(),
                PACK.nuclearStatuses().values().stream()
                        .map(status -> status.quality())
                        .toList(),
                PACK.backstory().fragments().values().stream()
                        .map(fragment -> fragment.quality())
                        .toList());

        assertThat(longestRun(wheels, quality -> rules.isVeryGood(quality))).isGreaterThanOrEqualTo(rules.length());
        assertThat(longestRun(wheels, quality -> rules.isVeryBad(quality))).isGreaterThanOrEqualTo(rules.length());
    }

    @Test
    void streaksAreRareInTheChainAndFireOncePerKind() {
        TreeMap<StreakKind, Integer> counts = new TreeMap<>();
        for (long seed = 0; seed < SEEDS; seed++) {
            List<StreakKind> fired = streaksOf(seed);
            assertThat(fired).doesNotHaveDuplicates();
            for (StreakKind kind : fired) {
                counts.merge(kind, 1, Integer::sum);
            }
        }

        // Стрік — подія, а не норма: не частіше, ніж у кожної четвертої держави. Без коліс карти стрік дуже
        // рідкісний; частоту переглянемо, коли ланцюжок буде повним.
        counts.values().forEach(count -> assertThat(count * 4).isLessThan(SEEDS));
    }

    /** Найдовша серія коліс поспіль, кожне з яких має хоча б один результат з умовою. */
    private static int longestRun(List<List<Integer>> wheels, IntPredicate extreme) {
        int longest = 0;
        int run = 0;
        for (List<Integer> qualities : wheels) {
            run = qualities.stream().anyMatch(extreme::test) ? run + 1 : 0;
            longest = Math.max(longest, run);
        }
        return longest;
    }

    /** Стріки держави з ланцюжка коліс генерації без карти; уран — у кожної другої. */
    private static List<StreakKind> streaksOf(long seed) {
        TreeSet<ResourceId> resources = new TreeSet<>();
        if (seed % 2 == 0) {
            resources.add(new ResourceId("uranium"));
        }
        CountryGenerationInput input =
                new CountryGenerationInput(RELIGIONS, resources, new TreeSet<>(), new TreeSet<>(), new TreeSet<>());
        return CountryGenerator.generate(Rng.of(seed), PACK, input).streaks().stream()
                .map(StreakBonus::streak)
                .toList();
    }
}
