pluginManagement {
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

rootProject.name = "Chiron"
include(":app")

include(":core:model")
include(":core:common")
include(":core:database")
include(":core:ui")
include(":core:spotify")

include(":feature:exercises")
include(":feature:history")
include(":feature:goals")
include(":feature:timer")
