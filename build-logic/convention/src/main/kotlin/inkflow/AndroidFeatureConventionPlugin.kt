package inkflow

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** A UI feature: Android library + Compose + Hilt + the shared design system. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("inkflow.android.library")
        pluginManager.apply("inkflow.android.compose")
        pluginManager.apply("inkflow.android.hilt")
        dependencies {
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:common"))
            add("implementation", project(":core:model"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-hilt-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("kotlinx-coroutines-android"))
            add("implementation", libs.lib("compose-material-icons-extended"))
            add("implementation", libs.lib("androidx-activity-compose"))
            add("testImplementation", project(":core:testing"))
        }
    }
}
