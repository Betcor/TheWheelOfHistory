package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.content.GdpLevelDef;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.content.TrainingLevelDef;
import kolo.engine.generation.country.ArmySizeWheel;
import kolo.engine.generation.country.ArmyTrainingWheel;
import kolo.engine.generation.country.Backstory;
import kolo.engine.generation.country.BackstoryWheel;
import kolo.engine.generation.country.DevelopmentWheel;
import kolo.engine.generation.country.GdpWheel;
import kolo.engine.generation.country.Regime;
import kolo.engine.generation.country.RegimeWheel;
import kolo.engine.generation.country.StartArmySize;
import kolo.engine.generation.country.StartArmyTraining;
import kolo.engine.generation.country.StartDevelopment;
import kolo.engine.generation.country.StartGdp;
import kolo.engine.modifier.Modifiers;
import kolo.engine.rng.Rng;
import kolo.engine.state.CountryId;
import kolo.engine.state.Development;
import kolo.engine.state.TechBranch;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import org.junit.jupiter.api.Test;

/**
 * Колесо вишколу армії на вбудованому контенті: п'ять рівнів GD §4.4 з модифікаторами −20..+20, лад, ВВП і військова
 * розвиненість лише зсувають шанси, кожен рівень досяжний у ланцюжку лад → розвиненість → ВВП → армія, а розвинене
 * військо навчене краще.
 */
class BundledArmyTrainingWheelIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();
    private static final int SEEDS = 5_000;

    @Test
    void levelsMatchDesignTable() {
        // GD §4.4: ополчення −20, резервісти −10, регулярна армія 0, ветерани +10, еліта +20.
        assertThat(PACK.trainingLevels().values())
                .extracting(TrainingLevelDef::name, TrainingLevelDef::combatModifier)
                .containsExactly(
                        tuple("Ополчення", -20),
                        tuple("Резервісти", -10),
                        tuple("Регулярна армія", 0),
                        tuple("Ветерани", 10),
                        tuple("Еліта", 20));
    }

    @Test
    void extremesAreCriticalAndRare() {
        List<TrainingLevelDef> levels = List.copyOf(PACK.trainingLevels().values());

        assertThat(levels.stream().map(TrainingLevelDef::quality).toList())
                .isSorted()
                .doesNotHaveDuplicates();
        assertThat(levels.getFirst().tier()).isEqualTo(OutcomeTier.CRIT_FAIL);
        assertThat(levels.getLast().tier()).isEqualTo(OutcomeTier.CRIT_SUCCESS);
        assertThat(levels).anySatisfy(level -> assertThat(level.tier()).isEqualTo(OutcomeTier.PARTIAL));
        int total = levels.stream().mapToInt(TrainingLevelDef::weight).sum();
        // Регулярна армія — найчастіша, крайні рівні — не частіше за кожну десяту державу.
        assertThat(levels.get(2).weight())
                .isEqualTo(
                        levels.stream().mapToInt(TrainingLevelDef::weight).max().orElseThrow());
        assertThat(levels.getFirst().weight() * 10).isLessThanOrEqualTo(total);
        assertThat(levels.getLast().weight() * 10).isLessThanOrEqualTo(total);
    }

    @Test
    void advantageStaysModerateForEveryRegimeGdpAndDevelopment() {
        for (IdeologyDef ideology : PACK.ideologies().values()) {
            for (SubIdeologyDef sub : ideology.subIdeologies()) {
                Regime regime = new Regime(ideology, sub, new TreeSet<>(), List.of());
                int advantage = Modifiers.advantage(regime.modifiers(), ArmyTrainingWheel.KIND, 0)
                        .value();
                // Лад лише зсуває шанси: гранична перевага лишається для подій і людей.
                assertThat(advantage).as(sub.id().value()).isBetween(-50, 50);
            }
        }
        int perStep = PACK.balance().generation().armyTrainingGdpAdvantage();
        for (GdpLevelDef level : PACK.gdpLevels()) {
            assertThat(level.tier().step() * perStep).as(level.id().value()).isBetween(-50, 50);
        }
        int perLevel = PACK.balance().generation().armyTrainingDevelopmentAdvantage();
        assertThat(Development.MIN * perLevel).isBetween(-50, 50);
        assertThat(Development.MAX * perLevel).isBetween(-50, 50);
        assertThat(perStep).isLessThan(Advantage.MAX);
        assertThat(perLevel).isLessThan(Advantage.MAX);
    }

    @Test
    void everyLevelIsReachableInTheChain() {
        TreeMap<Integer, Integer> counts = new TreeMap<>();
        for (long seed = 0; seed < SEEDS; seed++) {
            counts.merge(chain(seed).training().level(), 1, Integer::sum);
        }

        assertThat(counts.keySet())
                .containsExactlyElementsOf(PACK.trainingLevels().keySet());
    }

    @Test
    void advancedMilitaryTrainsBetterOnAverage() {
        long advancedSum = 0;
        long advancedCount = 0;
        long backwardSum = 0;
        long backwardCount = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            Chain chain = chain(seed);
            int military = chain.development().level(TechBranch.MILITARY);
            if (military > Development.WORLD) {
                advancedSum += chain.training().level();
                advancedCount++;
            } else if (military < Development.WORLD) {
                backwardSum += chain.training().level();
                backwardCount++;
            }
        }

        assertThat(advancedCount).isPositive();
        assertThat(backwardCount).isPositive();
        assertThat(advancedSum * backwardCount).isGreaterThan(backwardSum * advancedCount);
    }

    @Test
    void chainStillLeadsBackstory() {
        List<CountryId> neighbors = List.of(CountryId.of(2), CountryId.of(5));
        for (long seed = 0; seed < 500; seed++) {
            Chain chain = chain(seed);
            TreeSet<String> tags = new TreeSet<>(chain.regime().tags());
            tags.addAll(chain.development().tags());
            tags.addAll(chain.gdp().tags());
            tags.addAll(chain.army().tags());
            tags.addAll(chain.training().tags());
            Backstory backstory = BackstoryWheel.generate(Rng.of(seed).fork("backstory"), PACK, tags, neighbors);

            assertThat(backstory.entries()).as("seed %d", seed).isNotEmpty();
            assertThat(backstory.tags()).containsAll(chain.training().tags());
        }
    }

    private static Chain chain(long seed) {
        Rng rng = Rng.of(seed);
        Regime regime = RegimeWheel.generate(rng.fork("regime"), PACK);
        StartDevelopment development = DevelopmentWheel.generate(rng.fork("development"), PACK, regime.modifiers());
        StartGdp gdp = GdpWheel.generate(rng.fork("gdp"), PACK, regime.modifiers(), development);
        StartArmySize army = ArmySizeWheel.generate(rng.fork("army_size"), PACK, regime.modifiers(), gdp);
        StartArmyTraining training =
                ArmyTrainingWheel.generate(rng.fork("army_training"), PACK, regime.modifiers(), gdp, development);
        return new Chain(regime, development, gdp, army, training);
    }

    private record Chain(
            Regime regime,
            StartDevelopment development,
            StartGdp gdp,
            StartArmySize army,
            StartArmyTraining training) {}
}
