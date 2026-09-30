package kolo.client.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import org.junit.jupiter.api.Test;

/** Коди помилок рушія ↔ тексти клієнта: гравець ніколи не має побачити сирий ключ замість повідомлення. */
class ErrorMessagesIntegrationTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]*)}");

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

    /** Текст підставляє лише подробиці, які є в кожному винятку коду: інакше гравець побачив би «{field}». */
    @Test
    void placeholdersUseOnlyRequiredDetails() throws IOException {
        Properties messages = load();

        for (ErrorCode code : ErrorCode.values()) {
            Matcher placeholders = PLACEHOLDER.matcher(messages.getProperty(code.key()));
            while (placeholders.find()) {
                assertThat(code.requiredDetails()).as(code.key()).contains(placeholders.group(1));
            }
        }
    }

    @Test
    void errorTextSubstitutesDetails() {
        String text = Texts.ukrainian()
                .error(ErrorCode.SAVE_VERSION_TOO_NEW, ErrorDetails.of("version", 3L, "supported", 1L, "part", "file"));

        assertThat(text).contains("3").contains("1").doesNotContain("{");
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
