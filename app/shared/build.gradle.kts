import org.jetbrains.compose.desktop.application.dsl.TargetFormat
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
    // Music Assistant server (its own WS API + long-lived token, distinct from HA). ma.url is
    // optional — MaConfig derives `ws://<ha-host>:8095/ws` from ha.url when it is blank.
    val maUrl = localProps.getProperty("ma.url").orEmpty()
    val maToken = localProps.getProperty("ma.token").orEmpty()
    // Declare the values as inputs so the task re-runs (and stays cacheable) when they change.
    inputs.property("haUrl", haUrl)
    inputs.property("haToken", haToken)
    inputs.property("maUrl", maUrl)
    inputs.property("maToken", maToken)

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
                const val MA_URL: String = "${maUrl.kt()}"
                const val MA_TOKEN: String = "${maToken.kt()}"
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

            // Album/browse artwork loading (multiplatform), fetching over the shared Ktor engines.
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)

            // Long-press drag-to-reorder for the up-next queue (multiplatform Compose).
            implementation(libs.reorderable)
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

composeCompiler {
    // Objective stability proof: the emitted report/metrics show which state types are stable and
    // which composables actually skip. Build-only — no runtime effect. (See the phone-performance
    // plan: Step 0 establishes the baseline, Step 1's verification diffs the report.)
    reportsDestination = layout.buildDirectory.dir("compose-reports")
    metricsDestination = layout.buildDirectory.dir("compose-metrics")
    // Types we don't own but pass as composable params (kotlinx-datetime, stdlib collections) are
    // declared stable here — see compose-stability.conf.
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose-stability.conf"))
}

// Desktop (JVM) entry point. `./gradlew :shared:run` launches the dashboard in a resizable window;
// `:shared:createDistributable` builds a self-contained app image under build/compose/binaries, and
// `:shared:packageDistributionForCurrentOS` wraps that into the host's installer format (.rpm here).
compose.desktop {
    application {
        mainClass = "com.mattschoe.smarthome.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "SmartHome"
            packageVersion = "1.0.0"
            description = "Smart Home Dashboard"
            vendor = "mattschoe"

            // Trim the bundled runtime to what the app actually loads. Skia/Compose needs desktop
            // AWT, Ktor needs the HTTP/crypto stack; the rest of the JDK can stay out of the image.
            modules(
                "java.instrument",   // Coil/Ktor agents-capable libraries probe for it at startup
                "java.management",   // coroutines' debug/JMX probes
                "java.naming",       // OkHttp DNS
                "jdk.crypto.ec",     // TLS to Home Assistant over wss/https
                "jdk.unsupported",   // sun.misc.Unsafe, still used by Skiko
            )

            linux {
                iconFile.set(project.file("icons/smarthome.png"))
                shortcut = true          // writes the .desktop entry, so it shows up in the launcher
                menuGroup = "Utility"
                appCategory = "Utility"
                packageName = "smarthome"
            }
            // No macOS/Windows iconFile: jpackage wants .icns / .ico there, and only icons/smarthome.png
            // exists. Both hosts fall back to the Compose default icon until someone builds on them.
            macOS {
                bundleID = "com.mattschoe.smarthome"
            }
            windows {
                menuGroup = "SmartHome"
                shortcut = true
                // Stable UUID so an upgrade replaces the install instead of stacking beside it.
                upgradeUuid = "6f1f7d9c-4a1e-4a5f-9a2a-2f0c1b7a5e31"
            }
        }
    }
}
