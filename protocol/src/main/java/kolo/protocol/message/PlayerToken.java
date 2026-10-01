package kolo.protocol.message;

import kolo.engine.error.Checks;

/**
 * Номер гравця й токен, з якими клієнт посідає своє місце в завантаженому світі ({@link ClientMessage.LoadWorld}).
 *
 * @param player номер гравця з {@link ServerMessage.Joined}
 * @param token токен гравця з {@link ServerMessage.Joined}
 */
public record PlayerToken(int player, String token) {

    public PlayerToken {
        Checks.inRange("player", player, 1, Integer.MAX_VALUE);
        Checks.notBlank("token", token);
        Checks.inRange("token", token.length(), 1, ClientMessage.MAX_TOKEN_LENGTH);
    }
}
