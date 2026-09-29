package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.TreeSet;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.IdeologyId;
import kolo.engine.content.SecularStateDef;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.generation.country.Regime;
import kolo.engine.generation.country.RegimeWheel;
import kolo.engine.generation.country.StartStateReligion;
import kolo.engine.generation.country.StateReligionWheel;
import kolo.engine.generation.religion.StartReligion;
import kolo.engine.generation.religion.WorldReligionsWheel;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.SourceKind;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.RolledSector;
import org.junit.jupiter.api.Test;

/** Вбудований контент ↔ колесо релігії держави рушія. */
class BundledStateReligionWheelIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();
    private static final SecularStateDef SECULAR =
            PACK.religions().stateReligion().secular();
    private static final IdeologyId THEOCRACY = new IdeologyId("theocracy");
    private static final IdeologyId SOCIALISM = new IdeologyId("socialism");

    @Test
    void theocracyIsNeverSecularAndOtherRegimesMayBe() {
        // GD §25.2: теократія світською бути не може.
        for (IdeologyDef ideology : PACK.ideologiesInContentOrder()) {
            for (SubIdeologyDef sub : ideology.subIdeologies()) {
                TreeSet<String> tags = regimeTags(ideology, sub);
                assertThat(SECULAR.weightFor(tags) > 0)
                        .as("світська держава для %s", sub.id())
                        .isEqualTo(!ideology.id().equals(THEOCRACY));
            }
        }
    }

    @Test
    void socialismTendsToSecularState() {
        // GD §25.2: соціалізм тяжіє до світськості — будь-яка його підкласифікація важча за будь-яку несоціалістичну.
        int socialistMin = Integer.MAX_VALUE;
        int otherMax = 0;
        for (IdeologyDef ideology : PACK.ideologiesInContentOrder()) {
            for (SubIdeologyDef sub : ideology.subIdeologies()) {
                int weight = SECULAR.weightFor(regimeTags(ideology, sub));
                if (ideology.id().equals(SOCIALISM)) {
                    socialistMin = Math.min(socialistMin, weight);
                } else {
                    otherMax = Math.max(otherMax, weight);
                }
            }
        }
        assertThat(socialistMin).isGreaterThan(otherMax);
    }

    @Test
    void chainFromRegimeGivesWellFormedReligion() {
        TreeSet<String> results = new TreeSet<>();
        for (long seed = 0; seed < 2000; seed++) {
            Rng rng = Rng.of(seed);
            List<StartReligion> religions = WorldReligionsWheel.generate(rng.fork("religions"), PACK, 12)
                    .religions();
            Regime regime = RegimeWheel.generate(rng.fork("regime"), PACK);

            StartStateReligion result =
                    StateReligionWheel.generate(rng.fork("state_religion"), PACK, regime.tags(), religions);

            if (regime.ideology().id().equals(THEOCRACY)) {
                assertThat(result.secular()).isFalse();
            }
            assertThat(PACK.religions().producedTags()).containsAll(result.tags());
            if (result.secular()) {
                assertThat(result.tags()).containsExactlyElementsOf(SECULAR.tags());
                assertThat(result.modifiers()).isEmpty();
            } else {
                StartReligion religion = religions.get(result.religion().getAsInt());
                assertThat(result.tags()).isEqualTo(religion.tags());
                assertThat(result.modifiers()).extracting(Modifier::id).doesNotHaveDuplicates();
                assertThat(result.modifiers())
                        .allMatch(modifier ->
                                modifier.source().kind() == SourceKind.RELIGION && modifier.expiresAtTurn() == null);
            }
            results.add(result.secular() ? "secular" : "religion");
        }
        assertThat(results).containsExactly("religion", "secular");
    }

    @Test
    void worldReligionsShareEqually() {
        List<StartReligion> religions =
                WorldReligionsWheel.generate(Rng.of(1970), PACK, 12).religions();
        List<RolledSector> sectors = StateReligionWheel.generate(Rng.of(0), PACK, new TreeSet<>(), religions)
                .rolls()
                .getFirst()
                .sectors();

        assertThat(sectors).hasSize(religions.size() + 1);
        // Нормалізація найбільшими залишками може дати різницю в 1 bp.
        int first = sectors.getFirst().weightBp();
        assertThat(sectors.subList(0, religions.size()))
                .allSatisfy(sector -> assertThat(sector.weightBp()).isBetween(first - 1, first + 1));
    }

    private static TreeSet<String> regimeTags(IdeologyDef ideology, SubIdeologyDef sub) {
        TreeSet<String> tags = new TreeSet<>(ideology.tags());
        tags.addAll(sub.tags());
        return tags;
    }
}
