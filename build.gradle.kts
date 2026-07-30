import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    java
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    jvmToolchain(21)
}

val localIdePath = providers.gradleProperty("localIdePath").orNull

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        if (localIdePath == null) {
            webstorm(providers.gradleProperty("platformVersion"))
            testFramework(TestFrameworkType.Platform)
        } else {
            local(localIdePath)
            testFramework(TestFrameworkType.Platform.Bundled)
        }

        bundledPlugin("JavaScript")
        bundledPlugin("com.intellij.css")
    }
}

intellijPlatform {
    instrumentCode.set(
        providers.gradleProperty("skipInstrumentation")
            .map { !it.toBoolean() }
            .orElse(true),
    )

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253"
            untilBuild = provider { null }
        }
    }
}
