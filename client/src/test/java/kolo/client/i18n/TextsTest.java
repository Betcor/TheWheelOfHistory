package kolo.client.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.MissingResourceException;
import org.junit.jupiter.api.Test;

class TextsTest {

    private static final Texts TEXTS = Texts.ukrainian();

    @Test
    void substitutesArgumentsAndKeepsApostrophes() {
        assertThat(TEXTS.text("map.info.summary", "Провінція 3", "Велор")).isEqualTo("Провінція 3 — Велор");
        assertThat(TEXTS.text("error.unknown_reference")).isEqualTo("Посилання на об'єкт, якого немає.");
    }

    @Test
    void missingKeyIsBug() {
        assertThat(TEXTS.has("app.title")).isTrue();
        assertThat(TEXTS.has("no.such.key")).isFalse();
        assertThatThrownBy(() -> TEXTS.text("no.such.key")).isInstanceOf(MissingResourceException.class);
    }
}
