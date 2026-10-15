plugins {
    id("inkflow.android.library")
}

dependencies {
    implementation(project(":domain:project"))
    implementation(project(":domain:canvas"))
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(project(":core:testing"))
}
