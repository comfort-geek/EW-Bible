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
    // DO NOT put versionCatalogs here - Gradle loads gradle/libs.versions.toml automatically
}
rootProject.name = "EW Bible"
include(":app")