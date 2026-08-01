pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.4.10"
        id("org.jetbrains.intellij.platform") version "2.18.1"
        id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
        id("io.gitlab.arturbosch.detekt") version "1.23.8"
    }
}

rootProject.name = "taiga-design-tokens-plugin"
