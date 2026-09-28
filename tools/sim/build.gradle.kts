plugins {
    id("kolo.java-application")
}

dependencies {
    implementation(project(":ai"))
    implementation(project(":content"))
}

application {
    mainClass = "kolo.tools.sim.SimMain"
}
