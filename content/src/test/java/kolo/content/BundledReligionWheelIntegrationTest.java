package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import kolo.content.loader.ContentLoader;
import kolo.engine.content.ArchetypeDef;
import kolo.engine.content.ArchetypeId;
import kolo.engine.content.ContentPack;
import kolo.engine.content.CountRange;
import kolo.engine.content.FaithFormDef;
import kolo.engine.content.ReligionContent;
import kolo.engine.content.ReligionCountDef;
import kolo.engine.generation.religion.ReligionWheel;
import kolo.engine.generation.religion.StartReligion;
import kolo.engine.generation.religion.StartReligions;
import kolo.engine.generation.religion.WorldReligionsWheel;
import kolo.engine.rng.Rng;
import kolo.engine.state.GrammaticalCase;
import kolo.engine.state.Sex;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.WheelKind;
import org.junit.jupiter.api.Test;

/** Вбудований шаблон релігій і баланс ↔ генератор релігій світу рушія. */
class BundledReligionWheelIntegrationTest {

    private static final ContentPack PACK = ContentLoader.loadBundled();
    private static final ReligionContent RELIGIONS = PACK.religions();

    /** Найбільша кількість держав у світі (GD §3.2). */
    private static final int MAX_COUNTRIES = 40;

    @Test
    void religionCountMatchesDesign() {
        // GD §25.1: запропоновано 3–6 релігій; таблиця покриває кожну кількість держав до максимуму.
        for (ReligionCountDef row : PACK.balance().religion().count()) {
            assertThat(row.religions().min()).isGreaterThanOrEqualTo(3);
            assertThat(row.religions().max()).isLessThanOrEqualTo(6);
        }
        assertThat(PACK.balance().religion().count().getLast().maxCountries()).isGreaterThanOrEqualTo(MAX_COUNTRIES);
        // Більше держав — не менше релігій.
        CountRange previous = null;
        for (int countries = 1; countries <= MAX_COUNTRIES; countries++) {
            CountRange range = PACK.balance().religion().religions(countries);
            if (previous != null) {
                assertThat(range.min()).isGreaterThanOrEqualTo(previous.min());
                assertThat(range.max()).isGreaterThanOrEqualTo(previous.max());
            }
            previous = range;
        }
    }

    @Test
    void figureSexesFitRole() {
        // Роль із чоловічим словом («Праотець», «Учитель») — лише чоловік; обидві статі — там, де роль не має роду.
        assertThat(figureSexes("ancestor_cult")).containsExactly(Sex.MALE);
        assertThat(figureSexes("philosophy")).containsExactly(Sex.MALE);
        assertThat(figureSexes("polytheism")).containsExactlyInAnyOrder(Sex.MALE, Sex.FEMALE);
    }

    @Test
    void worldsOfEverySizeGetWellFormedReligions() {
        for (int countries = 1; countries <= MAX_COUNTRIES; countries++) {
            for (long seed = 0; seed < 25; seed++) {
                StartReligions world = WorldReligionsWheel.generate(Rng.of(seed), PACK, countries);

                assertThat(PACK.balance()
                                .religion()
                                .religions(countries)
                                .contains(world.religions().size()))
                        .isTrue();
                TreeSet<String> names = new TreeSet<>();
                for (StartReligion religion : world.religions()) {
                    assertWellFormed(religion);
                    assertThat(names.add(religion.name().nominative())).isTrue();
                }
            }
        }
    }

    @Test
    void partsNeverRunShort() {
        // Вбудований шаблон завжди дає стільки аспектів і догматів, скільки випало на колесі кількості.
        for (long seed = 0; seed < 5000; seed++) {
            StartReligion religion = ReligionWheel.generate(Rng.of(seed), PACK, Set.of());

            assertThat(religion.aspects()).hasSize(rolled(religion, ReligionWheel.ASPECT_COUNT_KIND, "aspects_"));
            assertThat(religion.dogmas()).hasSize(rolled(religion, ReligionWheel.DOGMA_COUNT_KIND, "dogmas_"));
        }
    }

    @Test
    void everyArchetypeFormAndSexAppears() {
        TreeMap<ArchetypeId, TreeSet<String>> forms = new TreeMap<>();
        TreeMap<ArchetypeId, TreeSet<Sex>> sexes = new TreeMap<>();
        for (long seed = 0; seed < 5000; seed++) {
            StartReligion religion = ReligionWheel.generate(Rng.of(seed), PACK, Set.of());
            forms.computeIfAbsent(religion.archetype(), id -> new TreeSet<>())
                    .add(religion.faithForm().value());
            sexes.computeIfAbsent(religion.archetype(), id -> new TreeSet<>()).add(religion.figureSex());
        }
        for (ArchetypeDef archetype : RELIGIONS.archetypes()) {
            assertThat(forms.get(archetype.id()))
                    .as("форми %s", archetype.id())
                    .containsExactlyInAnyOrderElementsOf(RELIGIONS.faithFormsFor(archetype.id()).stream()
                            .map(form -> form.id().value())
                            .toList());
            assertThat(sexes.get(archetype.id())).containsExactlyInAnyOrderElementsOf(archetype.figureSexes());
        }
    }

    @Test
    void crowdedTemplateStillHasFreeNames() {
        // 500 релігій в одному світі — набагато більше, ніж буде з розколами.
        TreeSet<String> taken = new TreeSet<>();
        for (long seed = 0; seed < 500; seed++) {
            StartReligion religion = ReligionWheel.generate(Rng.of(seed), PACK, taken);
            assertThat(taken.add(religion.name().nominative())).isTrue();
        }
    }

    private static void assertWellFormed(StartReligion religion) {
        ArchetypeDef archetype = RELIGIONS.archetype(religion.archetype()).orElseThrow();
        assertThat(archetype.figureSexes()).contains(religion.figureSex());
        assertThat(PACK.balance()
                        .religion()
                        .aspects()
                        .contains(religion.aspects().size()))
                .isTrue();
        assertThat(PACK.balance().religion().dogmas().contains(religion.dogmas().size()))
                .isTrue();
        for (int i = 0; i < religion.dogmas().size(); i++) {
            for (int j = i + 1; j < religion.dogmas().size(); j++) {
                assertThat(RELIGIONS.compatible(
                                religion.dogmas().get(i), religion.dogmas().get(j)))
                        .isTrue();
            }
        }
        assertThat(religion.tags()).containsAll(archetype.tags());
        FaithFormDef form = RELIGIONS.faithForm(religion.faithForm()).orElseThrow();
        String figure = religion.figure().form(form.figureCase());
        for (GrammaticalCase grammaticalCase : GrammaticalCase.values()) {
            String name = religion.name().form(grammaticalCase);
            assertThat(name).endsWith(" " + figure).doesNotContain("{").doesNotContain("}");
            assertThat(Character.isUpperCase(name.charAt(0))).isTrue();
        }
    }

    private static Set<Sex> figureSexes(String archetype) {
        return Set.copyOf(
                RELIGIONS.archetype(new ArchetypeId(archetype)).orElseThrow().figureSexes());
    }

    private static int rolled(StartReligion religion, WheelKind kind, String prefix) {
        String id = religion.rolls().stream()
                .filter(roll -> roll.kind().equals(kind))
                .map(RollRecord::resultSectorId)
                .findFirst()
                .orElseThrow();
        return Integer.parseInt(id.substring(prefix.length()));
    }
}
