package kolo.server.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import kolo.engine.error.ContentException;
import kolo.engine.error.ErrorCode;
import kolo.server.TestServers;
import org.junit.jupiter.api.Test;

class LazyContentTest {

    @Test
    void loadsOnFirstUseOnly() {
        AtomicInteger loads = new AtomicInteger();
        LazyContent content = new LazyContent(() -> {
            loads.incrementAndGet();
            return TestServers.CONTENT;
        });

        assertThat(loads).hasValue(0);
        assertThat(content.get()).isSameAs(TestServers.CONTENT);
        assertThat(content.get()).isSameAs(TestServers.CONTENT);
        assertThat(loads).hasValue(1);
    }

    @Test
    void failureIsNotRemembered() {
        AtomicInteger loads = new AtomicInteger();
        LazyContent content = new LazyContent(() -> {
            if (loads.incrementAndGet() == 1) {
                throw new ContentException(ErrorCode.CONTENT_READ_FAILED, Map.of());
            }
            return TestServers.CONTENT;
        });

        assertThatThrownBy(content::get).isInstanceOf(ContentException.class);
        assertThat(content.get()).isSameAs(TestServers.CONTENT);
    }
}
