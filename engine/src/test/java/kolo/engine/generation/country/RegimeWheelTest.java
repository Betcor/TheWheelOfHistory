package kolo.engine.generation.country;

import static kolo.engine.generation.country.TestRegime.CONSTITUTIONAL;
import static kolo.engine.generation.country.TestRegime.DEMOCRACY;
import static kolo.engine.generation.country.TestRegime.LIBERAL;
import static kolo.engine.generation.country.TestRegime.MONARCHY;
import static kolo.engine.generation.country.TestRegime.PACK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.TreeSet;
import kolo.engine.content.ContentPack;
import kolo.engine.content.IdeologyDef;
import kolo.engine.content.SubIdeologyDef;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import kolo.engine.rng.Rng;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.RollRecord;
import kolo.engine.wheel.RolledSector;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RegimeWheelTest {

    private static final int SEEDS = 500;

    @Test
    void ideologySectorsFollowContentOrderAndWeights() {
        List<Sector<IdeologyDef>> sectors = RegimeWheel.ideologySectors(PACK);

        assertThat(sectors).extracting(Sector::id).containsExactly("democracy", "monarchy");
        assertThat(sectors).extracting(Sector::weightBp).containsExactly(100, 300);
        assertThat(sectors).extracting(Sector::value).containsExactly(DEMOCRACY, MONARCHY);
        assertThat(sectors).allSatisfy(sector -> {
            assertThat(sector.tier()).isEqualTo(OutcomeTier.PARTIAL);
            assertThat(sector.quality()).isEqualTo(RegimeWheel.QUALITY);
            assertThat(sector.tags()).isEqualTo(sector.value().tags());
        });
    }

    @Test
    void subIdeologySectorsAreOnlyThoseOfTheIdeology() {
        List<Sector<SubIdeologyDef>> sectors = RegimeWheel.subIdeologySectors(DEMOCRACY);

        assertThat(sectors).extracting(Sector::value).containsExactly(LIBERAL, CONSTITUTIONAL);
        assertThat(sectors).extracting(Sector::weightBp).containsExactly(100, 300);
        assertThat(sectors).extracting(Sector::tags).containsExactly(List.of("liberal"), List.of("monarch"));
    }

    @Test
    void subIdeologyBelongsToTheRolledIdeology() {
        for (long seed = 0; seed < SEEDS; seed++) {
            Regime regime = RegimeWheel.generate(Rng.of(seed), PACK);

            assertThat(regime.ideology().subIdeologies()).contains(regime.subIdeology());
        }
    }

    @Test
    void rollsRecordBothWheelsWithoutAdvantage() {
        Regime regime = RegimeWheel.generate(Rng.of(7), PACK);

        assertThat(regime.rolls())
                .extracting(RollRecord::kind)
                .containsExactly(RegimeWheel.IDEOLOGY_KIND, RegimeWheel.SUB_IDEOLOGY_KIND);
        assertThat(regime.rolls())
                .extracting(RollRecord::resultSectorId)
                .containsExactly(
                        regime.ideology().id().value(),
                        regime.subIdeology().id().value());
        assertThat(regime.rolls()).allSatisfy(roll -> {
            assertThat(roll.advantage()).isZero();
            assertThat(roll.modifiers()).isEmpty();
            assertThat(roll.turn()).isZero();
            assertThat(roll.season()).isNull();
            assertThat(roll.sectors().stream().mapToInt(RolledSector::weightBp).sum())
                    .isEqualTo(10_000);
        });
    }

    @Test
    void tagsJoinIdeologyAndSubIdeologyWithoutRepeats() {
        for (long seed = 0; seed < SEEDS; seed++) {
            Regime regime = RegimeWheel.generate(Rng.of(seed), PACK);
            if (regime.subIdeology().equals(CONSTITUTIONAL)) {
                assertThat(regime.tags()).containsExactly("democratic", "monarch");
            } else if (regime.ideology().equals(MONARCHY)) {
                assertThat(regime.tags()).containsExactly("absolutism", "monarch", "monarchic");
            } else {
                assertThat(regime.tags()).containsExactly("democratic", "liberal");
            }
        }
    }

    @Test
    void singleSectorWheelsAlwaysGiveTheOnlyChoice() {
        IdeologyDef only = TestRegime.ideology("theocracy", 1, List.of(), TestRegime.sub("temple_state", 1));
        ContentPack pack = TestBackstory.pack(List.of(only), TestBackstory.FRAGMENTS, TestBackstory.COUNT);

        Regime regime = RegimeWheel.generate(Rng.of(1), pack);

        assertThat(regime.ideology()).isEqualTo(only);
        assertThat(regime.subIdeology().id().value()).isEqualTo("temple_state");
        assertThat(regime.tags()).isEmpty();
    }

    @Test
    void subIdeologyStreamIsIndependentOfIdeologyStream() {
        // Колесо підкласифікації крутиться у своєму потоці: з тією самою ідеологією однаковий seed дає ту саму
        // підкласифікацію, незалежно від того, скільки кидків зробило колесо ідеології.
        Rng rng = Rng.of(42);
        int direct = rng.fork("sub_ideology").nextInt(10_000);
        Regime regime = RegimeWheel.generate(Rng.of(42), PACK);

        assertThat(regime.rolls().get(1).roll()).isEqualTo(direct);
    }

    @ParameterizedTest
    @ValueSource(ints = {Advantage.MIN, 0, Advantage.MAX})
    void advantageDoesNotChangeRegimeWheels(int advantage) {
        // Сектори ладу — PARTIAL: навіть гранична перевага лишає ваги контенту.
        assertThat(weights(Wheel.applyAdvantage(RegimeWheel.ideologySectors(PACK), advantage, Wheel.MAX_STRENGTH)))
                .containsExactly(2500, 7500);
        assertThat(weights(
                        Wheel.applyAdvantage(RegimeWheel.subIdeologySectors(DEMOCRACY), advantage, Wheel.MAX_STRENGTH)))
                .containsExactly(2500, 7500);
    }

    private static <T> List<Integer> weights(List<Sector<T>> sectors) {
        return sectors.stream().map(Sector::weightBp).toList();
    }

    @Test
    void regimeRejectsSubIdeologyOfAnotherIdeology() {
        assertThatThrownBy(() -> new Regime(MONARCHY, LIBERAL, new TreeSet<>(), List.of()))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UNKNOWN_REFERENCE));
    }
}
