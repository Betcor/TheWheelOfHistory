package kolo.engine.wheel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import kolo.engine.error.ValidationException;
import org.junit.jupiter.api.Test;

/** Значення-об'єкти колеса: перевірки в конструкторах і незмінність. */
class WheelValuesTest {

    @Test
    void advantageSumsAndClampsModifiers() {
        List<AppliedModifier> modifiers = List.of(
                new AppliedModifier("a", "modifier.a", 80),
                new AppliedModifier("b", "modifier.b", 70),
                new AppliedModifier("c", "modifier.c", -20));

        Advantage advantage = Advantage.of(modifiers);

        assertThat(advantage.value()).isEqualTo(100);
        assertThat(advantage.modifiers()).isEqualTo(modifiers);
        assertThat(Advantage.of(List.of(new AppliedModifier("d", "modifier.d", -150)))
                        .value())
                .isEqualTo(-100);
        assertThat(Advantage.of(List.of())).isEqualTo(Advantage.NONE);
    }

    @Test
    void advantageRejectsOutOfRangeValue() {
        assertThatThrownBy(() -> new Advantage(101, List.of())).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new Advantage(-101, List.of())).isInstanceOf(ValidationException.class);
    }

    @Test
    void sectorValidatesFields() {
        assertThatThrownBy(() -> new Sector<>("Bad-Id", 1, "x", 0, OutcomeTier.FAIL, List.of()))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new Sector<>("ok", -1, "x", 0, OutcomeTier.FAIL, List.of()))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new Sector<>("ok", 10_001, "x", 0, OutcomeTier.FAIL, List.of()))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new Sector<>("ok", 1, "x", 101, OutcomeTier.FAIL, List.of()))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new Sector<>("ok", 1, null, 0, OutcomeTier.FAIL, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Sector<>("ok", 1, "x", 0, null, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void sectorCopiesTags() {
        List<String> tags = new ArrayList<>(List.of("revanchism"));
        Sector<String> sector = new Sector<>("ok", 1, "x", 0, OutcomeTier.FAIL, tags);
        tags.add("lost_war");

        assertThat(sector.tags()).containsExactly("revanchism");
    }

    @Test
    void wheelKindIsSnakeCase() {
        assertThat(new WheelKind("economic_cycle").id()).isEqualTo("economic_cycle");
        assertThatThrownBy(() -> new WheelKind("EconomicCycle")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new WheelKind("1st")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new WheelKind("")).isInstanceOf(ValidationException.class);
    }

    @Test
    void tierClassification() {
        assertThat(OutcomeTier.CRIT_SUCCESS.isSuccess()).isTrue();
        assertThat(OutcomeTier.SUCCESS.isSuccess()).isTrue();
        assertThat(OutcomeTier.FAIL.isFailure()).isTrue();
        assertThat(OutcomeTier.CRIT_FAIL.isFailure()).isTrue();
        assertThat(OutcomeTier.PARTIAL.isSuccess()).isFalse();
        assertThat(OutcomeTier.PARTIAL.isFailure()).isFalse();
        assertThat(OutcomeTier.PARTIAL.isCritical()).isFalse();
        assertThat(OutcomeTier.CRIT_FAIL.isCritical()).isTrue();
        assertThat(OutcomeTier.CRIT_SUCCESS.isCritical()).isTrue();
    }

    @Test
    void tierStepCountsFromPartial() {
        assertThat(Arrays.stream(OutcomeTier.values()).mapToInt(OutcomeTier::step))
                .containsExactly(-2, -1, 0, 1, 2);
    }

    @Test
    void tierKeyIsContentKey() {
        assertThat(OutcomeTier.CRIT_FAIL.key()).isEqualTo("crit_fail");
        assertThat(OutcomeTier.PARTIAL.key()).isEqualTo("partial");
        assertThat(OutcomeTier.CRIT_SUCCESS.key()).isEqualTo("crit_success");
    }

    @Test
    void rollRecordValidatesResult() {
        List<RolledSector> sectors = List.of(
                new RolledSector("fail", 4000, OutcomeTier.FAIL, 10),
                new RolledSector("success", 6000, OutcomeTier.SUCCESS, 80));
        WheelKind kind = new WheelKind("construction");

        RollRecord record = new RollRecord(kind, sectors, 0, List.of(), "success", 5000, 0, null);
        assertThat(record.result().quality()).isEqualTo(80);

        assertThatThrownBy(() -> new RollRecord(kind, sectors, 0, List.of(), "missing", 5000, 0, null))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new RollRecord(kind, sectors, 0, List.of(), "fail", 10_000, 0, null))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new RollRecord(kind, sectors, 0, List.of(), "fail", 0, -1, null))
                .isInstanceOf(ValidationException.class);
    }
}
