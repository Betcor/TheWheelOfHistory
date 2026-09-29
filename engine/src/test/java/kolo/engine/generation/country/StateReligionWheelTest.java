package kolo.engine.generation.country;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.content.DogmaId;
import kolo.engine.content.StateReligionDef;
import kolo.engine.content.TagCondition;
import kolo.engine.content.TestReligions;
import kolo.engine.error.InvariantViolationException;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.name.TestNames;
import kolo.engine.generation.religion.StartReligion;
import kolo.engine.generation.religion.WorldReligionsWheel;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.SourceKind;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StateReligionWheelTest {

    private static final ContentPack PACK = TestNames.PACK;
    private static final List<StartReligion> RELIGIONS =
            WorldReligionsWheel.generate(Rng.of(1970), PACK, 12).religions();

    /** Світська держава: 50, соціалізм +150, не теократія. */
    private static final StateReligionDef DEF = new StateReligionDef(
            100,
            TestReligions.secular(
                    50, Map.of("socialist", 150), new TagCondition(List.of(), List.of(), List.of("theocratic"))));

    @Test
    void sameSeedGivesSameReligion() {
        assertThat(generate(42)).isEqualTo(generate(42));
    }

    @Test
    void sectorsAreWorldReligionsThenSecularState() {
        List<Sector<OptionalInt>> sectors = StateReligionWheel.sectors(DEF, Set.of(), RELIGIONS);

        assertThat(sectors).hasSize(RELIGIONS.size() + 1);
        for (int i = 0; i < RELIGIONS.size(); i++) {
            assertThat(sectors.get(i).id()).isEqualTo("religion_" + i);
            assertThat(sectors.get(i).weightBp()).isEqualTo(100);
            assertThat(sectors.get(i).value()).isEqualTo(OptionalInt.of(i));
        }
        Sector<OptionalInt> secular = sectors.getLast();
        assertThat(secular.id()).isEqualTo(StateReligionWheel.SECULAR);
        assertThat(secular.weightBp()).isEqualTo(50);
        assertThat(secular.value()).isEmpty();
        assertThat(sectors).allSatisfy(sector -> {
            assertThat(sector.tier()).isEqualTo(OutcomeTier.PARTIAL);
            assertThat(sector.quality()).isEqualTo(StateReligionWheel.QUALITY);
        });
    }

    @Test
    void secularWeightDependsOnRegimeTags() {
        assertThat(StateReligionWheel.sectors(DEF, Set.of("socialist"), RELIGIONS)
                        .getLast()
                        .weightBp())
                .isEqualTo(200);
    }

    @Test
    void theocracyCannotBeSecular() {
        List<Sector<OptionalInt>> sectors =
                StateReligionWheel.sectors(DEF, Set.of("theocratic", "socialist"), RELIGIONS);

        assertThat(sectors).hasSize(RELIGIONS.size());
        assertThat(sectors).noneMatch(sector -> sector.id().equals(StateReligionWheel.SECULAR));
    }

    @Test
    void withoutWorldReligionsOnlySecularStateRemains() {
        assertThat(StateReligionWheel.sectors(DEF, Set.of(), List.of()))
                .extracting(Sector::id)
                .containsExactly(StateReligionWheel.SECULAR);
    }

    @Test
    void failsWhenNoSectorIsLeft() {
        // Мітка тестової ідеології monarchy забороняє світську державу, а релігій світу немає.
        ContentPack pack = TestNames.pack(
                TestReligions.content(new StateReligionDef(
                        100,
                        TestReligions.secular(
                                100, Map.of(), new TagCondition(List.of(), List.of(), List.of("monarchy"))))),
                TestReligions.BALANCE);

        assertThat(StateReligionWheel.generate(Rng.of(1), pack, Set.of("democracy"), List.of())
                        .secular())
                .isTrue();
        assertThatThrownBy(() -> StateReligionWheel.generate(Rng.of(1), pack, Set.of("monarchy"), List.of()))
                .isInstanceOf(InvariantViolationException.class);
    }

    @Test
    void religiousStateGetsTagsAndModifiersOfItsFaith() {
        for (long seed = 0; seed < 200; seed++) {
            StartStateReligion result = generate(seed);
            if (result.secular()) {
                assertThat(result.tags()).containsExactly("secular");
                assertThat(result.modifiers()).isEmpty();
                continue;
            }
            StartReligion religion = RELIGIONS.get(result.religion().getAsInt());
            assertThat(result.tags()).isEqualTo(religion.tags());
            // Тестові догмати дають стабільність +5, устрої — нічого.
            List<Modifier> modifiers = result.modifiers();
            assertThat(modifiers).hasSize(religion.dogmas().size());
            for (int i = 0; i < modifiers.size(); i++) {
                DogmaId dogma = religion.dogmas().get(i);
                Modifier modifier = modifiers.get(i);
                assertThat(modifier.id()).isEqualTo("dogma:" + dogma + ":0");
                assertThat(modifier.source().kind()).isEqualTo(SourceKind.RELIGION);
                assertThat(modifier.source().refId()).isEqualTo(dogma.value());
                assertThat(modifier.descriptionKey()).isEqualTo("dogma." + dogma);
                assertThat(modifier.value()).isEqualTo(5);
                assertThat(modifier.expiresAtTurn()).isNull();
            }
        }
    }

    @Test
    void polityModifiersFollowDogmas() {
        StartReligion religion = RELIGIONS.getFirst();
        List<Modifier> modifiers = StateReligionWheel.modifiers(PACK.religions(), religion);

        assertThat(modifiers).hasSize(religion.dogmas().size());
        assertThat(modifiers).extracting(Modifier::id).doesNotHaveDuplicates();
    }

    @Test
    void rollRecordsTheSectorAndNoAdvantage() {
        TreeSet<String> results = new TreeSet<>();
        for (long seed = 0; seed < 200; seed++) {
            StartStateReligion result = generate(seed);
            RollRecord roll = result.rolls().getFirst();
            assertThat(result.rolls()).hasSize(1);
            assertThat(roll.kind()).isEqualTo(StateReligionWheel.KIND);
            assertThat(roll.advantage()).isZero();
            assertThat(roll.modifiers()).isEmpty();
            assertThat(roll.resultSectorId())
                    .isEqualTo(
                            result.religion().isEmpty()
                                    ? "secular"
                                    : "religion_" + result.religion().getAsInt());
            results.add(roll.resultSectorId());
        }
        // Випадає кожна релігія й світська держава.
        assertThat(results).hasSize(RELIGIONS.size() + 1);
    }

    @ParameterizedTest
    @ValueSource(ints = {-100, 0, 100})
    void advantageDoesNotChangeWeights(int advantage) {
        List<Sector<OptionalInt>> sectors = StateReligionWheel.sectors(DEF, Set.of(), RELIGIONS);
        List<Sector<OptionalInt>> base = Wheel.applyAdvantage(sectors, 0, Wheel.MAX_STRENGTH);

        assertThat(Wheel.applyAdvantage(sectors, advantage, Wheel.MAX_STRENGTH)).isEqualTo(base);
    }

    @Test
    void resultValidatesIndex() {
        assertThatThrownBy(() -> new StartStateReligion(OptionalInt.of(-1), new TreeSet<>(), List.of(), List.of()))
                .isInstanceOf(ValidationException.class);
        assertThat(new StartStateReligion(OptionalInt.empty(), new TreeSet<>(Set.of("secular")), List.of(), List.of())
                        .secular())
                .isTrue();
    }

    private static StartStateReligion generate(long seed) {
        return StateReligionWheel.generate(Rng.of(seed), PACK, Set.of(), RELIGIONS);
    }
}
