package kolo.protocol.message;

import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.state.NpcShare;

/** Що за світ у лобі: новий (згенерується, коли хост почне гру) чи завантажений з файлу. */
public sealed interface LobbySetup permits LobbySetup.NewWorld, LobbySetup.SavedWorld {

    /** seed світу. */
    long seed();

    /**
     * Новий світ.
     *
     * @param seed seed світу (GD §3.4)
     * @param npcShare частка NPC-держав
     */
    record NewWorld(long seed, NpcShare npcShare) implements LobbySetup {

        public NewWorld {
            Objects.requireNonNull(npcShare, "npcShare");
        }
    }

    /**
     * Світ, завантажений з файлу: гравці й держави вже є, лобі лише збирає гравців на їхні місця.
     *
     * @param name ім'я світу в списку збережень ({@link WorldInfo#name()})
     * @param seed seed світу
     * @param turn рік, з якого гра продовжиться (хід, не календарний рік)
     */
    record SavedWorld(String name, long seed, int turn) implements LobbySetup {

        public SavedWorld {
            WorldInfo.checkName(name);
            Checks.inRange("turn", turn, 0, Integer.MAX_VALUE);
        }
    }
}
