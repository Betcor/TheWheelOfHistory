package kolo.client.screen;

import java.util.Objects;
import java.util.concurrent.Executor;
import kolo.client.app.Navigator;
import kolo.client.i18n.Texts;
import kolo.client.net.GameClient;
import kolo.client.state.SessionModel;

/**
 * Те, що потрібно екранам гри: переходи, тексти, гра клієнта, її стан для UI й фоновий потік для важкої роботи.
 *
 * @param background фоновий виконавець (растеризація карти); не потік UI
 */
public record ScreenContext(
        Navigator navigator, Texts texts, GameClient game, SessionModel session, Executor background) {

    public ScreenContext {
        Objects.requireNonNull(navigator, "navigator");
        Objects.requireNonNull(texts, "texts");
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(background, "background");
    }
}
