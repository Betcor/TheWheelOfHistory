package kolo.client.screen;

import java.util.List;
import kolo.client.i18n.Texts;
import kolo.protocol.message.PlayerInfo;

/** Підписи гравців у лобі й на карті. */
public final class PlayerLabels {

    private PlayerLabels() {}

    /** Гравець у лобі: нікнейм, позначки «хост» і «ви». */
    public static String lobby(Texts texts, PlayerInfo player, boolean me) {
        String key = player.host()
                ? (me ? "lobby.player_host_me" : "lobby.player_host")
                : (me ? "lobby.player_me" : "lobby.player");
        return texts.text(key, player.nickname());
    }

    /** Гравець у лобі завантаженого світу: ще й держава, вільне місце чи гість без держави. */
    public static String savedLobby(Texts texts, PlayerInfo player, boolean me) {
        String label = lobby(texts, player, me);
        if (player.country().isEmpty()) {
            return texts.text("lobby.guest", label);
        }
        return texts.text(
                player.connected() ? "lobby.seat" : "lobby.seat_free",
                label,
                player.country().getAsInt() + 1);
    }

    /** Чи це гість лобі завантаженого світу — на зв'язку, але без держави. */
    public static boolean guest(PlayerInfo player) {
        return player.connected() && player.country().isEmpty();
    }

    /** Чи це вільне місце гравця завантаженого світу. */
    public static boolean freeSeat(PlayerInfo player) {
        return !player.connected() && player.country().isPresent();
    }

    /** Гравець на карті: готовий, не на зв'язку чи ще думає над наказами. */
    public static String game(Texts texts, PlayerInfo player) {
        String key = !player.connected() ? "map.player_offline" : player.ready() ? "map.player_ready" : "map.player";
        return texts.text(key, player.nickname());
    }

    /** Нікнейми гравців на зв'язку, яких ще чекає рік, через кому; порожньо — не чекаємо нікого. */
    public static String waitingFor(List<PlayerInfo> players) {
        return String.join(
                ", ",
                players.stream()
                        .filter(p -> p.connected() && !p.ready())
                        .map(PlayerInfo::nickname)
                        .toList());
    }
}
