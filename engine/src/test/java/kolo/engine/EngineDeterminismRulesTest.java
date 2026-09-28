package kolo.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import kolo.engine.archfixture.Allowed;
import kolo.engine.archfixture.Violations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Перевіряє, що правила детермінізму не порожні: ловлять навмисні порушення й пропускають дозволене. */
class EngineDeterminismRulesTest {

    record Case(Class<?> fixture, ArchRule rule) {
        @Override
        public String toString() {
            return fixture.getSimpleName();
        }
    }

    static List<Case> violations() {
        return List.of(
                new Case(Violations.UsesJdkRandom.class, EngineDeterminismTest.noJdkRandomness),
                new Case(Violations.UsesMathRandom.class, EngineDeterminismTest.noJdkRandomness),
                new Case(Violations.UsesClock.class, EngineDeterminismTest.noSystemAccess),
                new Case(Violations.UsesEnvironment.class, EngineDeterminismTest.noSystemAccess),
                new Case(Violations.UsesHashMap.class, EngineDeterminismTest.noHashOrderedCollections),
                new Case(Violations.UsesSetOf.class, EngineDeterminismTest.noHashOrderedCollections),
                new Case(Violations.UsesCollectorsToSet.class, EngineDeterminismTest.noHashOrderedCollections),
                new Case(Violations.UsesParallelStream.class, EngineDeterminismTest.noConcurrency),
                new Case(Violations.UsesCompletableFuture.class, EngineDeterminismTest.noConcurrency),
                new Case(Violations.UsesJavaTime.class, EngineDeterminismTest.noIo));
    }

    @ParameterizedTest
    @MethodSource("violations")
    void ruleCatchesViolation(Case testCase) {
        JavaClasses classes = new ClassFileImporter().importClasses(testCase.fixture());

        assertThat(testCase.rule().evaluate(classes).hasViolation()).isTrue();
    }

    @Test
    void allowedConstructsPassAllRules() {
        JavaClasses classes = new ClassFileImporter().importClasses(Allowed.class);

        for (ArchRule rule : List.of(
                EngineDeterminismTest.noJdkRandomness,
                EngineDeterminismTest.noSystemAccess,
                EngineDeterminismTest.noHashOrderedCollections,
                EngineDeterminismTest.noConcurrency,
                EngineDeterminismTest.noIo)) {
            assertThat(rule.evaluate(classes).getFailureReport().getDetails())
                    .as(rule.getDescription())
                    .isEmpty();
        }
    }
}
