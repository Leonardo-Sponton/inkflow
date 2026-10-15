plugins {
    id("inkflow.android.library")
    id("inkflow.android.compose")
}

dependencies {
    api(project(":domain:canvas"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    api(libs.androidx.ink.authoring)
    api(libs.androidx.ink.brush)
    api(libs.androidx.ink.geometry)
    api(libs.androidx.ink.rendering)
    api(libs.androidx.ink.strokes)
    implementation(libs.androidx.graphics.core)
    implementation(libs.androidx.input.motionprediction)
    implementation(libs.androidx.customview)
    implementation(libs.kotlinx.coroutines.android)
}
