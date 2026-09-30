package kolo.client.screen;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.client.i18n.Texts;
import kolo.client.map.TestMaps;
import org.junit.jupiter.api.Test;

class ProvinceDescriptionTest {

    private static final Texts TEXTS = Texts.ukrainian();

    @Test
    void landProvince() {
        assertThat(ProvinceDescription.title(TestMaps.MAP, 1, TEXTS)).isEqualTo("Провінція 1");
        assertThat(ProvinceDescription.lines(TestMaps.MAP, 1, TEXTS))
                .containsExactly(
                        "Власник: Королівство Арна",
                        "Місцевість: ліс",
                        "Рельєф: рівнина, висота 20",
                        "Клімат: помірний",
                        "Родючість: 30",
                        "Тече річка",
                        "Вихід до моря");
    }

    @Test
    void playerAndUnclaimedAndLake() {
        assertThat(ProvinceDescription.owner(TestMaps.MAP, 3, TEXTS)).isEqualTo("Республіка Велор (гравець)");
        assertThat(ProvinceDescription.lines(TestMaps.MAP, 3, TEXTS))
                .contains("Рельєф: гори, висота 80", "Річки немає")
                .doesNotContain("Вихід до моря");
        assertThat(ProvinceDescription.owner(TestMaps.MAP, 4, TEXTS)).isEqualTo("Нічийна земля");
        // Озеро — не море: виходу до моря не дає.
        assertThat(ProvinceDescription.lines(TestMaps.MAP, 4, TEXTS)).doesNotContain("Вихід до моря");
    }

    @Test
    void water() {
        assertThat(ProvinceDescription.title(TestMaps.MAP, 2, TEXTS)).isEqualTo("Море");
        assertThat(ProvinceDescription.title(TestMaps.MAP, 5, TEXTS)).isEqualTo("Озеро");
        assertThat(ProvinceDescription.owner(TestMaps.MAP, 2, TEXTS)).isEmpty();
        assertThat(ProvinceDescription.summary(TestMaps.MAP, 2, TEXTS)).isEqualTo("Море");
        assertThat(ProvinceDescription.lines(TestMaps.MAP, 5, TEXTS)).hasSize(1);
    }

    @Test
    void summaryJoinsTitleAndOwner() {
        assertThat(ProvinceDescription.summary(TestMaps.MAP, 0, TEXTS))
                .isEqualTo("Провінція 0 — Республіка Велор (гравець)");
    }
}
