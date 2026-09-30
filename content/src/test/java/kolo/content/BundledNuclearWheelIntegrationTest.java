package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.ModifierDef;
import kolo.engine.content.ResourceDef;
import kolo.engine.content.ResourceId;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.generation.country.ArmySizeWheel;
import kolo.engine.generation.country.ArmyTrainingWheel;
import kolo.engine.generation.country.Backstory;
import kolo.engine.generation.country.BackstoryWheel;
import kolo.engine.generation.country.DevelopmentWheel;
import kolo.engine.generation.country.GdpWheel;
import kolo.engine.generation.country.HdiWheel;
import kolo.engine.generation.country.NuclearWheel;
import kolo.engine.generation.country.Regime;
import kolo.engine.generation.country.RegimeWheel;
import kolo.engine.generation.country.StartDevelopment;
import kolo.engine.generation.country.StartNuclear;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.modifier.Modifiers;
import kolo.engine.rng.Rng;
import kolo.engine.state.CountryId;
import kolo.engine.state.Development;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.TechBranch;
import kolo.engine.wheel.WheelKind;
import org.junit.jupiter.api.Test;

/**
 * Колесо ядерного статусу на вбудованому контенті: ваги за GD §4.6, уран — ядерне паливо, модифікатори ладу влучають
 * у справжні колеса, кожен статус досяжний у ланцюжку лад → розвиненість → ядерний статус, а мітки статусу ведуть
 * передісторію.
 */
class BundledNuclearWheelIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();
    private static final ResourceId URANIUM = new ResourceId("uranium");
    private static final int SEEDS = 5_000;

    @Test
    void weightsMatchDesign() {
        // GD §4.6: ваги 72/13/15 — арсенал можливий лише за урану й енергетики, тож його вага вища за частку.
        assertThat(PACK.nuclearStatus(NuclearStatus.NONE).weight()).isEqualTo(72);
        assertThat(PACK.nuclearStatus(NuclearStatus.PROGRAM).weight()).isEqualTo(13);
        assertThat(PACK.nuclearStatus(NuclearStatus.ARSENAL).weight()).isEqualTo(15);
    }

    @Test
    void qualityGrowsTowardsArsenalWithoutPunishingItsAbsence() {
        int none = PACK.nuclearStatus(NuclearStatus.NONE).quality();
        int program = PACK.nuclearStatus(NuclearStatus.PROGRAM).quality();
        int arsenal = PACK.nuclearStatus(NuclearStatus.ARSENAL).quality();

        assertThat(none)
                .isLessThan(program)
                .isGreaterThan(PACK.balance().streaks().veryBadQuality());
        assertThat(program).isLessThan(arsenal);
    }

    @Test
    void onlyUraniumIsNuclearFuel() {
        List<ResourceId> fuel = PACK.resources().values().stream()
                .filter(resource -> resource.tags().contains(NuclearWheel.NUCLEAR_FUEL_TAG))
                .map(ResourceDef::id)
                .toList();

        assertThat(fuel).containsExactly(URANIUM);
    }

    @Test
    void generationWheelModifiersTargetRealWheels() {
        // Тип колеса — довільний рядок: одрук мовчки вимкнув би модифікатор.
        TreeSet<WheelKind> kinds = new TreeSet<>();
        Arrays.stream(TechBranch.values()).map(DevelopmentWheel::kind).forEach(kinds::add);
        kinds.add(NuclearWheel.KIND);
        kinds.add(GdpWheel.KIND);
        kinds.add(HdiWheel.KIND);
        kinds.add(ArmySizeWheel.KIND);
        kinds.add(ArmyTrainingWheel.KIND);
        int nuclear = 0;
        for (IdeologyDef ideology : PACK.ideologies().values()) {
            List<ModifierDef> all = new ArrayList<>(ideology.modifiers());
            ideology.subIdeologies().forEach(sub -> all.addAll(sub.modifiers()));
            for (ModifierDef modifier : all) {
                if (modifier.target() instanceof ModifierTarget.WheelTarget(WheelKind kind)
                        && kind.id().startsWith("generation_")) {
                    assertThat(kinds).as(kind.id()).contains(kind);
                    nuclear += kind.equals(NuclearWheel.KIND) ? 1 : 0;
                }
            }
        }
        assertThat(nuclear).isPositive();
    }

    @Test
    void advantageStaysModerateForEveryRegimeAndEnergyLevel() {
        for (IdeologyDef ideology : PACK.ideologies().values()) {
            for (SubIdeologyDef sub : ideology.subIdeologies()) {
                Regime regime = new Regime(ideology, sub, new TreeSet<>(), List.of());
                int regimeAdvantage = Modifiers.advantage(regime.modifiers(), NuclearWheel.KIND, 0)
                        .value();
                // Лад лише зсуває шанси: гранична перевага лишається для подій і людей.
                assertThat(regimeAdvantage).as(sub.id().value()).isBetween(-50, 50);
            }
        }
        int energy = PACK.balance().generation().nuclearEnergyAdvantage();
        assertThat(Development.MIN * energy).isGreaterThanOrEqualTo(-50);
        assertThat(Development.MAX * energy).isLessThanOrEqualTo(50);
    }

    @Test
    void arsenalIsSeveralWarheads() {
        // GD §4.6: «кілька боєголовок».
        assertThat(PACK.balance().generation().warheads().min()).isGreaterThan(1);
    }

    @Test
    void everyStatusIsReachableInTheChainAndRulesHold() {
        int[] counts = new int[NuclearStatus.values().length];
        for (long seed = 0; seed < SEEDS; seed++) {
            Rng rng = Rng.of(seed);
            Regime regime = RegimeWheel.generate(rng.fork("regime"), PACK);
            StartDevelopment development = DevelopmentWheel.generate(rng.fork("development"), PACK, regime.modifiers());
            boolean uranium = seed % 2 == 0;
            StartNuclear nuclear = NuclearWheel.generate(
                    rng.fork("nuclear"),
                    PACK,
                    regime.modifiers(),
                    development,
                    uranium ? List.of(URANIUM) : List.of(new ResourceId("iron")));
            counts[nuclear.status().ordinal()]++;

            if (nuclear.status() == NuclearStatus.ARSENAL) {
                assertThat(uranium).isTrue();
                assertThat(development.level(TechBranch.ENERGY_SCIENCE))
                        .isGreaterThanOrEqualTo(NuclearWheel.ARSENAL_MIN_ENERGY_LEVEL);
                assertThat(PACK.balance().generation().warheads().contains(nuclear.warheads()))
                        .isTrue();
            }
        }

        assertThat(counts).doesNotContain(0);
    }

    @Test
    void nuclearTagsLeadBackstory() {
        List<CountryId> neighbors = List.of(CountryId.of(2), CountryId.of(5));
        for (long seed = 0; seed < 500; seed++) {
            Rng rng = Rng.of(seed);
            Regime regime = RegimeWheel.generate(rng.fork("regime"), PACK);
            StartDevelopment development = DevelopmentWheel.generate(rng.fork("development"), PACK, regime.modifiers());
            StartNuclear nuclear =
                    NuclearWheel.generate(rng.fork("nuclear"), PACK, regime.modifiers(), development, List.of(URANIUM));
            TreeSet<String> tags = new TreeSet<>(regime.tags());
            tags.addAll(development.tags());
            tags.addAll(nuclear.tags());
            Backstory backstory = BackstoryWheel.generate(rng.fork("backstory"), PACK, tags, neighbors);

            assertThat(backstory.entries()).as("seed %d", seed).isNotEmpty();
            assertThat(backstory.tags()).containsAll(nuclear.tags());
        }
    }
}
