plugins {
    id("inkflow.android.application")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
}

android {
    defaultConfig {
        applicationId = "io.github.leonardosponton.inkflow"
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        debug {
            // `-Pinkflow.appIdSuffix=.demo` installs a separate copy beside the regular one (screenshots, demos).
            providers.gradleProperty("inkflow.appIdSuffix").orNull?.let { applicationIdSuffix = it }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // No release keystore in the repo: local release builds use the debug key.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":domain:project"))
    implementation(project(":domain:canvas"))
    implementation(project(":domain:diagram"))
    implementation(project(":domain:uml"))
    implementation(project(":domain:dbmodel"))
    implementation(project(":domain:flowchart"))
    implementation(project(":data:repository"))
    implementation(project(":feature:home"))
    implementation(project(":feature:editor"))
    implementation(project(":feature:settings"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.serialization.json)
    baselineProfile(project(":benchmark"))
    testImplementation(libs.junit4)
}
