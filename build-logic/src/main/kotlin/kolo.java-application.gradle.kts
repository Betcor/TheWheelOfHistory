// Модуль із точкою входу (server, client, tools).

plugins {
    id("kolo.java-conventions")
    application
}

tasks.named<JavaExec>("run") {
    // Щоб кирилиця в консолі й логах не ламалася на Windows.
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}
