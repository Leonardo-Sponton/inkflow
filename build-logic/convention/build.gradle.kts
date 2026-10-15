plugins {
    `kotlin-dsl`
}

group = "io.github.leonardosponton.inkflow.buildlogic"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
    compileOnly(libs.detekt.gradle.plugin)
    compileOnly(libs.spotless.gradle.plugin)
    compileOnly(libs.kover.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("jvmLibrary") {
            id = "inkflow.jvm.library"
            implementationClass = "inkflow.JvmLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = "inkflow.android.library"
            implementationClass = "inkflow.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "inkflow.android.compose"
            implementationClass = "inkflow.AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "inkflow.android.hilt"
            implementationClass = "inkflow.AndroidHiltConventionPlugin"
        }
        register("androidFeature") {
            id = "inkflow.android.feature"
            implementationClass = "inkflow.AndroidFeatureConventionPlugin"
        }
        register("androidApplication") {
            id = "inkflow.android.application"
            implementationClass = "inkflow.AndroidApplicationConventionPlugin"
        }
        register("quality") {
            id = "inkflow.quality"
            implementationClass = "inkflow.QualityConventionPlugin"
        }
    }
}
