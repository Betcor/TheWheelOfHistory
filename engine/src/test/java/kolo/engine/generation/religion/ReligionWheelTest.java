package kolo.engine.generation.religion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import kolo.engine.content.ArchetypeDef;
import kolo.engine.content.ArchetypeId;
import kolo.engine.content.AspectDef;
import kolo.engine.content.AspectId;
import kolo.engine.content.ContentPack;
import kolo.engine.content.CountRange;
import kolo.engine.content.DogmaDef;
import kolo.engine.content.DogmaId;
import kolo.engine.content.FaithFormDef;
import kolo.engine.content.FaithFormId;
import kolo.engine.content.ReligionContent;
import kolo.engine.content.ReligionPolityDef;
import kolo.engine.content.ReligionPolityId;
import kolo.engine.content.TestReligions;
import kolo.engine.error.InvariantViolationException;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.GrammaticalCase;
import kolo.engine.state.GrammaticalGender;
import kolo.engine.state.NounPhrase;
import kolo.engine.state.Sex;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import kolo.engine.wheel.WheelKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReligionWheelTest {

    private static final ContentPack PACK = TestNames.PACK;
    private static final ReligionContent RELIGIONS = PACK.religions();
    private static final ArchetypeDef MONOTHEISM =
            RELIGIONS.archetype(new ArchetypeId("monotheism")).orElseThrow();
    private static final ArchetypeDef POLYTHEISM =
            RELIGIONS.archetype(new ArchetypeId("polytheism")).orElseThrow();
    private static final AspectId WAR = new AspectId("war");
    private static final AspectId KNOWLEDGE = new AspectId("knowledge");
    private static final AspectId SEA = new AspectId("sea");
    private static final DogmaId HOLY_WAR = new DogmaId("holy_war");
    private static final DogmaId PACIFISM = new DogmaId("pacifism");
    private static final DogmaId SCHOLAR_HONOR = new DogmaId("scholar_honor");

    @Test
    void sameSeedGivesSameReligion() {
        assertThat(generate(42)).isEqualTo(generate(42));
    }

    @Test
    void rollsGoInOrderOfThrows() {
        for (long seed = 0; seed < 50; seed++) {
            StartReligion religion = generate(seed);
            List<RollRecord> rolls = religion.rolls();

            List<WheelKind> kinds = new ArrayList<>();
            List<String> results = new ArrayList<>();
            kinds.add(ReligionWheel.ARCHETYPE_KIND);
            results.add(religion.archetype().value());
            kinds.add(ReligionWheel.ASPECT_COUNT_KIND);
            results.add("aspects_" + religion.aspects().size());
            religion.aspects().forEach(aspect -> {
                kinds.add(ReligionWheel.ASPECT_KIND);
                results.add(aspect.value());
            });
            kinds.add(ReligionWheel.DOGMA_COUNT_KIND);
            results.add("dogmas_" + religion.dogmas().size());
            religion.dogmas().forEach(dogma -> {
                kinds.add(ReligionWheel.DOGMA_KIND);
                results.add(dogma.value());
            });
            kinds.add(ReligionWheel.POLITY_KIND);
            results.add(religion.polity().value());
            kinds.add(ReligionWheel.FAITH_FORM_KIND);
            results.add(religion.faithForm().value());

            assertThat(rolls).extracting(RollRecord::kind).containsExactlyElementsOf(kinds);
            assertThat(rolls).extracting(RollRecord::resultSectorId).containsExactlyElementsOf(results);
        }
    }

    @Test
    void wheelsHaveNoAdvantage() {
        for (RollRecord roll : generate(3).rolls()) {
            assertThat(roll.advantage()).isZero();
            assertThat(roll.turn()).isZero();
            assertThat(roll.sectors()).allMatch(sector -> sector.tier() == OutcomeTier.PARTIAL);
            assertThat(roll.sectors()).allMatch(sector -> sector.quality() == ReligionWheel.QUALITY);
            assertThat(roll.sectors().stream().mapToInt(RolledSector::weightBp).sum())
                    .isEqualTo(Wheel.TOTAL_BP);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-100, 0, 100})
    void advantageDoesNotChangeReligionWheels(int advantage) {
        // Частини релігії — PARTIAL: навіть гранична перевага не зсуває ваги.
        Set<String> tags = Set.of("archetype_polytheism", "religion_war");
        assertSameWeights(ReligionWheel.archetypeSectors(RELIGIONS), advantage);
        assertSameWeights(ReligionWheel.countSectors("aspects_", TestReligions.BALANCE.aspects()), advantage);
        assertSameWeights(ReligionWheel.aspectSectors(RELIGIONS, tags, List.of()), advantage);
        assertSameWeights(ReligionWheel.dogmaSectors(RELIGIONS, tags, List.of()), advantage);
        assertSameWeights(ReligionWheel.politySectors(RELIGIONS, tags), advantage);
        assertSameWeights(ReligionWheel.faithFormSectors(RELIGIONS, POLYTHEISM), advantage);
    }

    @Test
    void archetypeSectorsFollowContentOrderAndWeights() {
        List<Sector<ArchetypeDef>> sectors = ReligionWheel.archetypeSectors(RELIGIONS);

        assertThat(sectors).extracting(Sector::id).containsExactly("monotheism", "polytheism");
        assertThat(sectors).extracting(Sector::weightBp).containsExactly(100, 100);
    }

    @Test
    void countSectorsAreEqualForEveryCountInRange() {
        List<Sector<Integer>> sectors = ReligionWheel.countSectors("dogmas_", new CountRange(2, 4));

        assertThat(sectors).extracting(Sector::id).containsExactly("dogmas_2", "dogmas_3", "dogmas_4");
        assertThat(sectors).extracting(Sector::value).containsExactly(2, 3, 4);
        assertThat(sectors).allMatch(sector -> sector.weightBp() == 1);
    }

    @Test
    void aspectWeightsFollowTagsAndChosenAspectsLeave() {
        assertThat(weights(ReligionWheel.aspectSectors(RELIGIONS, Set.of("archetype_monotheism"), List.of())))
                .isEqualTo(Map.of("war", 100, "knowledge", 100, "sea", 100));
        // Політеїзм +50 війні; обрана війна (мітка religion_war) −50 морю й сама вибуває.
        assertThat(weights(ReligionWheel.aspectSectors(RELIGIONS, Set.of("archetype_polytheism"), List.of())))
                .containsEntry("war", 150);
        assertThat(weights(ReligionWheel.aspectSectors(
                        RELIGIONS, Set.of("archetype_polytheism", "religion_war"), List.of(WAR))))
                .isEqualTo(Map.of("knowledge", 100, "sea", 50));
    }

    @Test
    void aspectWithZeroWeightIsLeftOut() {
        ReligionContent religions = religions(
                List.of(
                        TestReligions.aspect("war", Map.of()),
                        TestReligions.aspect("sea", Map.of("religion_war", -100))),
                List.of(TestReligions.dogma("pacifism", Map.of())),
                RELIGIONS.polities());

        assertThat(ReligionWheel.aspectSectors(religions, Set.of("religion_war"), List.of(WAR)))
                .isEmpty();
    }

    @Test
    void dogmaSectorsSkipChosenAndIncompatible() {
        Set<String> tags = Set.of("religion_war", "religion_knowledge");
        assertThat(weights(ReligionWheel.dogmaSectors(RELIGIONS, tags, List.of())))
                .isEqualTo(Map.of("holy_war", 200, "pacifism", 100, "scholar_honor", 200));
        // Несумісність симетрична: обрана священна війна прибирає ненасильство й навпаки.
        assertThat(ReligionWheel.dogmaSectors(RELIGIONS, tags, List.of(HOLY_WAR)))
                .extracting(Sector::id)
                .containsExactly("scholar_honor");
        assertThat(ReligionWheel.dogmaSectors(RELIGIONS, tags, List.of(PACIFISM)))
                .extracting(Sector::id)
                .containsExactly("scholar_honor");
        assertThat(ReligionWheel.dogmaSectors(RELIGIONS, tags, List.of(SCHOLAR_HONOR)))
                .extracting(Sector::id)
                .containsExactly("holy_war", "pacifism");
    }

    @Test
    void polityWeightsFollowAllReligionTags() {
        assertThat(weights(ReligionWheel.politySectors(RELIGIONS, Set.of("archetype_monotheism"))))
                .isEqualTo(Map.of("single_church", 200, "communities", 100));
        assertThat(weights(ReligionWheel.politySectors(RELIGIONS, Set.of("archetype_polytheism"))))
                .isEqualTo(Map.of("single_church", 100, "communities", 100));
    }

    @Test
    void faithFormsAreThoseOfArchetype() {
        assertThat(ReligionWheel.faithFormSectors(RELIGIONS, MONOTHEISM))
                .extracting(Sector::id)
                .containsExactly("path");
        List<Sector<FaithFormDef>> polytheism = ReligionWheel.faithFormSectors(RELIGIONS, POLYTHEISM);
        assertThat(polytheism).extracting(Sector::id).containsExactly("path", "temple");
        assertThat(polytheism).allMatch(sector -> sector.weightBp() == 1);
    }

    @Test
    void religionIsAssembledFromChosenParts() {
        for (long seed = 0; seed < 200; seed++) {
            StartReligion religion = generate(seed);
            ArchetypeDef archetype = RELIGIONS.archetype(religion.archetype()).orElseThrow();

            TreeSet<String> tags = new TreeSet<>(archetype.tags());
            religion.aspects()
                    .forEach(
                            id -> tags.addAll(RELIGIONS.aspect(id).orElseThrow().tags()));
            religion.dogmas()
                    .forEach(id -> tags.addAll(RELIGIONS.dogma(id).orElseThrow().tags()));
            tags.addAll(RELIGIONS.polity(religion.polity()).orElseThrow().tags());
            assertThat(religion.tags()).isEqualTo(tags);

            assertThat(archetype.figureSexes()).contains(religion.figureSex());
            assertThat(religion.figure().gender())
                    .isEqualTo(religion.figureSex().gender());
            assertThat(RELIGIONS.faithForm(religion.faithForm()).orElseThrow().appliesTo(archetype.id()))
                    .isTrue();
            // Тестовий шаблон ніколи не вичерпується: кількість частин — як на колесі кількості.
            assertThat(religion.aspects()).hasSize(rolled(religion, ReligionWheel.ASPECT_COUNT_KIND, "aspects_"));
            assertThat(religion.dogmas()).hasSize(rolled(religion, ReligionWheel.DOGMA_COUNT_KIND, "dogmas_"));
        }
    }

    @Test
    void figureOfMonotheismIsAlwaysMale() {
        TreeSet<Sex> monotheism = new TreeSet<>();
        TreeSet<Sex> polytheism = new TreeSet<>();
        for (long seed = 0; seed < 200; seed++) {
            StartReligion religion = generate(seed);
            (religion.archetype().equals(MONOTHEISM.id()) ? monotheism : polytheism).add(religion.figureSex());
        }
        assertThat(monotheism).containsExactly(Sex.MALE);
        assertThat(polytheism).containsExactly(Sex.MALE, Sex.FEMALE);
    }

    @Test
    void nameDeclinesMainWordWithFigureInFormCase() {
        FaithFormDef path = RELIGIONS.faithForm(new FaithFormId("path")).orElseThrow();
        NounPhrase figure = new NounPhrase(
                GrammaticalGender.MASCULINE,
                List.of("Орін", "Оріна", "Орінові", "Оріна", "Оріном", "Орінові", "Оріне"));

        NounPhrase name = ReligionWheel.name(path, figure);

        assertThat(name.gender()).isEqualTo(GrammaticalGender.MASCULINE);
        assertThat(name.forms())
                .containsExactly(
                        "Шлях Оріна",
                        "Шляху Оріна",
                        "Шляху Оріна",
                        "Шлях Оріна",
                        "Шляхом Оріна",
                        "Шляху Оріна",
                        "Шляху Оріна");
    }

    @Test
    void generatedNameUsesFigureName() {
        for (long seed = 0; seed < 50; seed++) {
            StartReligion religion = generate(seed);
            FaithFormDef form = RELIGIONS.faithForm(religion.faithForm()).orElseThrow();

            assertThat(religion.name()).isEqualTo(ReligionWheel.name(form, religion.figure()));
            assertThat(religion.name().nominative())
                    .endsWith(" " + religion.figure().form(GrammaticalCase.GENITIVE));
        }
    }

    @Test
    void takenNameIsRegeneratedWithoutShiftingOtherParts() {
        for (long seed = 0; seed < 50; seed++) {
            StartReligion free = generate(seed);
            StartReligion busy = ReligionWheel.generate(
                    Rng.of(seed), PACK, Set.of(free.name().nominative()));

            assertThat(busy.name().nominative()).isNotEqualTo(free.name().nominative());
            assertThat(busy.archetype()).isEqualTo(free.archetype());
            assertThat(busy.aspects()).isEqualTo(free.aspects());
            assertThat(busy.dogmas()).isEqualTo(free.dogmas());
            assertThat(busy.polity()).isEqualTo(free.polity());
            assertThat(busy.faithForm()).isEqualTo(free.faithForm());
            assertThat(busy.figureSex()).isEqualTo(free.figureSex());
            assertThat(busy.rolls()).isEqualTo(free.rolls());
        }
    }

    @Test
    void runningOutOfNamesIsAnInvariantViolation() {
        // Сотні seed-ів вичерпують усі назви тестового контенту: імен у стилях лише кілька на стать.
        TreeSet<String> everything = new TreeSet<>();
        for (long seed = 0; seed < 2000; seed++) {
            everything.add(generate(seed).name().nominative());
        }

        assertThatThrownBy(() -> ReligionWheel.generate(Rng.of(1), PACK, everything))
                .isInstanceOf(InvariantViolationException.class);
    }

    @Test
    void partsMayRunShort() {
        // Лише один аспект і один догмат, хоча баланс просить щонайменше 2 аспекти.
        ReligionContent religions = religions(
                List.of(TestReligions.aspect("war", Map.of())),
                List.of(TestReligions.dogma("holy_war", Map.of())),
                RELIGIONS.polities());
        ContentPack pack = TestNames.pack(religions, TestReligions.BALANCE);

        for (long seed = 0; seed < 50; seed++) {
            StartReligion religion = ReligionWheel.generate(Rng.of(seed), pack, Set.of());

            assertThat(religion.aspects()).containsExactly(WAR);
            assertThat(religion.dogmas()).containsExactly(HOLY_WAR);
        }
    }

    @Test
    void noAvailablePolityIsAnInvariantViolation() {
        ReligionContent religions = religions(
                RELIGIONS.aspects(),
                RELIGIONS.dogmas(),
                List.of(TestReligions.polity(
                        "single_church", Map.of("archetype_monotheism", -100, "archetype_polytheism", -100))));
        ContentPack pack = TestNames.pack(religions, TestReligions.BALANCE);

        assertThatThrownBy(() -> ReligionWheel.generate(Rng.of(1), pack, Set.of()))
                .isInstanceOf(InvariantViolationException.class);
    }

    @Test
    void polityWithZeroWeightIsNeverChosen() {
        ReligionContent religions = religions(
                RELIGIONS.aspects(),
                RELIGIONS.dogmas(),
                List.of(
                        TestReligions.polity("single_church", Map.of("archetype_polytheism", -100)),
                        TestReligions.polity("communities", Map.of())));
        ContentPack pack = TestNames.pack(religions, TestReligions.BALANCE);

        for (long seed = 0; seed < 100; seed++) {
            StartReligion religion = ReligionWheel.generate(Rng.of(seed), pack, Set.of());
            if (religion.archetype().equals(POLYTHEISM.id())) {
                assertThat(religion.polity()).isEqualTo(new ReligionPolityId("communities"));
            }
        }
    }

    static StartReligion generate(long seed) {
        return ReligionWheel.generate(Rng.of(seed), PACK, Set.of());
    }

    private static int rolled(StartReligion religion, WheelKind kind, String prefix) {
        String id = religion.rolls().stream()
                .filter(roll -> roll.kind().equals(kind))
                .findFirst()
                .orElseThrow()
                .resultSectorId();
        return Integer.parseInt(id.substring(prefix.length()));
    }

    private static ReligionContent religions(
            List<AspectDef> aspects, List<DogmaDef> dogmas, List<ReligionPolityDef> polities) {
        return new ReligionContent(RELIGIONS.archetypes(), aspects, dogmas, polities, RELIGIONS.faithForms());
    }

    private static <T> Map<String, Integer> weights(List<Sector<T>> sectors) {
        return sectors.stream().collect(Collectors.toMap(Sector::id, Sector::weightBp));
    }

    private static <T> void assertSameWeights(List<Sector<T>> sectors, int advantage) {
        assertThat(Wheel.applyAdvantage(sectors, advantage, Wheel.MAX_STRENGTH))
                .extracting(Sector::weightBp)
                .isEqualTo(Wheel.applyAdvantage(sectors, 0, Wheel.MAX_STRENGTH).stream()
                        .map(Sector::weightBp)
                        .toList());
    }
}
