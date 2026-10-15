package inkflow

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

internal const val COMPILE_SDK = 37
internal const val TARGET_SDK = 36
internal const val MIN_SDK = 29
internal const val BASE_NAMESPACE = "io.github.leonardosponton.inkflow"

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

/** `:feature:canvas-render` → `io.github.leonardosponton.inkflow.feature.canvasrender`. */
internal fun Project.defaultNamespace(): String =
    BASE_NAMESPACE + path.replace("-", "").replace(':', '.')

internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.apply {
        compileSdk = COMPILE_SDK
        defaultConfig.minSdk = MIN_SDK
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
    }
    extensions.getByType<KotlinAndroidProjectExtension>().configureKotlin()
}

internal fun Project.configureKotlinJvm() {
    extensions.getByType<KotlinJvmProjectExtension>().apply {
        jvmToolchain(21)
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
        configureKotlin()
    }
    extensions.getByType<org.gradle.api.plugins.JavaPluginExtension>().apply {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

private fun KotlinBaseExtension.configureKotlin() {
    when (this) {
        is KotlinAndroidProjectExtension -> compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(false)
            freeCompilerArgs.addAll(OPT_INS)
        }
        is KotlinJvmProjectExtension -> compilerOptions { freeCompilerArgs.addAll(OPT_INS) }
        else -> Unit
    }
}

private val OPT_INS = listOf(
    "-opt-in=kotlin.RequiresOptIn",
    "-Xconsistent-data-class-copy-visibility",
)
