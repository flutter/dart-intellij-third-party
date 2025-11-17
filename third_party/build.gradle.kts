import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

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
    id("org.jetbrains.kotlin.jvm") version "2.2.21" // Kotlin support
    id("org.jetbrains.intellij.platform") version "2.10.3" // IntelliJ Platform Gradle Plugin
    id("org.jetbrains.changelog") version "2.2.0" // Gradle Changelog Plugin
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
        name = providers.gradleProperty("pluginName")
        id = providers.gradleProperty("pluginId")
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
        changeNotes = provider {
            project.changelog.render(Changelog.OutputType.HTML)
        }
    }
    pluginVerification {
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_WARNINGS,
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            //            VerifyPluginTask.FailureLevel.DEPRECATED_API_USAGES,
            VerifyPluginTask.FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
            //            VerifyPluginTask.FailureLevel.EXPERIMENTAL_API_USAGES,
            //            VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
            VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
            VerifyPluginTask.FailureLevel.NON_EXTENDABLE_API_USAGES,
            VerifyPluginTask.FailureLevel.PLUGIN_STRUCTURE_WARNINGS,
            VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES,
            VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
            VerifyPluginTask.FailureLevel.NOT_DYNAMIC,
        )
        verificationReportsFormats = VerifyPluginTask.VerificationReportsFormats.ALL
        subsystemsToCheck = VerifyPluginTask.Subsystems.ALL
        ides {
            recommended()
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
        // intellijIdeaCommunity can be found here:
        // https://www.jetbrains.com/idea/download/other.html
        intellijIdeaCommunity("2025.1.1")

        testFramework(TestFrameworkType.Platform)

        // 1. Depend on the main Java plugin. This provides all Java-related features, including coverage.
        bundledPlugin("com.intellij.java")

        // 2. Depend on the main Kotlin plugin. This provides the Kotlin standard library and IDE support.
        bundledPlugin("org.jetbrains.kotlin")

        // 3. Add other necessary plugins and modules.
        bundledModule("intellij.platform.coverage")
        bundledModule("intellij.platform.coverage.agent")
        bundledPlugin("org.jetbrains.plugins.yaml")
        bundledPlugin("com.intellij.copyright")
    }

    implementation(fileTree("lib") { include("*.jar") })

    testImplementation("junit:junit:4.13.2")
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
    test {
        val dartSdkPath = System.getenv("DART_HOME")
        if (dartSdkPath != null) {
            val versionFile = file("${dartSdkPath}/version")
            if (versionFile.exists() && versionFile.isFile()) {
                jvmArgs("-Ddart.sdk=${dartSdkPath}")
            } else {
                logger.error("This directory, ${dartSdkPath}, doesn't appear to be Dart SDK path, " +
                        "no version file found at ${versionFile.absolutePath}")
            }
        } else {
            logger.error("DART_HOME environment variable is not set. Dart Analysis Server tests will fail.")
        }
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

// A task to print the classpath used for compiling an IntelliJ plugin
// Run with `./gradlew printCompileClasspath --no-configuration-cache`
tasks.register("printCompileClasspath") {
    doLast {
        println("--- Begin Compile Classpath ---")
        configurations.getByName("compileClasspath").forEach { file ->
            println(file.absolutePath)
        }
        println("--- End Compile Classpath ---")
    }
}
