pluginManagement {
    includeBuild("build-logic")
}

plugins {
    // Автоматично завантажує JDK потрібної версії (toolchain), якщо його немає в системі.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "kolo"

include(
    "content",
    "engine",
    "ai",
    "protocol",
    "server",
    "client",
    "tools:sim",
)
