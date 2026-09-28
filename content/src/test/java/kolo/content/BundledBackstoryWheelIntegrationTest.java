package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.content.CountRange;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.NuclearStatusDef;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.generation.country.Backstory;
import kolo.engine.generation.country.BackstoryEntry;
import kolo.engine.generation.country.BackstoryWheel;
import kolo.engine.generation.name.CountryNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.CountryId;
import kolo.engine.state.LocalizedName;
import org.junit.jupiter.api.Test;

/** Колесо передісторії на вбудованому контенті: фрагментів не бракує жодній державі, тексти складаються. */
class BundledBackstoryWheelIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();
    private static final CountRange COUNT = PACK.balance().generation().backstoryFragments();
    private static final List<CountryId> NEIGHBORS = List.of(CountryId.of(2), CountryId.of(5), CountryId.of(9));

    /** Скільки seed-ів на кожну комбінацію підкласифікації, ядерного статусу й наявності сусідів. */
    private static final int SEEDS = 40;

    @Test
    void everyStartingCountryGetsTheRolledNumberOfFragments() {
        int generated = 0;
        for (IdeologyDef ideology : PACK.ideologies().values()) {
            for (SubIdeologyDef sub : ideology.subIdeologies()) {
                for (NuclearStatusDef status : PACK.nuclearStatuses().values()) {
                    TreeSet<String> tags = new TreeSet<>(ideology.tags());
                    tags.addAll(sub.tags());
                    tags.addAll(status.tags());
                    for (List<CountryId> neighbors : List.of(List.<CountryId>of(), NEIGHBORS)) {
                        for (long seed = 0; seed < SEEDS; seed++) {
                            // Мітки коліс генерації ще не видаються колесами — додаємо їх по черзі, щоб умови
                            // з ними теж працювали.
                            TreeSet<String> withGeneration = new TreeSet<>(tags);
                            withGeneration.add(generationTag(seed));
                            Backstory backstory =
                                    BackstoryWheel.generate(Rng.of(seed), PACK, withGeneration, neighbors);

                            assertThat(backstory.entries())
                                    .as(
                                            "%s, %s, seed %d",
                                            sub.id(), status.status().key(), seed)
                                    .hasSize(rolledCount(backstory));
                            assertThat(COUNT.contains(backstory.entries().size()))
                                    .isTrue();
                            generated++;
                        }
                    }
                }
            }
        }
        assertThat(generated).isGreaterThan(1000);
    }

    @Test
    void fragmentsWithNeighborAppearAndRenderWithRealNames() {
        LocalizedName country = CountryNames.generate(Rng.of(1), PACK, sub("revanchism"));
        LocalizedName neighborName = CountryNames.generate(Rng.of(2), PACK, sub("absolute_monarchy"));
        TreeSet<String> tags = new TreeSet<>(List.of("totalitarian", "revanchism"));

        int withNeighbor = 0;
        for (long seed = 0; seed < 500; seed++) {
            Backstory backstory = BackstoryWheel.generate(Rng.of(seed), PACK, tags, NEIGHBORS);
            if (backstory.neighbor().isPresent()) {
                withNeighbor++;
                assertThat(NEIGHBORS).contains(backstory.neighbor().orElseThrow());
            }
            Optional<LocalizedName> neighbor = backstory.neighbor().map(id -> neighborName);
            for (BackstoryEntry entry : backstory.entries()) {
                assertThat(entry.text(country, neighbor))
                        .doesNotContain("{", "}")
                        .endsWith(".");
            }
        }
        assertThat(withNeighbor).isPositive();
    }

    private static int rolledCount(Backstory backstory) {
        String sector = backstory.rolls().getFirst().resultSectorId();
        return Integer.parseInt(sector.substring("fragments_".length()));
    }

    private static String generationTag(long seed) {
        List<String> tags = new ArrayList<>(PACK.backstory().generationTags().keySet());
        return tags.get((int) (seed % tags.size()));
    }

    private static SubIdeologyId sub(String id) {
        return new SubIdeologyId(id);
    }
}
