package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import kolo.content.loader.ContentLoader;
import kolo.content.loader.ContentSource;
import kolo.engine.content.ContentPack;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.IdeologyId;
import kolo.engine.content.ModifierDef;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.content.TechBranchDef;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierSource;
import kolo.engine.modifier.Modifiers;
import kolo.engine.modifier.SourceKind;
import kolo.engine.state.CountryStats;
import kolo.engine.state.Development;
import kolo.engine.state.NuclearStatus;
import kolo.engine.state.Stat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Вбудований контент ↔ рушій: файли гри відповідають дизайну й дають валідні модифікатори. */
class BundledContentIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();

    @Test
    void ideologiesAndSubIdeologiesMatchDesign() {
        // GD §4.2: 6 ідеологій, у кожної 3–4 підкласифікації.
        assertThat(PACK.ideologies().keySet())
                .extracting(IdeologyId::value)
                .containsExactly(
                        "authoritarianism", "democracy", "monarchy", "socialism", "theocracy", "totalitarianism");
        assertThat(subIds("democracy"))
                .containsExactly(
                        "liberal_democracy", "social_democracy", "direct_democracy", "constitutional_monarchy");
        assertThat(subIds("totalitarianism"))
                .containsExactly("personality_cult", "revanchism", "militarism", "isolationism");
        assertThat(PACK.ideologies().values())
                .allSatisfy(ideology -> assertThat(ideology.subIdeologies()).hasSizeBetween(3, 4));
        // GD §4.2: реваншизм додає тег revanchism.
        assertThat(PACK.subIdeology(new SubIdeologyId("revanchism"))
                        .orElseThrow()
                        .tags())
                .contains("revanchism");
    }

    @Test
    void doctrinesAndResourcesMatchDesign() {
        // GD §4.4 і §4.5.
        assertThat(PACK.doctrines()).hasSize(12);
        assertThat(PACK.resources().keySet())
                .extracting(id -> id.value())
                .containsExactlyInAnyOrder(
                        "iron",
                        "coal",
                        "oil",
                        "gas",
                        "uranium",
                        "copper",
                        "gold",
                        "rare_earths",
                        "timber",
                        "fertile_land");
    }

    @Test
    void developmentAndNuclearStatusesMatchDesign() {
        // GD §4.3: чотири галузі, рівні від −3 до +2.
        assertThat(PACK.techBranches().values())
                .extracting(TechBranchDef::name)
                .containsExactly("Економіка", "Військо", "Суспільство", "Енергетика й наука");
        assertThat(PACK.developmentLevels().keySet()).containsExactly(-3, -2, -1, 0, 1, 2);
        assertThat(PACK.developmentLevel(Development.WORLD).name()).isEqualTo("Світовий рівень");
        assertThat(PACK.developmentLevel(Development.MAX).name()).isEqualTo("Лідер");
        // GD §4.6: арсенал дає тег nuclear_power.
        assertThat(PACK.nuclearStatus(NuclearStatus.ARSENAL).tags()).contains("nuclear_power");
        assertThat(PACK.nuclearStatus(NuclearStatus.NONE).tags()).doesNotContain("nuclear_power");
    }

    @Test
    void everyIdeologyCombinationKeepsStatsInBounds() {
        CountryStats middle = new CountryStats(1_000_000, 50, 50, 50, 50, 50, 100);
        CountryStats extreme = new CountryStats(0, 0, 100, 0, 100, 0, 0);

        for (IdeologyDef ideology : PACK.ideologies().values()) {
            for (SubIdeologyDef sub : ideology.subIdeologies()) {
                List<Modifier> modifiers = new ArrayList<>();
                addAll(
                        modifiers,
                        ideology.modifiers(),
                        SourceKind.IDEOLOGY,
                        ideology.id().value());
                addAll(modifiers, sub.modifiers(), SourceKind.IDEOLOGY, sub.id().value());

                for (CountryStats base : List.of(middle, extreme)) {
                    CountryStats effective = Modifiers.effective(base, modifiers, 0);
                    for (Stat stat : Stat.values()) {
                        assertThat(stat.of(effective)).isBetween(stat.min(), stat.max());
                    }
                }
            }
        }
    }

    @Test
    void directorySourceGivesSameContentAndHash(@TempDir Path dir) throws IOException {
        for (String file : ContentLoader.FILES) {
            try (InputStream in = BundledContentIntegrationTest.class.getResourceAsStream("/content/" + file)) {
                assertThat(in).as(file).isNotNull();
                Files.write(dir.resolve(file), in.readAllBytes());
            }
        }

        ContentPack fromDisk = ContentLoader.load(ContentSource.directory(dir));

        assertThat(fromDisk.hash()).isEqualTo(PACK.hash());
        assertThat(fromDisk.ideologies()).isEqualTo(PACK.ideologies());
        assertThat(fromDisk.doctrines()).isEqualTo(PACK.doctrines());
        assertThat(fromDisk.resources()).isEqualTo(PACK.resources());
        assertThat(fromDisk.techBranches()).isEqualTo(PACK.techBranches());
        assertThat(fromDisk.developmentLevels()).isEqualTo(PACK.developmentLevels());
        assertThat(fromDisk.nuclearStatuses()).isEqualTo(PACK.nuclearStatuses());
    }

    private static List<String> subIds(String ideology) {
        return PACK.ideology(new IdeologyId(ideology)).orElseThrow().subIdeologies().stream()
                .map(sub -> sub.id().value())
                .toList();
    }

    private static void addAll(List<Modifier> target, List<ModifierDef> defs, SourceKind kind, String refId) {
        for (int i = 0; i < defs.size(); i++) {
            target.add(defs.get(i)
                    .toModifier(refId + ":" + i, new ModifierSource(kind, refId), null, "ideology." + refId));
        }
    }
}
