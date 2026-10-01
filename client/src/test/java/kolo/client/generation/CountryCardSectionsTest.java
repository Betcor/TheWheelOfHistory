package kolo.client.generation;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.client.i18n.Texts;
import org.junit.jupiter.api.Test;

class CountryCardSectionsTest {

    private static final Texts TEXTS = Texts.ukrainian();

    @Test
    void populationIsInThousandsOrMillions() {
        assertThat(CountryCardSections.population(TEXTS, 850)).isEqualTo("850 тис.");
        assertThat(CountryCardSections.population(TEXTS, 6_000)).isEqualTo("6 млн");
        assertThat(CountryCardSections.population(TEXTS, 12_480)).isEqualTo("12,4 млн");
    }

    @Test
    void shareIsAPercentWithOneDecimal() {
        assertThat(CountryCardSections.percent(40)).isEqualTo("0,4%");
        assertThat(CountryCardSections.percent(1_200)).isEqualTo("12%");
        assertThat(CountryCardSections.percent(0)).isEqualTo("0%");
    }

    @Test
    void longBackstoryIsShortenedOnTheWheel() {
        String text = "а".repeat(GenerationLabels.MAX_FRAGMENT + 10);

        assertThat(GenerationLabels.shorten(text))
                .hasSize(GenerationLabels.MAX_FRAGMENT)
                .endsWith("…");
        assertThat(GenerationLabels.shorten("коротко")).isEqualTo("коротко");
    }

    @Test
    void numbersAfterPrefix() {
        assertThat(GenerationLabels.number("continent_3", "continent_")).contains(3);
        assertThat(GenerationLabels.number("continent_x", "continent_")).isEmpty();
        assertThat(GenerationLabels.number("continent_", "continent_")).isEmpty();
        assertThat(GenerationLabels.number("other_3", "continent_")).isEmpty();
    }
}
