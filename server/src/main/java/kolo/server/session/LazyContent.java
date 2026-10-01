package kolo.server.session;

import java.util.Objects;
import java.util.function.Supplier;
import kolo.engine.content.ContentPack;

/**
 * Контент, що завантажується при першому зверненні: старт клієнта до меню його не чекає. Невдале завантаження не
 * запам'ятовується — наступне звернення спробує знову. Потокобезпечний.
 */
public final class LazyContent implements Supplier<ContentPack> {

    private final Supplier<ContentPack> source;
    private ContentPack content;

    public LazyContent(Supplier<ContentPack> source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    /** @throws kolo.engine.error.ContentException якщо контент невалідний */
    @Override
    public synchronized ContentPack get() {
        if (content == null) {
            content = Objects.requireNonNull(source.get(), "контент");
        }
        return content;
    }
}
