package kolo.engine.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.stream.Stream;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ValidationException;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Спільний формат id сутностей стану: префікс і номер без провідних нулів. */
class EntityIdsTest {

    /** Префікс, конструктор із рядка, фабрика з номера й номер назад. */
    record Kind(String prefix, Function<String, Object> parse, LongFunction<Object> of, Function<Object, Long> number) {

        @Override
        public String toString() {
            return prefix;
        }
    }

    static Stream<Kind> kinds() {
        return Stream.of(
                new Kind("cty_", CountryId::new, CountryId::of, id -> ((CountryId) id).number()),
                new Kind("prv_", ProvinceId::new, ProvinceId::of, id -> ((ProvinceId) id).number()),
                new Kind("sea_", SeaZoneId::new, SeaZoneId::of, id -> ((SeaZoneId) id).number()),
                new Kind("per_", PersonId::new, PersonId::of, id -> ((PersonId) id).number()),
                new Kind("rel_", ReligionId::new, ReligionId::of, id -> ((ReligionId) id).number()));
    }

    static Stream<Arguments> malformed() {
        return kinds().flatMap(kind -> Stream.of(
                        "",
                        kind.prefix(),
                        kind.prefix() + "-1",
                        kind.prefix() + "1a",
                        kind.prefix() + "01",
                        kind.prefix() + "00",
                        kind.prefix().toUpperCase(Locale.ROOT) + "1",
                        " " + kind.prefix() + "1",
                        "xyz_1",
                        kind.prefix() + "9".repeat(19))
                .map(value -> Arguments.of(kind, value)));
    }

    @ParameterizedTest
    @MethodSource("kinds")
    void numberRoundTrips(Kind kind) {
        Object id = kind.of().apply(17);

        assertThat(id).hasToString(kind.prefix() + "17").isEqualTo(kind.parse().apply(kind.prefix() + "17"));
        assertThat(kind.number().apply(id)).isEqualTo(17);
        assertThat(kind.number().apply(kind.of().apply(0))).isZero();
    }

    @ParameterizedTest
    @MethodSource("malformed")
    void rejectsMalformed(Kind kind, String value) {
        assertThatThrownBy(() -> kind.parse().apply(value))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_KEY_FORMAT));
    }

    @ParameterizedTest
    @MethodSource("kinds")
    void rejectsNullAndNegativeNumbers(Kind kind) {
        assertThatThrownBy(() -> kind.parse().apply(null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> kind.of().apply(-1)).isInstanceOf(ValidationException.class);
    }

    @Property
    void anyNumberRoundTrips(@ForAll @LongRange(min = 0, max = Long.MAX_VALUE) long number) {
        kinds().forEach(kind ->
                assertThat(kind.number().apply(kind.of().apply(number))).isEqualTo(number));
    }
}
