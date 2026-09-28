package kolo.engine.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import kolo.engine.modifier.Modifier;
import kolo.engine.modifier.ModifierSource;
import kolo.engine.modifier.ModifierTarget;
import kolo.engine.modifier.SourceKind;
import kolo.engine.rng.Rng;
import kolo.engine.state.CountryStats;
import kolo.engine.state.Stat;
import kolo.engine.wheel.Advantage;
import kolo.engine.wheel.OutcomeTier;
import kolo.engine.wheel.Sector;
import kolo.engine.wheel.Wheel;
import kolo.engine.wheel.WheelKind;
import org.junit.jupiter.api.Test;

/**
 * Смок-тест помилок рушія: типові некоректні входи до колеса, модифікаторів і статів ловляться на «межі» одним
 * обробником {@link GameException} і перетворюються на код і подробиці — як це робитиме сервер для клієнта.
 */
class ErrorSmokeTest {

    record Reported(String key, String detailsText) {}

    @Test
    void invalidInputsReachBoundaryAsGameExceptions() {
        List<Supplier<Object>> calls = List.of(
                () -> Wheel.applyAdvantage(List.of(), 0, 100),
                () -> Wheel.spin(
                        Rng.of(1),
                        new WheelKind("construction"),
                        List.of(new Sector<>("done", 0, "x", 50, OutcomeTier.SUCCESS, List.of())),
                        Advantage.NONE,
                        100,
                        0,
                        null),
                () -> new Sector<>("Bad", 1, "x", 0, OutcomeTier.FAIL, List.of()),
                () -> new Modifier(
                        "m", new ModifierSource(SourceKind.EVENT, "evt_1"), ModifierTarget.stat(Stat.HDI), 1, -3, "k"),
                () -> new CountryStats(0, 50, 50, 50, 50, 200, 0),
                () -> Rng.of(1).nextInt(0));

        List<Reported> reported = new ArrayList<>();
        for (Supplier<Object> call : calls) {
            reported.add(boundary(call));
        }

        assertThat(reported)
                .extracting(Reported::key)
                .containsExactly(
                        "error.empty_collection",
                        "error.wheel_zero_weight",
                        "error.invalid_key_format",
                        "error.value_out_of_range",
                        "error.value_out_of_range",
                        "error.value_out_of_range");
        assertThat(reported.get(4).detailsText()).isEqualTo("{field=stats.WAR_WEARINESS, max=100, min=0, value=200}");
    }

    /** Так обробляє помилки межа (обробник повідомлень сесії): лише код і подробиці, без getMessage(). */
    private static Reported boundary(Supplier<Object> call) {
        try {
            call.get();
            throw new AssertionError("очікувався виняток");
        } catch (GameException e) {
            return new Reported(e.code().key(), e.details().toString());
        }
    }
}
