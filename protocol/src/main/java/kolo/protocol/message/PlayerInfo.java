package kolo.protocol.message;

import java.util.Objects;
import java.util.OptionalInt;
import kolo.engine.error.Checks;

/**
 * Гравець сесії, як його бачать інші: у лобі ({@link ServerMessage.Lobby}) і під час гри ({@link
 * ServerMessage.Players}).
 *
 * @param number номер гравця в сесії; не змінюється й не використовується вдруге
 * @param nickname нікнейм ({@link Nicknames})
 * @param host чи це хост сесії
 * @param connected чи гравець зараз на зв'язку; у лобі — завжди так
 * @param ready чи натиснув «Готово» в поточному році; у лобі — ні
 * @param country номер держави гравця; у лобі — порожньо
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
