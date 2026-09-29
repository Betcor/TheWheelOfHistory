package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.content.NameFinalDef;
import kolo.engine.content.NameStyleDef;
import kolo.engine.content.StateFormDef;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.InvariantViolationException;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.name.CountryNames;
import kolo.engine.generation.name.TestNames;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NameWheelTest {

    static final SubIdeologyId LIBERAL = new SubIdeologyId("liberal_democracy");
    static final SubIdeologyId DIRECT = new SubIdeologyId("direct_democracy");
    static final SubIdeologyId ABSOLUTE = new SubIdeologyId("absolute_monarchy");

    private static final ContentPack PACK = TestNames.PACK;

    @Test
    void sameSeedGivesSameName() {
        assertThat(generate(42, DIRECT, Set.of())).isEqualTo(generate(42, DIRECT, Set.of()));
    }

    @Test
    void wheelHasEqualSectorPerCandidate() {
        for (long seed = 0; seed < 50; seed++) {
            StartName name = generate(seed, DIRECT, Set.of());
            RollRecord roll = name.roll();

            assertThat(name.candidates()).hasSize(PACK.balance().generation().nameCandidates());
            assertThat(roll.kind()).isEqualTo(NameWheel.KIND);
            assertThat(roll.sectors())
                    .extracting(RolledSector::id)
                    .containsExactly("name_1", "name_2", "name_3", "name_4", "name_5");
            assertThat(roll.sectors()).allMatch(sector -> sector.weightBp() == Wheel.TOTAL_BP / 5);
            assertThat(roll.sectors()).allMatch(sector -> sector.tier() == OutcomeTier.PARTIAL);
            assertThat(roll.sectors()).allMatch(sector -> sector.quality() == NameWheel.QUALITY);
            assertThat(roll.advantage()).isZero();
            assertThat(roll.resultSectorId()).isEqualTo("name_" + (name.chosen() + 1));
            assertThat(name.name())
                    .isEqualTo(name.candidates().get(name.chosen()).name());
            assertThat(name.style())
                    .isEqualTo(name.candidates().get(name.chosen()).style());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-100, 0, 100})
    void advantageDoesNotChangeNameWheel(int advantage) {
        // Сектори назви — PARTIAL: навіть гранична перевага не зсуває ваги.
        List<Sector<Integer>> sectors = NameWheel.sectors(5);

        assertThat(weights(Wheel.applyAdvantage(sectors, advantage, Wheel.MAX_STRENGTH)))
                .isEqualTo(weights(Wheel.applyAdvantage(sectors, 0, Wheel.MAX_STRENGTH)));
    }

    @Test
    void sectorsAreNumberedFromOne() {
        List<Sector<Integer>> sectors = NameWheel.sectors(3);

        assertThat(sectors).extracting(Sector::id).containsExactly("name_1", "name_2", "name_3");
        assertThat(sectors).extracting(Sector::value).containsExactly(0, 1, 2);
        assertThat(sectors).allMatch(sector -> sector.weightBp() == 1);
    }

    @Test
    void candidatesUseFormsOfSubIdeologyAndMatchStyle() {
        for (long seed = 0; seed < 100; seed++) {
            for (NameCandidate candidate : generate(seed, ABSOLUTE, Set.of()).candidates()) {
                assertThat(candidate.name().fullName().nominative()).startsWith("Королівство ");
                String root = candidate.name().shortName().nominative();
                if (candidate.style().equals(TestNames.SOUTHERN.id())) {
                    assertThat(root).isIn("Салан", "Маран");
                } else {
                    assertThat(root).doesNotEndWith("ан");
                }
            }
            for (NameCandidate candidate : generate(seed, LIBERAL, Set.of()).candidates()) {
                assertThat(candidate.name().fullName().nominative()).startsWith("Республіка ");
            }
        }
    }

    @Test
    void candidatesAreUniqueAndAvoidTakenNames() {
        Set<String> taken = Set.of("Королівство Салан", "Королівство Маран", "Королівство Велор");

        for (long seed = 0; seed < 100; seed++) {
            List<String> names = generate(seed, ABSOLUTE, taken).candidates().stream()
                    .map(candidate -> candidate.name().fullName().nominative())
                    .toList();

            assertThat(new TreeSet<>(names)).hasSameSizeAs(names);
            assertThat(names).doesNotContainAnyElementsOf(taken);
            // Обидва південні королівства зайняті: вільні королівства лишилися лише в північному стилі.
            assertThat(generate(seed, ABSOLUTE, taken).candidates())
                    .allMatch(candidate -> candidate.style().equals(TestNames.NORTHERN.id()));
        }
    }

    @Test
    void sameRootWithAnotherFormIsAllowed() {
        // Зайнята республіка не забороняє королівство з тим самим коренем.
        Set<String> taken = Set.of("Республіка Салан", "Республіка Маран");
        boolean found = false;
        for (long seed = 0; seed < 200 && !found; seed++) {
            found = generate(seed, ABSOLUTE, taken).candidates().stream()
                    .anyMatch(candidate ->
                            candidate.name().shortName().nominative().equals("Салан"));
        }
        assertThat(found).isTrue();
    }

    @Test
    void exhaustedNamesAreAnInvariantViolation() {
        Set<String> all = allNames(TestNames.KINGDOM);
        String free = "Королівство Салан";
        TreeSet<String> almostAll = new TreeSet<>(all);
        assertThat(almostAll.remove(free)).isTrue();

        StartName last = NameWheel.generate(Rng.of(1), TestNames.pack(1), ABSOLUTE, almostAll);
        assertThat(last.name().fullName().nominative()).isEqualTo(free);
        assertThatThrownBy(() -> NameWheel.generate(Rng.of(1), PACK, ABSOLUTE, all))
                .isInstanceOf(InvariantViolationException.class);
    }

    @Test
    void unknownSubIdeologyIsRejected() {
        assertThatThrownBy(() -> generate(1, new SubIdeologyId("anarchy"), Set.of()))
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE));
    }

    @Test
    void singleCandidateIsChosenForSure() {
        StartName name = NameWheel.generate(Rng.of(3), TestNames.pack(1), DIRECT, Set.of());

        assertThat(name.chosen()).isZero();
        assertThat(name.roll().sectors())
                .singleElement()
                .extracting(RolledSector::weightBp)
                .isEqualTo(Wheel.TOTAL_BP);
    }

    @Test
    void startNameRejectsInconsistentFields() {
        StartName name = generate(5, DIRECT, Set.of());
        int other = (name.chosen() + 1) % name.candidates().size();

        assertThatThrownBy(() -> new StartName(name.candidates(), other, name.roll()))
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE));
        assertThatThrownBy(
                        () -> new StartName(name.candidates(), name.candidates().size(), name.roll()))
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.VALUE_OUT_OF_RANGE));
        List<NameCandidate> duplicated = new ArrayList<>(name.candidates());
        duplicated.set(other, name.candidates().get(name.chosen()));
        assertThatThrownBy(() -> new StartName(duplicated, name.chosen(), name.roll()))
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.DUPLICATE_ID));
    }

    static StartName generate(long seed, SubIdeologyId sub, Set<String> taken) {
        return NameWheel.generate(Rng.of(seed), PACK, sub, taken);
    }

    /** Усі повні назви з цією формою, які дає тестовий контент. */
    static Set<String> allNames(StateFormDef form) {
        TreeSet<String> names = new TreeSet<>();
        for (NameStyleDef style : PACK.names().styles().values()) {
            List<String> middles = new ArrayList<>();
            middles.add("");
            if (style.middleChanceBp() > 0) {
                middles.addAll(style.middles());
            }
            for (String start : style.starts()) {
                for (String middle : middles) {
                    for (NameFinalDef nameFinal : style.finals()) {
                        names.add(CountryNames.name(
                                        form,
                                        CountryNames.root(
                                                start + middle,
                                                nameFinal,
                                                PACK.names().paradigm(nameFinal)))
                                .fullName()
                                .nominative());
                    }
                }
            }
        }
        return names;
    }

    private static List<Integer> weights(List<Sector<Integer>> sectors) {
        return sectors.stream().map(Sector::weightBp).toList();
    }
}
