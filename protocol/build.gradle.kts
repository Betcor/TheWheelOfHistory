plugins {
    id("kolo.java-library")
}

dependencies {
    api(project(":engine"))
    implementation(libs.jackson.databind)
    // api: ProtocolPipeline приймає ChannelPipeline — типи Netty в публічному API.
    api(libs.netty.codec)
}
