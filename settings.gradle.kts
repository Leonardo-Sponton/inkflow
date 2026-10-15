pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Inkflow"

include(":app")
include(":benchmark")
include(":core:model", ":core:geometry", ":core:common", ":core:designsystem", ":core:testing")
include(
    ":domain:project",
    ":domain:canvas",
    ":domain:diagram",
    ":domain:uml",
    ":domain:dbmodel",
    ":domain:flowchart",
    ":domain:export",
)
include(":data:local", ":data:files", ":data:repository")
include(
    ":feature:home",
    ":feature:editor",
    ":feature:canvas-render",
    ":feature:palette",
    ":feature:export",
    ":feature:settings",
)
