package kolo.engine.content;

import java.util.List;
import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.FateTokens;

/**
 * Нагорода колеса стріку (GD §4.10) і її сектор у колесі.
 *
 * <p>Нагорода мусить щось давати: модифікатори, жетони долі, додаткових постатей або мітки.
 *
 * @param name назва українською
 * @param description опис для гравця
 * @param weight вага в колесі стріку, {@code 1..}{@value #MAX_WEIGHT}; відносна — колесо нормалізує ваги всіх
 *     нагород
 * @param durationYears скільки років діють модифікатори від 01.01.1970, {@code 0..}{@value #MAX_DURATION_YEARS};
 *     {@code 0} — постійно
 * @param modifiers ефекти нагороди
 * @param fateTokens скільки жетонів долі дає нагорода, {@code 0..}{@link FateTokens#MAX}; понад ліміт жетони згорають
 * @param extraPeople скільки додаткових відомих людей дає нагорода, {@code 0..}{@value #MAX_EXTRA_PEOPLE}
 * @param tags мітки, які нагорода дає державі
 */
public record StreakRewardDef(
        StreakRewardId id,
        String name,
        String description,
        int weight,
        int durationYears,
        List<ModifierDef> modifiers,
        int fateTokens,
        int extraPeople,
        List<String> tags) {

    public static final int MAX_WEIGHT = 10_000;
    public static final int MAX_DURATION_YEARS = 100;
    public static final int MAX_EXTRA_PEOPLE = 3;

    public StreakRewardDef {
        Objects.requireNonNull(id, "id");
        String field = "streak_reward." + id;
        Checks.notBlank(field + ".name", name);
        Checks.notBlank(field + ".description", description);
        Checks.inRange(field + ".weight", weight, 1, MAX_WEIGHT);
        Checks.inRange(field + ".duration", durationYears, 0, MAX_DURATION_YEARS);
        modifiers = List.copyOf(modifiers);
        Checks.inRange(field + ".fate_tokens", fateTokens, 0, FateTokens.MAX);
        Checks.inRange(field + ".extra_people", extraPeople, 0, MAX_EXTRA_PEOPLE);
        tags = Defs.tags(field + ".tags", tags);
        // Порожній сектор виглядав би для гравця як нагорода, яка нічого не дала.
        if (modifiers.isEmpty() && fateTokens == 0 && extraPeople == 0 && tags.isEmpty()) {
            throw new ValidationException(ErrorCode.EMPTY_COLLECTION, ErrorDetails.of("field", field + ".effects"));
        }
    }
}
