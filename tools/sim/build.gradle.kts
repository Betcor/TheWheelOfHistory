plugins {
    id("kolo.java-application")
}

dependencies {
    implementation(project(":ai"))
}

application {
    mainClass = "kolo.tools.sim.SimMain"
}
