package inkflow

import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * Pure Kotlin module (no Android). Domain and geometry modules must keep ≥ 80 % line coverage,
 * enforced by `koverVerify` (part of `check`).
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        pluginManager.apply("org.jetbrains.kotlinx.kover")
        pluginManager.apply("inkflow.quality")
        configureKotlinJvm()
        tasks.withType<Test>().configureEach { useJUnitPlatform() }
        dependencies {
            add("testImplementation", platform("org.junit:junit-bom:${libs.findVersion("junitJupiter").get()}"))
            add("testImplementation", libs.lib("junit-jupiter"))
            add("testRuntimeOnly", libs.lib("junit-platform-launcher"))
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
            add("testImplementation", libs.lib("turbine"))
        }
        if (path.startsWith(":domain:") || path == ":core:geometry") {
            extensions.configure<KoverProjectExtension> {
                reports.verify.rule { minBound(MIN_DOMAIN_COVERAGE) }
            }
        }
    }

    private companion object {
        const val MIN_DOMAIN_COVERAGE = 80
    }
}
