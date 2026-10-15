plugins {
    id("inkflow.jvm.library")
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
}

dependencies {
    testImplementation(project(":core:testing"))
}
