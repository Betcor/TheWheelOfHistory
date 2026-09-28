plugins {
    id("kolo.java-library")
}

dependencies {
    // Модель контенту й винятки — у рушії; тут лише завантаження YAML і перевірки між файлами.
    api(project(":engine"))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
}
