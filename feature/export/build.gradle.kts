plugins {
    id("inkflow.android.feature")
}

dependencies {
    implementation(project(":domain:export"))
    implementation(project(":domain:project"))
    implementation(project(":domain:canvas"))
    implementation(project(":domain:dbmodel"))
    implementation(project(":domain:uml"))
    implementation(project(":feature:canvas-render"))
    implementation(libs.androidx.core.ktx)
}
