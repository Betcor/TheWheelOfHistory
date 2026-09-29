package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ArmySizeDef;
import kolo.engine.content.ArmySizeId;
import kolo.engine.content.ContentPack;
import kolo.engine.content.GdpLevelDef;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.generation.country.ArmySizeWheel;
import kolo.engine.generation.country.Backstory;
import kolo.engine.generation.country.BackstoryWheel;
import kolo.engine.generation.country.DevelopmentWheel;
import kolo.engine.generation.country.GdpWheel;
import kolo.engine.generation.country.Regime;
import kolo.engine.generation.country.RegimeWheel;
import kolo.engine.generation.country.StartArmySize;
import kolo.engine.generation.country.StartDevelopment;
import kolo.engine.generation.country.StartGdp;
import kolo.engine.modifier.Modifiers;
import kolo.engine.rng.Rng;
import kolo.engine.state.CountryId;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import org.junit.jupiter.api.Test;

/**
 * Колесо розміру армії на вбудованому контенті: частки в межах GD §4.4, мітки {@code small_army}/{@code large_army}
 * відповідають своїм порогам, лад і ВВП лише зсувають шанси, кожен рівень досяжний у ланцюжку лад → розвиненість → ВВП
 * → армія, а мілітаристський лад тримає більшу армію.
 */
class BundledArmySizeWheelIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();
    private static final int SEEDS = 5_000;

    /** GD §4.4: від 0,2% до 8% населення. */
    private static final int MIN_SHARE_BP = 20;

    private static final int MAX_SHARE_BP = 800;

    /** Пороги міток з {@code generation_tags}: менше 0,5% — мала армія, не менше 3% — велика. */
    private static final int SMALL_ARMY_BELOW_BP = 50;

    private static final int LARGE_ARMY_FROM_BP = 300;

    @Test
    void sizesSpanDesignRangeWithCriticalExtremes() {
        List<ArmySizeDef> sizes = PACK.armySizes();

        assertThat(sizes.stream().map(ArmySizeDef::shareBp).toList()).isSorted().doesNotHaveDuplicates();
        assertThat(sizes.stream().map(ArmySizeDef::quality).toList()).isSorted().doesNotHaveDuplicates();
        assertThat(sizes.getFirst().shareBp()).isEqualTo(MIN_SHARE_BP);
        assertThat(sizes.getLast().shareBp()).isEqualTo(MAX_SHARE_BP);
        // Крайні сектори рідкісні, але не зникають навіть з граничною перевагою (GD §2.3, §4.4).
        assertThat(sizes.getFirst().tier()).isEqualTo(OutcomeTier.CRIT_FAIL);
        assertThat(sizes.getLast().tier()).isEqualTo(OutcomeTier.CRIT_SUCCESS);
        assertThat(sizes).anySatisfy(size -> assertThat(size.tier()).isEqualTo(OutcomeTier.PARTIAL));
        int total = sizes.stream().mapToInt(ArmySizeDef::weight).sum();
        assertThat(sizes.getFirst().weight() * 10).isLessThan(total);
        assertThat(sizes.getLast().weight() * 10).isLessThan(total);
    }

    @Test
    void armyTagsMatchTheirThresholds() {
        for (ArmySizeDef size : PACK.armySizes()) {
            assertThat(PACK.backstory().generationTags().keySet()).containsAll(size.tags());
            assertThat(size.tags().contains("small_army"))
                    .as(size.id().value())
                    .isEqualTo(size.shareBp() < SMALL_ARMY_BELOW_BP);
            assertThat(size.tags().contains("large_army"))
                    .as(size.id().value())
                    .isEqualTo(size.shareBp() >= LARGE_ARMY_FROM_BP);
        }
    }

    @Test
    void advantageStaysModerateForEveryRegimeAndGdp() {
        for (IdeologyDef ideology : PACK.ideologies().values()) {
            for (SubIdeologyDef sub : ideology.subIdeologies()) {
                Regime regime = new Regime(ideology, sub, new TreeSet<>(), List.of());
                int advantage = Modifiers.advantage(regime.modifiers(), ArmySizeWheel.KIND, 0)
                        .value();
                // Лад лише зсуває шанси: гранична перевага лишається для подій і людей.
                assertThat(advantage).as(sub.id().value()).isBetween(-50, 50);
            }
        }
        int perStep = PACK.balance().generation().armySizeGdpAdvantage();
        for (GdpLevelDef level : PACK.gdpLevels()) {
            assertThat(level.tier().step() * perStep).as(level.id().value()).isBetween(-50, 50);
        }
        assertThat(perStep).isLessThan(Advantage.MAX);
    }

    @Test
    void everySizeIsReachableInTheChain() {
        TreeMap<ArmySizeId, Integer> counts = new TreeMap<>();
        for (long seed = 0; seed < SEEDS; seed++) {
            counts.merge(chain(seed).army().size(), 1, Integer::sum);
        }

        assertThat(counts.keySet())
                .containsExactlyElementsOf(
                        PACK.armySizes().stream().map(ArmySizeDef::id).sorted().toList());
    }

    @Test
    void militaristRegimesKeepLargerArmiesOnAverage() {
        long militaristSum = 0;
        long militaristCount = 0;
        long otherSum = 0;
        long otherCount = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            Chain chain = chain(seed);
            if (Modifiers.advantage(chain.regime().modifiers(), ArmySizeWheel.KIND, 0)
                            .value()
                    > 0) {
                militaristSum += chain.army().shareBp();
                militaristCount++;
            } else {
                otherSum += chain.army().shareBp();
                otherCount++;
            }
        }

        assertThat(militaristCount).isPositive();
        assertThat(otherCount).isPositive();
        assertThat(militaristSum * otherCount).isGreaterThan(otherSum * militaristCount);
    }

    @Test
    void armyTagsLeadBackstory() {
        List<CountryId> neighbors = List.of(CountryId.of(2), CountryId.of(5));
        for (long seed = 0; seed < 500; seed++) {
            Chain chain = chain(seed);
            TreeSet<String> tags = new TreeSet<>(chain.regime().tags());
            tags.addAll(chain.development().tags());
            tags.addAll(chain.gdp().tags());
            tags.addAll(chain.army().tags());
            Backstory backstory = BackstoryWheel.generate(Rng.of(seed).fork("backstory"), PACK, tags, neighbors);

            assertThat(backstory.entries()).as("seed %d", seed).isNotEmpty();
            assertThat(backstory.tags()).containsAll(chain.army().tags());
        }
    }

    private static Chain chain(long seed) {
        Rng rng = Rng.of(seed);
        Regime regime = RegimeWheel.generate(rng.fork("regime"), PACK);
        StartDevelopment development = DevelopmentWheel.generate(rng.fork("development"), PACK, regime.modifiers());
        StartGdp gdp = GdpWheel.generate(rng.fork("gdp"), PACK, regime.modifiers(), development);
        StartArmySize army = ArmySizeWheel.generate(rng.fork("army_size"), PACK, regime.modifiers(), gdp);
        return new Chain(regime, development, gdp, army);
    }

    private record Chain(Regime regime, StartDevelopment development, StartGdp gdp, StartArmySize army) {}
}
