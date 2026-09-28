plugins {
    id("kolo.java-library")
}

dependencies {
    api(project(":engine"))
    implementation(libs.jackson.databind)
    implementation(libs.netty.codec)
}
