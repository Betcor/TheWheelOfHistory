package kolo.client.screen;

import java.net.InetSocketAddress;
import kolo.client.i18n.Texts;
import kolo.client.net.RemoteLobby;
import kolo.engine.state.WorldState;
import kolo.protocol.message.LobbyInfo;
import kolo.protocol.message.LobbySetup;
import kolo.protocol.message.WorldInfo;

/** Підписи світу в лобі, у списку лобі й у списку збережень. */
public final class LobbyLabels {

    private LobbyLabels() {}

    /** Що за світ у лобі: новий — seed і частка NPC, завантажений — ім'я, seed і рік; обидва — з таймером ходу. */
    public static String setup(Texts texts, LobbySetup setup) {
        return switch (setup) {
            case LobbySetup.NewWorld world ->
                texts.text(
                        "lobby.settings",
                        world.seed(),
                        texts.text("npc_share." + world.npcShare().key()),
                        TimerLabels.timer(texts, world.timer()));
            case LobbySetup.SavedWorld world ->
                texts.text(
                        "lobby.saved_settings",
                        world.name(),
                        world.seed(),
                        WorldState.year(world.turn()),
                        TimerLabels.timer(texts, world.timer()));
        };
    }

    /** Лобі зі списку: хост, гравці, світ і адреса сервера. */
    public static String remote(Texts texts, RemoteLobby remote) {
        LobbyInfo lobby = remote.lobby();
        return texts.text(
                "connect.lobby", lobby.host(), lobby.players(), setup(texts, lobby.setup()), address(remote.server()));
    }

    /** Адреса сервера так, як її вводять у поле адреси: {@code вузол:порт}, IPv6 — у дужках. */
    static String address(InetSocketAddress address) {
        String host = address.isUnresolved()
                ? address.getHostString()
                : address.getAddress().getHostAddress();
        return (host.indexOf(':') >= 0 ? "[" + host + "]" : host) + ":" + address.getPort();
    }

    /** Збережений світ: ім'я, рік і гравці. */
    public static String world(Texts texts, WorldInfo world) {
        return texts.text(
                "load.world",
                world.name(),
                WorldState.year(world.turn()),
                world.players().isEmpty() ? texts.text("load.no_players") : String.join(", ", world.players()));
    }
}
