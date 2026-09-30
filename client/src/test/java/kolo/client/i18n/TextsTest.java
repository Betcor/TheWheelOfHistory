package kolo.client.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.MissingResourceException;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import org.junit.jupiter.api.Test;

class TextsTest {

    private static final Texts TEXTS = Texts.ukrainian();

    @Test
    void substitutesArgumentsAndKeepsApostrophes() {
        assertThat(TEXTS.text("map.info.summary", "Провінція 3", "Велор")).isEqualTo("Провінція 3 — Велор");
        assertThat(TEXTS.text("error.unknown_reference")).isEqualTo("Посилання на об'єкт, якого немає.");
    }

    @Test
    void errorSubstitutesNamedDetails() {
        assertThat(TEXTS.error(
                        ErrorCode.VERSION_MISMATCH,
                        ErrorDetails.of("part", "protocol", "client", 2L, "server", 1L, "extra", "x")))
                .isEqualTo("Версія гри або контенту не збігається з сервером (у вас — 2, на сервері — 1).");
        assertThat(TEXTS.error(ErrorCode.NOT_FOUND, ErrorDetails.of("id", "cty_1")))
                .isEqualTo(TEXTS.text("error.not_found"));
    }

    @Test
    void missingKeyIsBug() {
        assertThat(TEXTS.has("app.title")).isTrue();
        assertThat(TEXTS.has("no.such.key")).isFalse();
        assertThatThrownBy(() -> TEXTS.text("no.such.key")).isInstanceOf(MissingResourceException.class);
    }
}
