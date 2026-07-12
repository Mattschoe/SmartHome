import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlin.serialization)
}

// Home Assistant secrets live in the repo-root local.properties (git-ignored) and are code-generated
// into a commonMain object so every target — Android, iOS, and Desktop — reads them from one source.
// (AGP's BuildConfig is Android-only, which is why the secrets can't come from there for iOS/Desktop.)
// Absent keys resolve to empty strings, so a machine without local.properties (e.g. CI) falls back to
// MockAdapter without failing the build. See com.mattschoe.smarthome.haConfigFromSecrets().
val generateBuildSecrets by tasks.registering {
    val localProps = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    val haUrl = localProps.getProperty("ha.url").orEmpty()
    val haToken = localProps.getProperty("ha.token").orEmpty()
    // Declare the values as inputs so the task re-runs (and stays cacheable) when they change.
    inputs.property("haUrl", haUrl)
    inputs.property("haToken", haToken)

    val outputDir = layout.buildDirectory.dir("generated/buildSecrets/kotlin")
    outputs.dir(outputDir)

    doLast {
        fun String.kt() = replace("\\", "\\\\").replace("\"", "\\\"")
        val pkgDir = outputDir.get().dir("com/mattschoe/smarthome").asFile
        pkgDir.mkdirs()
        pkgDir.resolve("BuildSecrets.kt").writeText(
            """
            package com.mattschoe.smarthome

            /** Generated from repo-root local.properties by the :shared `generateBuildSecrets` task. Do not edit. */
            internal object BuildSecrets {
                const val HA_URL: String = "${haUrl.kt()}"
                const val HA_TOKEN: String = "${haToken.kt()}"
            }

            """.trimIndent()
        )
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    jvm("desktop")

    androidLibrary {
       namespace = "com.mattschoe.smarthome.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            // Ktor engine for Android (JVM/OkHttp).
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            // Ktor engine for iOS (Darwin/NSURLSession).
            implementation(libs.ktor.client.darwin)
        }
        getByName("desktopMain").dependencies {
            implementation(compose.desktop.currentOs)
            // Provides Dispatchers.Main on the JVM — required by lifecycle's
            // collectAsStateWithLifecycle. Version pinned to the catalog's coroutines version.
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:${libs.versions.kotlinxCoroutines.get()}")
            // Ktor engine for Desktop (JVM/OkHttp), so a live HA session works there too.
            implementation(libs.ktor.client.okhttp)
        }
        commonMain {
            // Generated BuildSecrets.kt (see generateBuildSecrets above). Passing the task provider
            // wires the compile-task dependency automatically.
            kotlin.srcDir(generateBuildSecrets)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            //Architecture + Navigation
            implementation(libs.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)

            // Home Assistant transport (WebSocket client, shared across platforms).
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

// Desktop (JVM) entry point. `./gradlew :shared:run` launches the dashboard in a resizable window.
compose.desktop {
    application {
        mainClass = "com.mattschoe.smarthome.MainKt"
        nativeDistributions {
            packageName = "SmartHome"
            packageVersion = "1.0.0"
        }
    }
}
