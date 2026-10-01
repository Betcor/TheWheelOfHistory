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
