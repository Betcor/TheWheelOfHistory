package kolo.protocol.message;

import java.util.Objects;
import kolo.engine.error.Checks;
import kolo.engine.state.NpcShare;
import kolo.engine.state.TurnTimer;

/** Що за світ у лобі: новий (згенерується, коли хост почне гру) чи завантажений з файлу. */
public sealed interface LobbySetup permits LobbySetup.NewWorld, LobbySetup.SavedWorld {

    /** seed світу. */
    long seed();

    /** Таймер ходу, з яким гра піде (GD §6.1); хост змінює його в лобі. */
    TurnTimer timer();

    /** Те саме лобі з іншим таймером ходу. */
    LobbySetup withTimer(TurnTimer timer);

    /**
     * Новий світ.
     *
     * @param seed seed світу (GD §3.4)
     * @param npcShare частка NPC-держав
     * @param timer таймер ходу
     */
    record NewWorld(long seed, NpcShare npcShare, TurnTimer timer) implements LobbySetup {

        public NewWorld {
            Objects.requireNonNull(npcShare, "npcShare");
            Objects.requireNonNull(timer, "timer");
        }

        @Override
        public NewWorld withTimer(TurnTimer timer) {
            return new NewWorld(seed, npcShare, timer);
        }
    }

    /**
     * Світ, завантажений з файлу: гравці й держави вже є, лобі лише збирає гравців на їхні місця.
     *
     * @param name ім'я світу в списку збережень ({@link WorldInfo#name()})
     * @param seed seed світу
     * @param turn рік, з якого гра продовжиться (хід, не календарний рік)
     * @param timer таймер ходу; спершу — збережений у світі
     */
    record SavedWorld(String name, long seed, int turn, TurnTimer timer) implements LobbySetup {

        public SavedWorld {
            WorldInfo.checkName(name);
            Checks.inRange("turn", turn, 0, Integer.MAX_VALUE);
            Objects.requireNonNull(timer, "timer");
        }

        @Override
        public SavedWorld withTimer(TurnTimer timer) {
            return new SavedWorld(name, seed, turn, timer);
        }
    }
}
