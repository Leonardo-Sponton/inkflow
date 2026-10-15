plugins {
    id("inkflow.jvm.library")
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    api(project(":domain:project"))
    api(project(":domain:canvas"))
    api(libs.kotlinx.coroutines.test)
}
