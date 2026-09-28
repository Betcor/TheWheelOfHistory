package kolo.engine;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Межі модуля {@code engine}: рушій бачить лише модель контенту. */
@AnalyzeClasses(packages = "kolo", importOptions = ImportOption.DoNotIncludeTests.class)
class EngineBoundariesTest {

    // Gradle пускає content у classpath рушія цілком, тож заборону завантажувача перевіряємо тут.
    @ArchTest
    static final ArchRule engineUsesOnlyContentModel = noClasses()
            .that()
            .resideInAPackage("kolo.engine..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "kolo.content.loader..",
                    "kolo.content.validation..",
                    "kolo.ai..",
                    "kolo.protocol..",
                    "kolo.server..",
                    "kolo.client..",
                    "kolo.tools..")
            .allowEmptyShould(true);
}
