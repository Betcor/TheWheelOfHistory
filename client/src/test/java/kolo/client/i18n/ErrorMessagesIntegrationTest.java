package kolo.client.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.TreeSet;
import kolo.engine.error.ErrorCode;
import org.junit.jupiter.api.Test;

/** Коди помилок рушія ↔ тексти клієнта: гравець ніколи не має побачити сирий ключ замість повідомлення. */
class ErrorMessagesIntegrationTest {

    private static final String BUNDLE = "/i18n/messages_uk.properties";

    @Test
    void everyErrorCodeHasUkrainianText() throws IOException {
        Properties messages = load();

        for (ErrorCode code : ErrorCode.values()) {
            assertThat(messages.getProperty(code.key())).as(code.key()).isNotBlank();
        }
    }

    @Test
    void noStaleErrorTexts() throws IOException {
        TreeSet<String> known = new TreeSet<>();
        for (ErrorCode code : ErrorCode.values()) {
            known.add(code.key());
        }

        assertThat(load().stringPropertyNames())
                .filteredOn(key -> key.startsWith("error."))
                .allSatisfy(key -> assertThat(known).contains(key));
    }

    private static Properties load() throws IOException {
        try (InputStream in = ErrorMessagesIntegrationTest.class.getResourceAsStream(BUNDLE)) {
            assertThat(in).as(BUNDLE).isNotNull();
            Properties properties = new Properties();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            return properties;
        }
    }
}
