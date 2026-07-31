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
    testRuntimeOnly(kotlin("stdlib"))

    intellijPlatform {
        if (localIdePath == null) {
            webstorm(providers.gradleProperty("platformVersion"))
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

val integrationTestSourceSet = sourceSets.create("integrationTest")

configurations[integrationTestSourceSet.implementationConfigurationName].extendsFrom(
    configurations.testImplementation.get(),
)
configurations[integrationTestSourceSet.runtimeOnlyConfigurationName].extendsFrom(
    configurations.testRuntimeOnly.get(),
)

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests against packages installed from npm."
    group = "verification"
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    shouldRunAfter(tasks.test)
}
