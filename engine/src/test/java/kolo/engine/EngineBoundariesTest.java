package kolo.engine;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Межі модуля {@code engine}: рушій — нижній шар і не залежить від інших модулів гри. */
@AnalyzeClasses(packages = "kolo", importOptions = ImportOption.DoNotIncludeTests.class)
class EngineBoundariesTest {

    // Граф Gradle і так цього не дозволяє; правило ловить випадкову залежність, додану в build.gradle.kts.
    @ArchTest
    static final ArchRule engineIsBottomLayer = noClasses()
            .that()
            .resideInAPackage("kolo.engine..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "kolo.content..", "kolo.ai..", "kolo.protocol..", "kolo.server..", "kolo.client..", "kolo.tools..")
            .allowEmptyShould(true);
}
