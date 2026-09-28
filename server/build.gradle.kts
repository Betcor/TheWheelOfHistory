plugins {
    id("kolo.java-application")
    `java-library`
}

dependencies {
    api(project(":protocol"))
    implementation(project(":ai"))
    implementation(libs.netty.handler)
    implementation(libs.netty.transport)
    implementation(libs.sqlite.jdbc)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
}

application {
    mainClass = "kolo.server.DedicatedServerMain"
}
