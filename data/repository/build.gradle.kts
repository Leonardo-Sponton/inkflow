plugins {
    id("inkflow.android.library")
    id("inkflow.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":domain:project"))
    api(project(":domain:canvas"))
    api(project(":domain:export"))
    api(project(":data:local"))
    api(project(":data:files"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(project(":core:testing"))
}

extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    // Room schema JSON for MigrationTestHelper.
    sourceSets.getByName("androidTest").assets.directories.add("$rootDir/data/local/schemas")
}

dependencies {
    androidTestImplementation(project(":domain:dbmodel"))
    androidTestImplementation(project(":domain:uml"))
    androidTestImplementation(project(":domain:flowchart"))
    androidTestImplementation(libs.androidx.room.testing)
}
