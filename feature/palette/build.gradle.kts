plugins {
    id("inkflow.android.feature")
}

dependencies {
    implementation(project(":domain:diagram"))
    implementation(project(":feature:canvas-render"))
}
