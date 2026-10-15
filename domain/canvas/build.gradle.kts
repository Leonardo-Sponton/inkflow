plugins {
    id("inkflow.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":domain:diagram"))
}
