package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.content.GdpLevelDef;
import kolo.engine.content.HdiLevelDef;
import kolo.engine.content.HdiLevelId;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.generation.country.Backstory;
import kolo.engine.generation.country.BackstoryWheel;
import kolo.engine.generation.country.DevelopmentWheel;
import kolo.engine.generation.country.GdpWheel;
import kolo.engine.generation.country.HdiWheel;
import kolo.engine.generation.country.Regime;
import kolo.engine.generation.country.RegimeWheel;
import kolo.engine.generation.country.StartDevelopment;
import kolo.engine.generation.country.StartGdp;
import kolo.engine.generation.country.StartHdi;
import kolo.engine.modifier.Modifiers;
import kolo.engine.rng.Rng;
import kolo.engine.state.CountryId;
import kolo.engine.state.Stat;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import org.junit.jupiter.api.Test;

/**
 * Колесо ІЛР на вбудованому контенті: рівні зростають у межах показника, лад і ВВП лише зсувають шанси, кожен рівень
 * досяжний у ланцюжку лад → розвиненість → ВВП → ІЛР, а бідність тягне ІЛР донизу.
 */
class BundledHdiWheelIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();
    private static final int SEEDS = 5_000;

    @Test
    void levelsGrowWithinHdiStatWithCriticalExtremes() {
        List<HdiLevelDef> levels = PACK.hdiLevels();

        assertThat(levels.stream().map(HdiLevelDef::hdi).toList()).isSorted().doesNotHaveDuplicates();
        assertThat(levels.stream().map(HdiLevelDef::quality).toList())
                .isSorted()
                .doesNotHaveDuplicates();
        assertThat((long) levels.getFirst().hdi()).isGreaterThan(Stat.HDI.min());
        assertThat((long) levels.getLast().hdi()).isLessThan(Stat.HDI.max());
        // Крайні рівні не зникають навіть з граничною перевагою (GD §2.3).
        assertThat(levels.getFirst().tier()).isEqualTo(OutcomeTier.CRIT_FAIL);
        assertThat(levels.getLast().tier()).isEqualTo(OutcomeTier.CRIT_SUCCESS);
        assertThat(levels).anySatisfy(level -> assertThat(level.tier()).isEqualTo(OutcomeTier.PARTIAL));
    }

    @Test
    void levelTagsAreKnownGenerationTags() {
        for (HdiLevelDef level : PACK.hdiLevels()) {
            assertThat(PACK.backstory().generationTags().keySet()).containsAll(level.tags());
        }
    }

    @Test
    void lowAndHighLevelsAreTagged() {
        for (HdiLevelDef level : PACK.hdiLevels()) {
            if (level.hdi() <= 30) {
                assertThat(level.tags()).as(level.id().value()).containsExactly("low_hdi");
            } else if (level.hdi() >= 75) {
                assertThat(level.tags()).as(level.id().value()).containsExactly("high_hdi");
            } else {
                assertThat(level.tags()).as(level.id().value()).isEmpty();
            }
        }
    }

    @Test
    void advantageStaysModerateForEveryRegimeAndGdp() {
        for (IdeologyDef ideology : PACK.ideologies().values()) {
            for (SubIdeologyDef sub : ideology.subIdeologies()) {
                Regime regime = new Regime(ideology, sub, new TreeSet<>(), List.of());
                int advantage = Modifiers.advantage(regime.modifiers(), HdiWheel.KIND, 0)
                        .value();
                // Лад лише зсуває шанси: гранична перевага лишається для подій і людей.
                assertThat(advantage).as(sub.id().value()).isBetween(-50, 50);
            }
        }
        int perStep = PACK.balance().generation().hdiGdpAdvantage();
        for (GdpLevelDef level : PACK.gdpLevels()) {
            assertThat(level.tier().step() * perStep).as(level.id().value()).isBetween(-50, 50);
        }
        assertThat(perStep).isLessThan(Advantage.MAX);
    }

    @Test
    void everyLevelIsReachableInTheChain() {
        TreeMap<HdiLevelId, Integer> counts = new TreeMap<>();
        for (long seed = 0; seed < SEEDS; seed++) {
            counts.merge(chain(seed).hdi().level(), 1, Integer::sum);
        }

        assertThat(counts.keySet())
                .containsExactlyElementsOf(
                        PACK.hdiLevels().stream().map(HdiLevelDef::id).sorted().toList());
    }

    @Test
    void richCountriesHaveHigherHdiOnAverageThanPoorOnes() {
        long poorSum = 0;
        long poorCount = 0;
        long richSum = 0;
        long richCount = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            Chain chain = chain(seed);
            if (chain.gdp().tags().contains("poor")) {
                poorSum += chain.hdi().hdi();
                poorCount++;
            } else if (chain.gdp().tags().contains("rich")) {
                richSum += chain.hdi().hdi();
                richCount++;
            }
        }

        assertThat(poorCount).isPositive();
        assertThat(richCount).isPositive();
        assertThat(richSum * poorCount).isGreaterThan(poorSum * richCount);
    }

    @Test
    void hdiTagsLeadBackstory() {
        List<CountryId> neighbors = List.of(CountryId.of(2), CountryId.of(5));
        for (long seed = 0; seed < 500; seed++) {
            Chain chain = chain(seed);
            TreeSet<String> tags = new TreeSet<>(chain.regime().tags());
            tags.addAll(chain.development().tags());
            tags.addAll(chain.gdp().tags());
            tags.addAll(chain.hdi().tags());
            Backstory backstory = BackstoryWheel.generate(Rng.of(seed).fork("backstory"), PACK, tags, neighbors);

            assertThat(backstory.entries()).as("seed %d", seed).isNotEmpty();
            assertThat(backstory.tags()).containsAll(chain.hdi().tags());
        }
    }

    private static Chain chain(long seed) {
        Rng rng = Rng.of(seed);
        Regime regime = RegimeWheel.generate(rng.fork("regime"), PACK);
        StartDevelopment development = DevelopmentWheel.generate(rng.fork("development"), PACK, regime.modifiers());
        StartGdp gdp = GdpWheel.generate(rng.fork("gdp"), PACK, regime.modifiers(), development);
        StartHdi hdi = HdiWheel.generate(rng.fork("hdi"), PACK, regime.modifiers(), gdp);
        return new Chain(regime, development, gdp, hdi);
    }

    private record Chain(Regime regime, StartDevelopment development, StartGdp gdp, StartHdi hdi) {}
}
