rootProject.name = "android-world"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

// Backend modules (the core library):
// :desktop -> :application -> :domain; :adapters-* depend on :domain and are wired in by :desktop.
include(":domain", ":application", ":adapters-jvm", ":adapters-adb", ":desktop")
