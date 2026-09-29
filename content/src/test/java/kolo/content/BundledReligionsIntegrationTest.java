package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ArchetypeDef;
import kolo.engine.content.AspectDef;
import kolo.engine.content.ContentPack;
import kolo.engine.content.DogmaDef;
import kolo.engine.content.FaithFormDef;
import kolo.engine.content.ReligionContent;
import kolo.engine.content.ReligionPolityDef;
import kolo.engine.state.GrammaticalCase;
import org.junit.jupiter.api.Test;

/** Вбудований шаблон релігій ↔ дизайн: частини з GD §25.1 є, і генератор не впреться в брак частин. */
class BundledReligionsIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();
    private static final ReligionContent RELIGIONS = PACK.religions();

    @Test
    void partsMatchDesign() {
        // GD §25.1: архетипи, аспекти, догмати й устрої з прикладів дизайну.
        assertThat(RELIGIONS.archetypes())
                .extracting(archetype -> archetype.id().value())
                .contains("monotheism", "polytheism", "dualism", "ancestor_cult", "animism", "philosophy");
        assertThat(RELIGIONS.aspects())
                .extracting(aspect -> aspect.id().value())
                .contains("war", "knowledge", "fertility", "death", "order", "sea", "trade")
                // «Машина» — лише через розкол (GD §25.4).
                .doesNotContain("machine");
        assertThat(RELIGIONS.dogmas())
                .extracting(dogma -> dogma.id().value())
                .contains("holy_war", "asceticism", "scholar_honor", "usury_ban", "missionary");
        assertThat(RELIGIONS.polities())
                .extracting(polity -> polity.id().value())
                .containsExactly("single_church", "communities", "no_clergy");
        assertThat(RELIGIONS.producedTags()).contains("religion_war", "dogma_holy_war", "archetype_dualism");
    }

    @Test
    void everyArchetypeCanGetEnoughAspects() {
        int max = PACK.balance().religion().aspects().max();
        for (ArchetypeDef archetype : RELIGIONS.archetypes()) {
            // Будь-які вже обрані аспекти (менше максимуму) лишають досить доступних для решти обертань.
            for (List<AspectDef> chosen : subsets(RELIGIONS.aspects(), max - 1)) {
                TreeSet<String> tags = new TreeSet<>(archetype.tags());
                chosen.forEach(aspect -> tags.addAll(aspect.tags()));
                long available = RELIGIONS.aspects().stream()
                        .filter(aspect -> !chosen.contains(aspect))
                        .filter(aspect -> aspect.weightFor(tags) > 0)
                        .count();
                assertThat(available)
                        .as("%s з аспектами %s", archetype.id(), chosen)
                        .isGreaterThanOrEqualTo(max - chosen.size());
            }
        }
    }

    @Test
    void everyReligionCanGetEnoughCompatibleDogmas() {
        int maxAspects = PACK.balance().religion().aspects().max();
        int maxDogmas = PACK.balance().religion().dogmas().max();
        // Кожен обраний догмат прибирає себе й щонайбільше стільки несумісних, скільки має пар.
        int worstLoss = 1
                + RELIGIONS.dogmas().stream()
                        .mapToInt(dogma -> incompatibleCount(dogma))
                        .max()
                        .orElseThrow();
        for (ArchetypeDef archetype : RELIGIONS.archetypes()) {
            for (List<AspectDef> aspects : subsets(RELIGIONS.aspects(), maxAspects)) {
                TreeSet<String> tags = new TreeSet<>(archetype.tags());
                aspects.forEach(aspect -> tags.addAll(aspect.tags()));
                long available = RELIGIONS.dogmas().stream()
                        .filter(dogma -> dogma.weightFor(tags) > 0)
                        .count();
                // Навіть за найгіршого вибору після maxDogmas − 1 догматів лишається хоча б один доступний.
                assertThat(available)
                        .as("%s з аспектами %s", archetype.id(), aspects)
                        .isGreaterThanOrEqualTo((long) worstLoss * (maxDogmas - 1) + 1);
            }
        }
    }

    @Test
    void everyReligionHasSomePolity() {
        TreeSet<String> everything = new TreeSet<>(RELIGIONS.producedTags());
        for (ArchetypeDef archetype : RELIGIONS.archetypes()) {
            TreeSet<String> tags = new TreeSet<>(archetype.tags());
            assertThat(RELIGIONS.polities()).anyMatch(polity -> polity.weightFor(tags) > 0);
            assertThat(RELIGIONS.polities()).anyMatch(polity -> polity.weightFor(everything) > 0);
        }
        assertThat(RELIGIONS.polities()).extracting(ReligionPolityDef::weight).allMatch(weight -> weight > 0);
    }

    @Test
    void everyArchetypeHasSeveralFaithFormsThatRender() {
        for (ArchetypeDef archetype : RELIGIONS.archetypes()) {
            assertThat(RELIGIONS.faithFormsFor(archetype.id()))
                    .as("форми назви %s", archetype.id())
                    .hasSizeGreaterThanOrEqualTo(2);
        }
        for (FaithFormDef form : RELIGIONS.faithForms()) {
            for (GrammaticalCase grammaticalCase : GrammaticalCase.values()) {
                String name = form.render(grammaticalCase, "Оріна");
                assertThat(name).endsWith(" Оріна").doesNotContain("{").doesNotContain("}");
                assertThat(Character.isUpperCase(name.charAt(0))).isTrue();
            }
        }
    }

    private static int incompatibleCount(DogmaDef dogma) {
        return (int) RELIGIONS.dogmas().stream()
                .filter(other -> !other.equals(dogma))
                .filter(other -> !RELIGIONS.compatible(dogma.id(), other.id()))
                .count();
    }

    /** Усі підмножини розміром до {@code size} включно, у порядку контенту. */
    private static <T> List<List<T>> subsets(List<T> items, int size) {
        List<List<T>> result = new ArrayList<>();
        collect(items, size, 0, new ArrayList<>(), result);
        return result;
    }

    private static <T> void collect(List<T> items, int size, int from, List<T> current, List<List<T>> result) {
        result.add(List.copyOf(current));
        if (current.size() == size) {
            return;
        }
        for (int i = from; i < items.size(); i++) {
            current.add(items.get(i));
            collect(items, size, i + 1, current, result);
            current.removeLast();
        }
    }
}
