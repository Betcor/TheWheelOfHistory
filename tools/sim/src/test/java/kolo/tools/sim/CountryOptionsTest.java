package kolo.tools.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CountryOptionsTest {

    @Test
    void seedAloneGivesDefaults() {
        CountryOptions options = CountryOptions.parse(List.of("--seed", "42"));

        assertThat(options.seed()).isEqualTo(42);
        assertThat(options.countries()).isEqualTo(CountryOptions.DEFAULT_COUNTRIES);
        assertThat(options.resources()).isEmpty();
        assertThat(options.rolls()).isFalse();
        assertThat(options.content()).isEmpty();
    }

    @Test
    void readsEveryOptionInAnyOrder() {
        CountryOptions options = CountryOptions.parse(List.of(
                "--rolls", "--resources", "uranium, oil", "--content", "mods/x", "--countries", "40", "--seed", "-7"));

        assertThat(options.seed()).isEqualTo(-7);
        assertThat(options.countries()).isEqualTo(40);
        assertThat(options.resources()).containsExactly("oil", "uranium");
        assertThat(options.rolls()).isTrue();
        assertThat(options.content()).contains(Path.of("mods/x"));
    }

    @Test
    void acceptsCountryLimits() {
        assertThat(CountryOptions.parse(List.of("--seed", "1", "--countries", "1"))
                        .countries())
                .isEqualTo(1);
        assertThat(CountryOptions.parse(List.of("--seed", "1", "--countries", "1000"))
                        .countries())
                .isEqualTo(CountryOptions.MAX_COUNTRIES);
    }

    @Test
    void requiresSeed() {
        assertUsage(List.of("--countries", "5"), "error.usage.missing_seed");
        assertUsage(List.of(), "error.usage.missing_seed");
    }

    @Test
    void rejectsBadValues() {
        assertUsage(List.of("--seed", "abc"), "error.usage.not_a_number");
        assertUsage(List.of("--seed", "99999999999999999999"), "error.usage.not_a_number");
        assertUsage(List.of("--seed", "1", "--countries", "0"), "error.usage.out_of_range");
        assertUsage(List.of("--seed", "1", "--countries", "1001"), "error.usage.out_of_range");
        assertUsage(List.of("--seed", "1", "--countries", "99999999999"), "error.usage.out_of_range");
    }

    @Test
    void rejectsMissingValue() {
        assertUsage(List.of("--seed"), "error.usage.missing_value");
        assertUsage(List.of("--seed", "--rolls"), "error.usage.missing_value");
        assertUsage(List.of("--seed", "1", "--content"), "error.usage.missing_value");
    }

    @Test
    void rejectsUnknownAndRepeatedOptions() {
        assertUsage(List.of("--seed", "1", "--year", "3"), "error.usage.unknown_option");
        assertUsage(List.of("--seed", "1", "--seed", "2"), "error.usage.duplicate_option");
        assertUsage(List.of("--seed", "1", "--rolls", "--rolls"), "error.usage.duplicate_option");
        assertUsage(List.of("--seed", "1", "--resources", "a", "--resources", "b"), "error.usage.duplicate_option");
    }

    @Test
    void everyUsageErrorHasText() {
        UsageException e = new UsageException("error.usage.out_of_range", "--countries", "0", 1, 1000);

        assertThat(e.text()).contains("--countries", "0", "1..1000").doesNotContain("{");
    }

    private static void assertUsage(List<String> args, String key) {
        assertThatThrownBy(() -> CountryOptions.parse(args)).isInstanceOfSatisfying(UsageException.class, e -> {
            assertThat(e.key()).isEqualTo(key);
            assertThat(e.text()).isNotBlank().doesNotContain("{");
        });
    }
}
