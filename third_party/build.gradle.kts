import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

// Specify UTF-8 for all compilations so we avoid Windows-1252.
allprojects {
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
    tasks.withType<Test> {
        systemProperty("file.encoding", "UTF-8")
    }
}

// Plugins - must be first
plugins {
    id("java") // Java support
    id("org.jetbrains.kotlin.jvm") version "2.1.21-RC2" // Kotlin support
    id("org.jetbrains.intellij.platform") version "2.6.0" // IntelliJ Platform Gradle Plugin
}

// Configure project's dependencies
repositories {
    mavenCentral()
    // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
    intellijPlatform {
        defaultRepositories()
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

sourceSets {
    main {
        java {
            srcDir("gen")
            srcDir("thirdPartySrc/analysisServer")
            srcDir("thirdPartySrc/vmServiceDrivers")
        }
    }
}

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(21)
}
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3.1")
        testFramework(TestFrameworkType.Platform)
        bundledModule("intellij.platform.coverage")
    }
    implementation(fileTree("lib") { include("*.jar") })
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
