package kolo.client.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import kolo.client.i18n.Texts;
import kolo.engine.state.NpcShare;
import kolo.protocol.message.LobbySetup;
import kolo.protocol.message.Nicknames;
import kolo.protocol.message.PlayerInfo;
import kolo.protocol.message.WorldInfo;
import org.junit.jupiter.api.Test;

/** Нікнейм з поля введення, підписи гравців і світу в лобі. */
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
    void savedLobbyLabelsShowSeatsAndGuests() {
        PlayerInfo seated = new PlayerInfo(1, "Оля", true, true, false, OptionalInt.of(0));
        PlayerInfo free = new PlayerInfo(2, "Ігор", false, false, false, OptionalInt.of(1));
        PlayerInfo guest = new PlayerInfo(3, "Марко", false, true, false, OptionalInt.empty());

        assertThat(PlayerLabels.savedLobby(TEXTS, seated, true)).isEqualTo("Оля — хост (ви) · держава №1");
        assertThat(PlayerLabels.savedLobby(TEXTS, free, false)).isEqualTo("Ігор · держава №2 · місце вільне");
        assertThat(PlayerLabels.savedLobby(TEXTS, guest, false)).isEqualTo("Марко · гість без держави");
        assertThat(PlayerLabels.guest(guest)).isTrue();
        assertThat(PlayerLabels.guest(seated)).isFalse();
        assertThat(PlayerLabels.freeSeat(free)).isTrue();
        assertThat(PlayerLabels.freeSeat(seated)).isFalse();
        assertThat(PlayerLabels.freeSeat(guest)).isFalse();
    }

    @Test
    void worldLabelsNameTheSetupAndTheYear() {
        assertThat(LobbyLabels.setup(TEXTS, new LobbySetup.NewWorld(42, NpcShare.FEW)))
                .isEqualTo("Seed 42 · NPC-держав: " + TEXTS.text("npc_share.few"));
        assertThat(LobbyLabels.setup(TEXTS, new LobbySetup.SavedWorld("world-42", 42, 5)))
                .isEqualTo("Світ «world-42» · seed 42 · продовження з 1975 року");
        assertThat(LobbyLabels.world(TEXTS, new WorldInfo("world-42", Optional.empty(), 42, 5, List.of("Оля", "Ігор"))))
                .isEqualTo("world-42 — 1975 рік · гравці: Оля, Ігор");
        assertThat(LobbyLabels.world(TEXTS, new WorldInfo("old", Optional.empty(), 1, 0, List.of())))
                .isEqualTo("old — 1970 рік · гравці: немає");
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
