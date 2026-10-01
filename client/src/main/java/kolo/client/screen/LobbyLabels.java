package kolo.client.screen;

import kolo.client.i18n.Texts;
import kolo.engine.state.WorldState;
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

    /** Збережений світ: ім'я, рік і гравці. */
    public static String world(Texts texts, WorldInfo world) {
        return texts.text(
                "load.world",
                world.name(),
                WorldState.year(world.turn()),
                world.players().isEmpty() ? texts.text("load.no_players") : String.join(", ", world.players()));
    }
}
