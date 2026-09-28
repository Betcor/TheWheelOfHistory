// Спільні налаштування для всіх Java-модулів: toolchain, тести, форматування.

plugins {
    java
    id("com.diffplug.spotless")
}

val libs = the<VersionCatalogsExtension>().named("libs")

fun lib(alias: String) = libs.findLibrary(alias).get()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.findVersion("java").get().requiredVersion)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = libs.findVersion("java").get().requiredVersion.toInt()
    // -parameters потрібен jqwik для імен параметрів у звітах property-тестів.
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror", "-parameters"))
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
}

dependencies {
    testImplementation(platform(lib("junit-bom")))
    testImplementation(lib("junit-jupiter"))
    testImplementation(lib("assertj-core"))
    testImplementation(lib("jqwik"))
    testImplementation(lib("archunit-junit5"))
    testRuntimeOnly(lib("junit-platform-launcher"))
}

tasks.withType<Test>().configureEach {
    // JUnit Jupiter, jqwik і ArchUnit — окремі рушії JUnit Platform, запускаються всі.
    useJUnitPlatform()
    systemProperty("file.encoding", "UTF-8")
    // Інакше jqwik створює .jqwik-database прямо в каталозі модуля.
    systemProperty("jqwik.database", layout.buildDirectory.file("jqwik-database").get().asFile.absolutePath)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

spotless {
    java {
        palantirJavaFormat(libs.findVersion("palantir-java-format").get().requiredVersion)
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
        targetExclude("**/build/**", "**/vendor/**")
    }
}
