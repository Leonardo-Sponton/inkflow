plugins {
    id("inkflow.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":domain:project"))
    implementation(project(":domain:canvas"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    androidTestImplementation(libs.androidx.room.testing)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
