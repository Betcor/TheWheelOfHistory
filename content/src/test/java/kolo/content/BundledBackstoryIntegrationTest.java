package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.BackstoryContent;
import kolo.engine.content.BackstoryFragmentDef;
import kolo.engine.content.BackstoryFragmentId;
import kolo.engine.content.ContentPack;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.NuclearStatusDef;
import kolo.engine.content.StreakRulesDef;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.generation.name.CountryNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.LocalizedName;
import org.junit.jupiter.api.Test;

/** Вбудована передісторія ↔ рушій: фрагментів досить кожній державі, тексти складаються з реальних назв. */
class BundledBackstoryIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();
    private static final BackstoryContent BACKSTORY = PACK.backstory();

    /** Скільки фрагментів найбільше обирає колесо передісторії (GD §4.7: 2–4). */
    private static final int MAX_FRAGMENTS =
            PACK.balance().generation().backstoryFragments().max();

    private static final StreakRulesDef STREAKS = PACK.balance().streaks();

    @Test
    void thereAreEnoughFragmentsForStreaksBothWays() {
        // Етап 2: 30+ фрагментів; стріки (GD §4.10) потребують і дуже добрих, і дуже поганих результатів.
        assertThat(BACKSTORY.fragments()).hasSizeGreaterThanOrEqualTo(30);
        assertThat(BACKSTORY.fragments().values()).anyMatch(fragment -> STREAKS.isVeryGood(fragment.quality()));
        assertThat(BACKSTORY.fragments().values()).anyMatch(fragment -> STREAKS.isVeryBad(fragment.quality()));
        assertThat(BACKSTORY.fragments().values()).anyMatch(BackstoryFragmentDef::neighbor);
    }

    @Test
    void everyStartingCountryHasEnoughFragmentsWithoutNeighbor() {
        // Навіть без міток коліс генерації й без сусіда колесу є з чого обрати всі фрагменти.
        for (IdeologyDef ideology : PACK.ideologies().values()) {
            for (SubIdeologyDef sub : ideology.subIdeologies()) {
                for (NuclearStatusDef status : PACK.nuclearStatuses().values()) {
                    TreeSet<String> tags = startingTags(ideology, sub, status);

                    assertThat(BACKSTORY.available(tags, false))
                            .as("%s, %s", sub.id(), status.status().key())
                            .hasSizeGreaterThanOrEqualTo(MAX_FRAGMENTS);
                }
            }
        }
    }

    @Test
    void everyFragmentIsReachable() {
        // Мітки фрагментів відкривають наступні: збираємо все, що доступне хоч якійсь державі по ланцюжку.
        TreeSet<BackstoryFragmentId> reached = new TreeSet<>();
        for (IdeologyDef ideology : PACK.ideologies().values()) {
            for (SubIdeologyDef sub : ideology.subIdeologies()) {
                for (NuclearStatusDef status : PACK.nuclearStatuses().values()) {
                    reach(startingTags(ideology, sub, status), reached);
                    for (String generationTag : BACKSTORY.generationTags().keySet()) {
                        TreeSet<String> tags = startingTags(ideology, sub, status);
                        tags.add(generationTag);
                        reach(tags, reached);
                    }
                }
            }
        }

        assertThat(reached).containsExactlyElementsOf(BACKSTORY.fragments().keySet());
    }

    @Test
    void lostWarChainFollowsDesignExample() {
        // GD §4.7: реваншизм підвищує вагу «програної війни», а вона відкриває контрибуції й жагу реваншу.
        BackstoryFragmentDef lostWar = fragment("lost_war");
        TreeSet<String> revanchist = tags("totalitarian", "revanchism");

        assertThat(lostWar.weightFor(revanchist)).isGreaterThan(lostWar.weightFor(tags("democratic")));
        assertThat(lostWar.available(revanchist, true)).isTrue();

        revanchist.addAll(lostWar.adds());
        assertThat(BACKSTORY.available(revanchist, true))
                .extracting(fragment -> fragment.id().value())
                .contains("reparations", "revanchist_mood")
                .doesNotContain("won_war");
    }

    @Test
    void everyTextRendersWithGeneratedNames() {
        LocalizedName country = CountryNames.generate(Rng.of(7), PACK, new SubIdeologyId("revanchism"));
        LocalizedName neighbor = CountryNames.generate(Rng.of(8), PACK, new SubIdeologyId("absolute_monarchy"));

        for (BackstoryFragmentDef fragment : BACKSTORY.fragments().values()) {
            String text = fragment.text().render(country, Optional.of(neighbor), fragment.yearFrom());

            assertThat(text)
                    .as(fragment.id().value())
                    .doesNotContain("{", "}", "  ")
                    .endsWith(".");
            if (fragment.neighbor()) {
                assertThat(text).as(fragment.id().value()).containsAnyOf(neighborForms(neighbor));
            }
            if (fragment.text().usesYear()) {
                assertThat(text).contains(String.valueOf(fragment.yearFrom()));
            }
        }
    }

    private static void reach(TreeSet<String> tags, TreeSet<BackstoryFragmentId> reached) {
        boolean grown = true;
        while (grown) {
            grown = false;
            for (BackstoryFragmentDef fragment : BACKSTORY.available(tags, true)) {
                reached.add(fragment.id());
                grown |= tags.addAll(fragment.adds());
            }
        }
    }

    private static TreeSet<String> startingTags(IdeologyDef ideology, SubIdeologyDef sub, NuclearStatusDef status) {
        TreeSet<String> tags = new TreeSet<>(ideology.tags());
        tags.addAll(sub.tags());
        tags.addAll(status.tags());
        return tags;
    }

    private static TreeSet<String> tags(String... tags) {
        return new TreeSet<>(List.of(tags));
    }

    private static BackstoryFragmentDef fragment(String id) {
        return BACKSTORY.fragment(new BackstoryFragmentId(id)).orElseThrow();
    }

    private static String[] neighborForms(LocalizedName neighbor) {
        return neighbor.shortName().forms().toArray(String[]::new);
    }
}
