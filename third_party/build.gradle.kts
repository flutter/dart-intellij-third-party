import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

plugins {
    id("java") // Java support
    id("org.jetbrains.kotlin.jvm") version "2.1.21-RC2" // Kotlin support
    id("org.jetbrains.intellij.platform") version "2.5.0" // IntelliJ Platform Gradle Plugin
}

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(21)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// Configure project's dependencies
repositories {
    mavenCentral()

    // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3.1")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        version = "1"
        ideaVersion {
            sinceBuild = "253"
            untilBuild = "253.*"
        }
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
}

// https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-faq.html#how-to-check-the-latest-available-eap-release
tasks {
    printProductsReleases {
        channels = listOf(ProductRelease.Channel.EAP)
        types = listOf(IntelliJPlatformType.IntellijIdeaCommunity)
        untilBuild = provider { null }
        doLast {
            productsReleases.get().max()
        }
    }
}
