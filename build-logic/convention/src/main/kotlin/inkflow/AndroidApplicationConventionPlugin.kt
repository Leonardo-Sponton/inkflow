package inkflow

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("inkflow.android.compose")
        pluginManager.apply("inkflow.android.hilt")
        pluginManager.apply("inkflow.quality")
        extensions.configure<ApplicationExtension> {
            namespace = BASE_NAMESPACE
            configureAndroidCommon(this)
            defaultConfig.targetSdk = TARGET_SDK
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            lint {
                warningsAsErrors = false
                abortOnError = true
                disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
            }
        }
        registerOfflineManifestCheck()
    }

    /**
     * The app must stay 100 % offline: fail `check` if any merged manifest (ours or a library's)
     * requests network access.
     */
    private fun Project.registerOfflineManifestCheck() {
        val verify = tasks.register("verifyNoNetworkPermission") {
            group = "verification"
            description = "Fails if the merged manifest declares a network permission."
            val manifests = layout.buildDirectory.dir("intermediates/merged_manifest")
            dependsOn("processDebugMainManifest", "processReleaseMainManifest")
            inputs.dir(manifests)
            doLast {
                val offenders = manifests.get().asFile.walkTopDown()
                    .filter { it.name == "AndroidManifest.xml" }
                    .filter { file -> FORBIDDEN.any { file.readText().contains(it) } }
                    .toList()
                if (offenders.isNotEmpty()) {
                    throw GradleException("Network permission found in: ${offenders.joinToString()}")
                }
            }
        }
        tasks.named("check") { dependsOn(verify) }
    }

    private companion object {
        val FORBIDDEN = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
        )
    }
}
