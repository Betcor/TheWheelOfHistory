// Спільна конфігурація модулів — у convention-плагінах `build-logic/`.
// Плагіни оголошені тут без застосування, щоб усі модулі ділили один classloader
// (інакше спільний build service Spotless ламається в модулях з додатковими плагінами).
plugins {
    id("kolo.java-conventions") apply false
    alias(libs.plugins.javafx) apply false
}
