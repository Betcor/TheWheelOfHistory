package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.content.DevelopmentLevelDef;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.ModifierDef;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.generation.country.Backstory;
import kolo.engine.generation.country.BackstoryWheel;
import kolo.engine.generation.country.DevelopmentWheel;
import kolo.engine.generation.country.Regime;
import kolo.engine.generation.country.RegimeWheel;
import kolo.engine.generation.country.StartDevelopment;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.modifier.Modifiers;
import kolo.engine.rng.Rng;
import kolo.engine.state.CountryId;
import kolo.engine.state.Development;
import kolo.engine.state.TechBranch;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.WheelKind;
import org.junit.jupiter.api.Test;

/**
 * Колесо розвиненості на вбудованому контенті: мітки рівнів збігаються зі словником міток генерації, модифікатори
 * ладу влучають у справжні колеса, кожен рівень досяжний, а мітки розвиненості ведуть передісторію.
 */
class BundledDevelopmentWheelIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();
    private static final int SEEDS = 5_000;

    @Test
    void levelTagsMatchGenerationTagsDictionary() {
        // backstory.yaml: backward — хоча б одна галузь на −2 або нижче, advanced — на +1 або вище.
        for (DevelopmentLevelDef level : PACK.developmentLevels().values()) {
            List<String> expected =
                    level.level() <= -2 ? List.of("backward") : level.level() >= 1 ? List.of("advanced") : List.of();
            assertThat(level.tags()).as("level %d", level.level()).isEqualTo(expected);
            assertThat(PACK.backstory().generationTags().keySet()).containsAll(level.tags());
        }
    }

    @Test
    void levelQualityGrowsWithLevel() {
        List<Integer> qualities = PACK.developmentLevels().values().stream()
                .map(DevelopmentLevelDef::quality)
                .toList();

        assertThat(qualities).isSorted().doesNotHaveDuplicates();
    }

    @Test
    void developmentModifiersTargetRealBranchWheels() {
        // Тип колеса — довільний рядок: одрук у назві галузі мовчки вимкнув би модифікатор.
        TreeSet<WheelKind> kinds = new TreeSet<>();
        Arrays.stream(TechBranch.values()).map(DevelopmentWheel::kind).forEach(kinds::add);
        int found = 0;
        for (IdeologyDef ideology : PACK.ideologies().values()) {
            List<ModifierDef> all = new ArrayList<>(ideology.modifiers());
            ideology.subIdeologies().forEach(sub -> all.addAll(sub.modifiers()));
            for (ModifierDef modifier : all) {
                if (modifier.target() instanceof ModifierTarget.WheelTarget(WheelKind kind)
                        && kind.id().startsWith(DevelopmentWheel.KIND_PREFIX)) {
                    assertThat(kinds).as(kind.id()).contains(kind);
                    found++;
                }
            }
        }
        assertThat(found).isPositive();
    }

    @Test
    void regimeAdvantageStaysWithinBoundsForEverySubIdeology() {
        for (IdeologyDef ideology : PACK.ideologies().values()) {
            for (SubIdeologyDef sub : ideology.subIdeologies()) {
                Regime regime = new Regime(ideology, sub, new TreeSet<>(), List.of());
                for (TechBranch branch : TechBranch.values()) {
                    int advantage = Modifiers.advantage(regime.modifiers(), DevelopmentWheel.kind(branch), 0)
                            .value();
                    // Лад лише зсуває шанси, а не визначає рівень: гранична перевага лишається для подій і людей.
                    assertThat(advantage).as("%s / %s", sub.id(), branch.key()).isBetween(-50, 50);
                    assertThat(advantage).isBetween(Advantage.MIN, Advantage.MAX);
                }
            }
        }
    }

    @Test
    void everyLevelIsReachableInEveryBranch() {
        int[][] counts = new int[TechBranch.values().length][Development.MAX - Development.MIN + 1];
        for (long seed = 0; seed < SEEDS; seed++) {
            Rng rng = Rng.of(seed);
            Regime regime = RegimeWheel.generate(rng.fork("regime"), PACK);
            StartDevelopment development = DevelopmentWheel.generate(rng.fork("development"), PACK, regime.modifiers());
            for (TechBranch branch : TechBranch.values()) {
                counts[branch.ordinal()][development.level(branch) - Development.MIN]++;
            }
        }

        for (TechBranch branch : TechBranch.values()) {
            assertThat(counts[branch.ordinal()]).as(branch.key()).doesNotContain(0);
        }
    }

    @Test
    void developmentTagsLeadBackstory() {
        List<CountryId> neighbors = List.of(CountryId.of(2), CountryId.of(5));
        for (long seed = 0; seed < 500; seed++) {
            Rng rng = Rng.of(seed);
            Regime regime = RegimeWheel.generate(rng.fork("regime"), PACK);
            StartDevelopment development = DevelopmentWheel.generate(rng.fork("development"), PACK, regime.modifiers());
            TreeSet<String> tags = new TreeSet<>(regime.tags());
            tags.addAll(development.tags());
            Backstory backstory = BackstoryWheel.generate(rng.fork("backstory"), PACK, tags, neighbors);

            assertThat(development.quality()).isBetween(0, 100);
            assertThat(backstory.entries()).as("seed %d", seed).isNotEmpty();
            assertThat(backstory.tags()).containsAll(development.tags());
        }
    }
}
