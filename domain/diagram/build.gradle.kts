plugins {
    id("inkflow.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:geometry"))
    api(project(":core:common"))
}
