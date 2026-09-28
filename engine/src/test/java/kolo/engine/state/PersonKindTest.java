package kolo.engine.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PersonKindTest {

    @Test
    void kindsMatchDesign() {
        // GD §12.3: дев'ять типів постатей.
        assertThat(Arrays.stream(PersonKind.values()).map(PersonKind::key))
                .containsExactly(
                        "scientist",
                        "general",
                        "admiral",
                        "diplomat",
                        "magnate",
                        "prophet",
                        "dissident",
                        "artist",
                        "pretender");
    }
}
