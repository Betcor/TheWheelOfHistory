package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.BackstoryFragmentDef;
import kolo.engine.content.ContentPack;
import kolo.engine.content.GdpLevelDef;
import kolo.engine.content.GdpLevelId;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.generation.country.Backstory;
import kolo.engine.generation.country.BackstoryWheel;
import kolo.engine.generation.country.DevelopmentWheel;
import kolo.engine.generation.country.GdpWheel;
import kolo.engine.generation.country.Regime;
import kolo.engine.generation.country.RegimeWheel;
import kolo.engine.generation.country.StartDevelopment;
import kolo.engine.generation.country.StartGdp;
import kolo.engine.modifier.Modifiers;
import kolo.engine.rng.Rng;
import kolo.engine.state.CountryId;
import kolo.engine.state.Development;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import org.junit.jupiter.api.Test;

/**
 * Колесо ВВП на вбудованому контенті: мітки рівнів збігаються зі словником міток генерації («бідна» — нижче медіани),
 * лад лише зсуває шанси, кожен рівень досяжний у ланцюжку лад → розвиненість → ВВП, а мітки ВВП ведуть передісторію.
 */
class BundledGdpWheelIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();
    private static final int SEEDS = 5_000;

    @Test
    void levelsGrowFromPoorToRichWithCriticalExtremes() {
        List<GdpLevelDef> levels = PACK.gdpLevels();

        assertThat(levels.stream().map(GdpLevelDef::perCapita).toList()).isSorted();
        assertThat(levels.stream().map(GdpLevelDef::quality).toList())
                .isSorted()
                .doesNotHaveDuplicates();
        // Крайні рівні не зникають навіть з граничною перевагою (GD §2.3).
        assertThat(levels.getFirst().tier()).isEqualTo(OutcomeTier.CRIT_FAIL);
        assertThat(levels.getLast().tier()).isEqualTo(OutcomeTier.CRIT_SUCCESS);
    }

    @Test
    void poorMeansBelowMedianAndMedianLevelIsNeutral() {
        // backstory.yaml: poor — нижче медіани світу, rich — значно вище.
        GdpLevelDef median = medianLevel();
        assertThat(median.tier()).isEqualTo(OutcomeTier.PARTIAL);
        assertThat(median.tags()).isEmpty();
        for (GdpLevelDef level : PACK.gdpLevels()) {
            assertThat(PACK.backstory().generationTags().keySet()).containsAll(level.tags());
            if (level.perCapita() < median.perCapita()) {
                assertThat(level.tags()).as(level.id().value()).containsExactly("poor");
            } else if (level.tags().contains("rich")) {
                assertThat(level.perCapita()).isGreaterThanOrEqualTo(3 * median.perCapita());
            }
        }
        assertThat(PACK.gdpLevels())
                .anySatisfy(level -> assertThat(level.tags()).contains("rich"));
    }

    @Test
    void backstoryReactsToGdpTags() {
        TreeSet<String> referenced = new TreeSet<>();
        for (BackstoryFragmentDef fragment : PACK.backstory().fragments().values()) {
            referenced.addAll(fragment.referencedTags());
        }

        assertThat(referenced).contains("poor", "rich");
    }

    @Test
    void advantageStaysModerateForEveryRegimeAndDevelopment() {
        for (IdeologyDef ideology : PACK.ideologies().values()) {
            for (SubIdeologyDef sub : ideology.subIdeologies()) {
                Regime regime = new Regime(ideology, sub, new TreeSet<>(), List.of());
                int advantage = Modifiers.advantage(regime.modifiers(), GdpWheel.KIND, 0)
                        .value();
                // Лад лише зсуває шанси: гранична перевага лишається для подій і людей.
                assertThat(advantage).as(sub.id().value()).isBetween(-50, 50);
            }
        }
        int perLevel = PACK.balance().generation().gdpDevelopmentAdvantage();
        int branches = GdpWheel.DEVELOPMENT_BRANCHES.size();
        assertThat(branches * Development.MIN * perLevel).isGreaterThan(Advantage.MIN);
        assertThat(branches * Development.MAX * perLevel).isLessThan(Advantage.MAX);
    }

    @Test
    void everyLevelIsReachableInTheChain() {
        TreeMap<GdpLevelId, Integer> counts = new TreeMap<>();
        for (long seed = 0; seed < SEEDS; seed++) {
            Rng rng = Rng.of(seed);
            Regime regime = RegimeWheel.generate(rng.fork("regime"), PACK);
            StartDevelopment development = DevelopmentWheel.generate(rng.fork("development"), PACK, regime.modifiers());
            StartGdp gdp = GdpWheel.generate(rng.fork("gdp"), PACK, regime.modifiers(), development);
            counts.merge(gdp.level(), 1, Integer::sum);
        }

        assertThat(counts.keySet())
                .containsExactlyElementsOf(
                        PACK.gdpLevels().stream().map(GdpLevelDef::id).sorted().toList());
    }

    @Test
    void gdpTagsLeadBackstory() {
        List<CountryId> neighbors = List.of(CountryId.of(2), CountryId.of(5));
        for (long seed = 0; seed < 500; seed++) {
            Rng rng = Rng.of(seed);
            Regime regime = RegimeWheel.generate(rng.fork("regime"), PACK);
            StartDevelopment development = DevelopmentWheel.generate(rng.fork("development"), PACK, regime.modifiers());
            StartGdp gdp = GdpWheel.generate(rng.fork("gdp"), PACK, regime.modifiers(), development);
            TreeSet<String> tags = new TreeSet<>(regime.tags());
            tags.addAll(development.tags());
            tags.addAll(gdp.tags());
            Backstory backstory = BackstoryWheel.generate(rng.fork("backstory"), PACK, tags, neighbors);

            assertThat(backstory.entries()).as("seed %d", seed).isNotEmpty();
            assertThat(backstory.tags()).containsAll(gdp.tags());
        }
    }

    /** Рівень, у який потрапляє половина базової ваги колеса. */
    private static GdpLevelDef medianLevel() {
        int total = PACK.gdpLevels().stream().mapToInt(GdpLevelDef::weight).sum();
        int cumulative = 0;
        for (GdpLevelDef level : PACK.gdpLevels()) {
            cumulative += level.weight();
            if (2 * cumulative > total) {
                return level;
            }
        }
        throw new AssertionError("порожнє колесо");
    }
}
