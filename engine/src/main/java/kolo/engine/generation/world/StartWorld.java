package kolo.engine.generation.world;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.SortedSet;
import java.util.TreeSet;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.generation.country.StartCountry;
import kolo.engine.generation.map.WorldMap;
import kolo.engine.generation.religion.StartHolyCenters;
import kolo.engine.generation.religion.StartReligions;

/**
 * Світ на старті — результат {@link WorldGenerator}.
 *
 * @param map карта з розміщеними державами
 * @param religions релігії світу
 * @param countries держави за номером на карті
 * @param holyCenters святий центр кожної релігії за її номером
 */
public record StartWorld(
        WorldMap map, StartReligions religions, List<StartCountry> countries, StartHolyCenters holyCenters) {

    public StartWorld {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(religions, "religions");
        countries = List.copyOf(countries);
        Objects.requireNonNull(holyCenters, "holyCenters");
        check("countries", countries.size(), map.countries());
        check(
                "holy_centers",
                holyCenters.centers().size(),
                religions.religions().size());
    }

    /** Держава з номером {@code country}. */
    public StartCountry country(int country) {
        Checks.inRange("country", country, 0, countries.size() - 1);
        return countries.get(country);
    }

    /** Державна релігія кожної держави за номером; порожньо — світська. */
    public List<OptionalInt> countryReligions() {
        return countries.stream().map(country -> country.religion().religion()).toList();
    }

    /** Номери держав, чия державна релігія — релігія з номером {@code religion}, за зростанням. */
    public SortedSet<Integer> followers(int religion) {
        Checks.inRange("religion", religion, 0, religions.religions().size() - 1);
        TreeSet<Integer> followers = new TreeSet<>();
        for (int n = 0; n < countries.size(); n++) {
            OptionalInt chosen = countries.get(n).religion().religion();
            if (chosen.isPresent() && chosen.getAsInt() == religion) {
                followers.add(n);
            }
        }
        return Collections.unmodifiableSortedSet(followers);
    }

    private static void check(String field, int actual, int expected) {
        if (actual != expected) {
            throw new ValidationException(
                    ErrorCode.VALUE_OUT_OF_RANGE,
                    ErrorDetails.of("field", field, "value", actual, "expected", expected));
        }
    }
}
