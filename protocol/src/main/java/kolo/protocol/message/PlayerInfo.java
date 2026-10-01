package kolo.protocol.message;

import java.util.Objects;
import java.util.OptionalInt;
import kolo.engine.error.Checks;

/**
 * Гравець сесії, як його бачать інші: у лобі ({@link ServerMessage.Lobby}) і під час гри ({@link
 * ServerMessage.Players}).
 *
 * <p>У лобі нового світу держав ще немає. У лобі завантаженого — гравці світу з державами (на зв'язку чи ще ні — тоді
 * місце вільне) і гості без держави: їм хост віддає вільні місця ({@link ClientMessage.AssignSeat}).
 *
 * @param number номер гравця в сесії; гравець світу має його назавжди, гість — доки не отримає місце
 * @param nickname нікнейм ({@link Nicknames})
 * @param host чи це хост сесії
 * @param connected чи гравець зараз на зв'язку; у лобі нового світу — завжди так
 * @param ready чи натиснув «Готово» в поточному році; у лобі — ні
 * @param country номер держави гравця; у лобі нового світу й у гостя — порожньо
 */
public record PlayerInfo(
        int number, String nickname, boolean host, boolean connected, boolean ready, OptionalInt country) {

    public PlayerInfo {
        Checks.inRange("number", number, 1, Integer.MAX_VALUE);
        Nicknames.check("nickname", nickname);
        Objects.requireNonNull(country, "country");
        if (country.isPresent()) {
            Checks.inRange("country", country.getAsInt(), 0, Integer.MAX_VALUE);
        }
    }
}
