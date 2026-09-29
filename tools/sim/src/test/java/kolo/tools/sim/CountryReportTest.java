package kolo.tools.sim;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CountryReportTest {

    @Test
    void formatsBasisPointsAsPercent() {
        assertThat(CountryReport.percent(0)).isEqualTo("0%");
        assertThat(CountryReport.percent(20)).isEqualTo("0,2%");
        assertThat(CountryReport.percent(5)).isEqualTo("0,05%");
        assertThat(CountryReport.percent(150)).isEqualTo("1,5%");
        assertThat(CountryReport.percent(800)).isEqualTo("8%");
        assertThat(CountryReport.percent(1234)).isEqualTo("12,34%");
        assertThat(CountryReport.percent(10_000)).isEqualTo("100%");
        assertThat(CountryReport.percent(-150)).isEqualTo("−1,5%");
    }

    @Test
    void formatsSignedNumbers() {
        assertThat(CountryReport.signed(10)).isEqualTo("+10");
        assertThat(CountryReport.signed(0)).isEqualTo("0");
        assertThat(CountryReport.signed(-3)).isEqualTo("−3");
        assertThat(CountryReport.signed(Integer.MIN_VALUE)).isEqualTo("−2147483648");
    }
}
