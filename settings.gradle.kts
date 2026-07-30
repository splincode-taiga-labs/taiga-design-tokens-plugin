pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.1.20"
        id("org.jetbrains.intellij.platform") version "2.18.1"
    }
}

rootProject.name = "taiga-design-tokens-plugin"
