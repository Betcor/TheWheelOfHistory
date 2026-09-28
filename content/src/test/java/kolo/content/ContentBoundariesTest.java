package kolo.content;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "kolo", importOptions = ImportOption.DoNotIncludeTests.class)
class ContentBoundariesTest {

    // Формат файлів (Jackson, YAML) — деталь завантажувача: модель рушія не повинна від нього залежати.
    @ArchTest
    static final ArchRule jacksonStaysInLoader = noClasses()
            .that()
            .resideOutsideOfPackage("kolo.content.loader..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.fasterxml.jackson..");
}
