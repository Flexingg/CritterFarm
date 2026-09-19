pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        // Pinned to versions already present in the local Gradle cache on this
        // machine so the first build resolves fast (and offline-reliably).
        id("com.android.application") version "8.13.1"
        id("org.jetbrains.kotlin.android") version "2.1.0"
        id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
        id("com.google.devtools.ksp") version "2.1.0-1.0.29"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CritterFarm"
include(":app")
