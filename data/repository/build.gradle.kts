plugins {
    id("inkflow.android.library")
    id("inkflow.android.hilt")
}

dependencies {
    api(project(":domain:project"))
    api(project(":domain:canvas"))
    api(project(":domain:export"))
    implementation(project(":data:local"))
    implementation(project(":data:files"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(project(":core:testing"))
}
