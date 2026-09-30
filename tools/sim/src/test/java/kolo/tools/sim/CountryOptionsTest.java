package kolo.tools.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import kolo.engine.state.NpcShare;
import kolo.engine.state.WorldLimits;
import org.junit.jupiter.api.Test;

class CountryOptionsTest {

    @Test
    void seedAloneGivesDefaults() {
        CountryOptions options = CountryOptions.parse(List.of("--seed", "42"));

        assertThat(options.seed()).isEqualTo(42);
        assertThat(options.players()).isEqualTo(CountryOptions.DEFAULT_PLAYERS);
        assertThat(options.npcShare()).isEqualTo(NpcShare.NORMAL);
        assertThat(options.country()).isZero();
        assertThat(options.rolls()).isFalse();
        assertThat(options.content()).isEmpty();
    }

    @Test
    void readsEveryOptionInAnyOrder() {
        CountryOptions options = CountryOptions.parse(List.of(
                "--rolls", "--npc", "many", "--content", "mods/x", "--country", "3", "--players", "4", "--seed", "-7"));

        assertThat(options.seed()).isEqualTo(-7);
        assertThat(options.players()).isEqualTo(4);
        assertThat(options.npcShare()).isEqualTo(NpcShare.MANY);
        assertThat(options.country()).isEqualTo(3);
        assertThat(options.rolls()).isTrue();
        assertThat(options.content()).contains(Path.of("mods/x"));
    }

    @Test
    void acceptsWorldLimits() {
        assertThat(CountryOptions.parse(List.of("--seed", "1", "--players", "1"))
                        .players())
                .isEqualTo(WorldLimits.MIN_PLAYERS);
        assertThat(CountryOptions.parse(List.of("--seed", "1", "--players", "16"))
                        .players())
                .isEqualTo(WorldLimits.MAX_PLAYERS);
        assertThat(CountryOptions.parse(List.of("--seed", "1", "--country", "39"))
                        .country())
                .isEqualTo(WorldLimits.MAX_COUNTRIES - 1);
        assertThat(CountryOptions.parse(List.of("--seed", "1", "--npc", "few")).npcShare())
                .isEqualTo(NpcShare.FEW);
    }

    @Test
    void requiresSeed() {
        assertUsage(List.of("--players", "5"), "error.usage.missing_seed");
        assertUsage(List.of(), "error.usage.missing_seed");
    }

    @Test
    void rejectsBadValues() {
        assertUsage(List.of("--seed", "abc"), "error.usage.not_a_number");
        assertUsage(List.of("--seed", "99999999999999999999"), "error.usage.not_a_number");
        assertUsage(List.of("--seed", "1", "--players", "0"), "error.usage.out_of_range");
        assertUsage(List.of("--seed", "1", "--players", "17"), "error.usage.out_of_range");
        assertUsage(List.of("--seed", "1", "--players", "99999999999"), "error.usage.out_of_range");
        assertUsage(List.of("--seed", "1", "--country", "-1"), "error.usage.out_of_range");
        assertUsage(List.of("--seed", "1", "--country", "40"), "error.usage.out_of_range");
        assertUsage(List.of("--seed", "1", "--npc", "lots"), "error.usage.unknown_npc_share");
        assertUsage(List.of("--seed", "1", "--npc", "NORMAL"), "error.usage.unknown_npc_share");
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
        assertUsage(List.of("--seed", "1", "--npc", "few", "--npc", "many"), "error.usage.duplicate_option");
        assertUsage(List.of("--seed", "1", "--country", "1", "--country", "2"), "error.usage.duplicate_option");
        assertUsage(List.of("--seed", "1", "--players", "1", "--players", "2"), "error.usage.duplicate_option");
        assertUsage(List.of("--seed", "1", "--countries", "20"), "error.usage.unknown_option");
    }

    @Test
    void everyUsageErrorHasText() {
        UsageException e = new UsageException("error.usage.out_of_range", "--players", "0", 1, 16);

        assertThat(e.text()).contains("--players", "0", "1..16").doesNotContain("{");
        assertThat(new UsageException("error.usage.no_such_country", 7, 5, 4).text())
                .contains("7", "5", "0..4")
                .doesNotContain("{");
    }

    private static void assertUsage(List<String> args, String key) {
        assertThatThrownBy(() -> CountryOptions.parse(args)).isInstanceOfSatisfying(UsageException.class, e -> {
            assertThat(e.key()).isEqualTo(key);
            assertThat(e.text()).isNotBlank().doesNotContain("{");
        });
    }
}
