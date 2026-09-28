package kolo.content;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "kolo.content", importOptions = ImportOption.DoNotIncludeTests.class)
class ContentBoundariesTest {

    // Модель потрапляє в рушій, тому не може тягнути за собою завантажувач (Jackson, файли).
    @ArchTest
    static final ArchRule modelIsIndependentOfLoading = noClasses()
            .that()
            .resideInAPackage("kolo.content.model..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("kolo.content.loader..", "kolo.content.validation..", "com.fasterxml.jackson..")
            .allowEmptyShould(true);
}
