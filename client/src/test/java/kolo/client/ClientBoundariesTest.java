package kolo.client;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Межі модуля {@code client}. */
@AnalyzeClasses(packages = "kolo", importOptions = ImportOption.DoNotIncludeTests.class)
class ClientBoundariesTest {

    // Клієнт говорить із сервером лише через протокол; модуль server потрібен тільки заради
    // вбудованого сервера для одиночної гри, hot-seat і LAN-хоста.
    @ArchTest
    static final ArchRule clientUsesOnlyEmbeddedServer = noClasses()
            .that()
            .resideInAPackage("kolo.client..")
            .should()
            .dependOnClassesThat(resideInAPackage("kolo.server..").and(not(simpleName("EmbeddedServer"))))
            .allowEmptyShould(true);
}
