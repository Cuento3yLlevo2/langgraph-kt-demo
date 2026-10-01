pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        // langgraph-kt is not on Maven Central yet. Run `./gradlew publishToMavenLocal` in the library repo first.
        mavenLocal {
            content { includeGroup("io.github.cuento3yllevo2") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "langgraph-kt-demo"

include(":composeApp")
