package inkflow

import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** detekt + ktlint (via Spotless) for every module. `check` runs both. */
class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("io.gitlab.arturbosch.detekt")
        pluginManager.apply("com.diffplug.spotless")
        extensions.configure<DetektExtension> {
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            buildUponDefaultConfig = true
            parallel = true
            source.setFrom("src/main/kotlin", "src/test/kotlin", "src/androidTest/kotlin")
        }
        // detekt 1.23 embeds Kotlin 2.0; keep its own classpath on that version.
        configurations.matching { it.name == "detekt" }.configureEach {
            resolutionStrategy.eachDependency {
                if (requested.group == "org.jetbrains.kotlin") useVersion(DETEKT_KOTLIN)
            }
        }
        extensions.configure<SpotlessExtension> {
            kotlin {
                target("src/**/*.kt")
                ktlint(libs.findVersion("ktlint").get().requiredVersion).setEditorConfigPath(rootProject.file(".editorconfig"))
            }
            kotlinGradle {
                target("*.gradle.kts")
                ktlint(libs.findVersion("ktlint").get().requiredVersion).setEditorConfigPath(rootProject.file(".editorconfig"))
            }
        }
    }

    private companion object {
        const val DETEKT_KOTLIN = "2.0.21"
    }
}
