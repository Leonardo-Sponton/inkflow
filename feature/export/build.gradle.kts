plugins {
    id("inkflow.android.feature")
}

dependencies {
    implementation(project(":domain:export"))
    implementation(project(":domain:project"))
    implementation(project(":feature:canvas-render"))
}
