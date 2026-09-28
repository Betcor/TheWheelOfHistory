plugins {
    id("kolo.java-library")
}

dependencies {
    // Рушій бачить лише модель контенту; заборону kolo.content.loader перевіряє ArchUnit.
    api(project(":content"))
    implementation(libs.jts.core)
}
