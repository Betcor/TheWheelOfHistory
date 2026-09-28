plugins {
    id("kolo.java-application")
    alias(libs.plugins.javafx)
}

dependencies {
    implementation(project(":server"))
    implementation(project(":protocol"))
    implementation(libs.atlantafx.base)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
}

javafx {
    version = libs.versions.javafx.get()
    modules("javafx.controls")
}

application {
    mainClass = "kolo.client.app.KoloApp"
}

tasks.named<JavaExec>("run") {
    jvmArgs("--enable-native-access=javafx.graphics")
    // Плагін org.openjfx.javafxplugin 0.1.0 звертається до project під час виконання run.
    notCompatibleWithConfigurationCache("org.openjfx.javafxplugin не підтримує configuration cache")
}
