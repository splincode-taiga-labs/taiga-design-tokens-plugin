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
val localTestFrameworkJar = localIdePath?.let { idePath ->
    listOf(
        file("$idePath/Contents/lib/testFramework.jar"),
        file("$idePath/lib/testFramework.jar"),
    ).firstOrNull { it.isFile }
        ?: error("Could not find testFramework.jar inside local IDE: $idePath")
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    if (localTestFrameworkJar != null) {
        testImplementation(files(localTestFrameworkJar))
    }

    intellijPlatform {
        if (localIdePath == null) {
            webstorm(providers.gradleProperty("platformVersion"))
            testFramework(TestFrameworkType.Platform)
        } else {
            local(localIdePath)
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
