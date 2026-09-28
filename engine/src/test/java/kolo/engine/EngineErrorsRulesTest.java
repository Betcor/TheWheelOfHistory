package kolo.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import kolo.engine.archfixture.Allowed;
import kolo.engine.archfixture.Violations;
import org.junit.jupiter.api.Test;

/** Перевіряє, що правила помилок не порожні: ловлять навмисні порушення й пропускають дозволене. */
class EngineErrorsRulesTest {

    @Test
    void ruleCatchesUncodedException() {
        JavaClasses classes = new ClassFileImporter().importClasses(Violations.ThrowsIllegalArgument.class);

        assertThat(EngineErrorsTest.noUncodedExceptions.evaluate(classes).hasViolation())
                .isTrue();
    }

    @Test
    void allowedErrorsPassRule() {
        JavaClasses classes = new ClassFileImporter().importClasses(Allowed.class);

        assertThat(EngineErrorsTest.noUncodedExceptions
                        .evaluate(classes)
                        .getFailureReport()
                        .getDetails())
                .isEmpty();
    }
}
