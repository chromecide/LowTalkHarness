pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            name = "AzureDoom Maven"
            url = uri("https://maven.azuredoom.com/mods")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "LowTalkHarness"

// Build LowTalk from the sibling checkout, so the harness always tests the LowTalk in this tree.
includeBuild("../lowtalk")
