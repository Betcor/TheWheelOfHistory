plugins {
    id("kolo.java-library")
}

dependencies {
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
}
