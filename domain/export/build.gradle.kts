plugins {
    id("inkflow.jvm.library")
}

dependencies {
    api(project(":domain:project"))
    api(project(":domain:canvas"))
}
