package kolo.engine.generation.religion;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.content.ArchetypeId;
import kolo.engine.content.AspectId;
import kolo.engine.content.DogmaId;
import kolo.engine.content.FaithFormId;
import kolo.engine.content.ReligionBalanceDef;
import kolo.engine.content.ReligionPolityId;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.NounPhrase;
import kolo.engine.state.Sex;
import kolo.engine.wheel.RollRecord;

/**
 * Релігія світу на старті (GD §25.1) — результат {@link ReligionWheel}. Id, святий центр і держави цієї віри
 * з'являться разом зі світом і картою.
 *
 * @param aspects аспекти божества в порядку вибору, без повторів, {@code 0..}{@value ReligionBalanceDef#MAX_PARTS}
 *     (менше за баланс, якщо доступних забракло)
 * @param dogmas догмати в порядку вибору, без повторів, {@code 0..}{@value ReligionBalanceDef#MAX_PARTS}
 * @param figureSex стать постаті, чиїм ім'ям зветься віра
 * @param figure ім'я постаті в усіх відмінках, напр. «Орін»; рід відповідає статі
 * @param name назва віри в усіх відмінках, напр. «Шлях Оріна»; рід — за формою назви
 * @param tags мітки релігії: об'єднання міток архетипу, аспектів, догматів і устрою
 * @param rolls обертання в порядку кидків: архетип, кількість аспектів, аспекти, кількість догматів, догмати, устрій,
 *     форма назви
 */
public record StartReligion(
        ArchetypeId archetype,
        List<AspectId> aspects,
        List<DogmaId> dogmas,
        ReligionPolityId polity,
        FaithFormId faithForm,
        Sex figureSex,
        NounPhrase figure,
        NounPhrase name,
        SortedSet<String> tags,
        List<RollRecord> rolls) {

    public StartReligion {
        Objects.requireNonNull(archetype, "archetype");
        aspects = unique("religion.aspects", aspects);
        dogmas = unique("religion.dogmas", dogmas);
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(faithForm, "faithForm");
        Objects.requireNonNull(figureSex, "figureSex");
        Objects.requireNonNull(figure, "figure");
        Objects.requireNonNull(name, "name");
        if (figure.gender() != figureSex.gender()) {
            throw new ValidationException(
                    ErrorCode.NAME_GENDER_MISMATCH,
                    ErrorDetails.of(
                            "field",
                            "religion.figure",
                            "value",
                            figure.nominative(),
                            "expected",
                            figureSex.gender().key()));
        }
        tags = Collections.unmodifiableSortedSet(new TreeSet<>(tags));
        rolls = List.copyOf(rolls);
    }

    private static <T extends Comparable<T>> List<T> unique(String field, List<T> ids) {
        List<T> copy = List.copyOf(ids);
        Checks.inRange(field, copy.size(), 0, ReligionBalanceDef.MAX_PARTS);
        TreeSet<T> seen = new TreeSet<>();
        for (T id : copy) {
            if (!seen.add(id)) {
                throw new ValidationException(ErrorCode.DUPLICATE_ID, ErrorDetails.of("field", field, "value", id));
            }
        }
        return copy;
    }
}
