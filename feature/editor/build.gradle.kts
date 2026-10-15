plugins {
    id("inkflow.android.feature")
}

dependencies {
    implementation(project(":domain:project"))
    implementation(project(":domain:canvas"))
    implementation(project(":domain:diagram"))
    implementation(project(":domain:uml"))
    implementation(project(":domain:dbmodel"))
    implementation(project(":domain:flowchart"))
    implementation(project(":domain:export"))
    implementation(project(":feature:canvas-render"))
    implementation(project(":feature:palette"))
    implementation(project(":feature:export"))
    implementation(libs.compose.material3.adaptive)
    implementation(libs.compose.material3.adaptive.layout)
}
