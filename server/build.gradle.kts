plugins {
    id("kolo.java-application")
    `java-library`
}

dependencies {
    api(project(":protocol"))
    implementation(project(":ai"))
    implementation(project(":content"))
    implementation(libs.jackson.databind)
    implementation(libs.netty.handler)
    implementation(libs.netty.transport)
    implementation(libs.sqlite.jdbc)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
}

application {
    mainClass = "kolo.server.DedicatedServerMain"
    // sqlite-jdbc завантажує нативну бібліотеку; без дозволу JVM попереджає, а згодом блокуватиме.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.named<Test>("test") {
    // DockerfileTest читає образ окремого сервера: зміна в docker/ — привід перезапустити тести.
    inputs.dir(rootProject.layout.projectDirectory.dir("docker"))
        .withPropertyName("docker")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
