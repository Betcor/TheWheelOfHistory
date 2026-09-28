plugins {
    id("kolo.java-library")
}

dependencies {
    // Рушій — нижній шар: модель контенту живе в ньому, завантажувач (модуль content) залежить від рушія.
    implementation(libs.jts.core)
}
