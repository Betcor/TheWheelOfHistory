package kolo.protocol;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "kolo.protocol", importOptions = ImportOption.DoNotIncludeTests.class)
class ProtocolBoundariesTest {

    // Формат на дроті (JSON, фрейми) — деталь кодека: повідомлення лишаються простими значеннями.
    @ArchTest
    static final ArchRule jacksonStaysInCodec = noClasses()
            .that()
            .resideOutsideOfPackage("kolo.protocol.codec..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.fasterxml.jackson..");

    @ArchTest
    static final ArchRule nettyStaysInCodec = noClasses()
            .that()
            .resideOutsideOfPackage("kolo.protocol.codec..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("io.netty..");

    // Протокол під сервером і клієнтом: залежить лише від рушія.
    @ArchTest
    static final ArchRule protocolDependsOnlyOnEngine = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("kolo.server..", "kolo.client..", "kolo.ai..", "kolo.content..");
}
