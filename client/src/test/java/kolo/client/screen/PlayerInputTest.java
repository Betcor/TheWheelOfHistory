package kolo.client.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.OptionalInt;
import kolo.client.i18n.Texts;
import kolo.protocol.message.Nicknames;
import kolo.protocol.message.PlayerInfo;
import org.junit.jupiter.api.Test;

/** Нікнейм з поля введення й підписи гравців. */
class PlayerInputTest {

    private static final Texts TEXTS = Texts.ukrainian();

    @Test
    void nicknameIsTrimmedAndChecked() {
        assertThat(NicknameInput.parse("  Оля ")).contains("Оля");
        assertThat(NicknameInput.parse("   ")).isEmpty();
        assertThat(NicknameInput.parse(null)).isEmpty();
        assertThat(NicknameInput.parse("я".repeat(Nicknames.MAX_LENGTH + 1))).isEmpty();
    }

    @Test
    void lobbyLabelsMarkHostAndMe() {
        PlayerInfo host = new PlayerInfo(1, "Оля", true, true, false, OptionalInt.empty());
        PlayerInfo guest = new PlayerInfo(2, "Ігор", false, true, false, OptionalInt.empty());

        assertThat(PlayerLabels.lobby(TEXTS, host, true)).isEqualTo("Оля — хост (ви)");
        assertThat(PlayerLabels.lobby(TEXTS, host, false)).isEqualTo("Оля — хост");
        assertThat(PlayerLabels.lobby(TEXTS, guest, true)).isEqualTo("Ігор (ви)");
        assertThat(PlayerLabels.lobby(TEXTS, guest, false)).isEqualTo("Ігор");
    }

    @Test
    void gameLabelsShowReadinessAndConnection() {
        PlayerInfo ready = new PlayerInfo(1, "Оля", true, true, true, OptionalInt.of(0));
        PlayerInfo thinking = new PlayerInfo(2, "Ігор", false, true, false, OptionalInt.of(1));
        PlayerInfo away = new PlayerInfo(3, "Ліна", false, false, false, OptionalInt.of(2));

        assertThat(PlayerLabels.game(TEXTS, ready)).isEqualTo("Оля — готовий");
        assertThat(PlayerLabels.game(TEXTS, thinking)).isEqualTo("Ігор");
        assertThat(PlayerLabels.game(TEXTS, away)).isEqualTo("Ліна — не на зв'язку");
        // Чекаємо лише тих, хто на зв'язку й ще не готовий.
        assertThat(PlayerLabels.waitingFor(List.of(ready, thinking, away))).isEqualTo("Ігор");
        assertThat(PlayerLabels.waitingFor(List.of(ready, away))).isEmpty();
    }
}
