pluginManagement {
    repositories {
        gradlePluginPortal()
    }
    plugins {
        kotlin("jvm") version "2.2.20"
        id("fabric-loom") version "1.11.8"
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "Glade"

include("paper")