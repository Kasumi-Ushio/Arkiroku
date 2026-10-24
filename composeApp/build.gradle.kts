import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.aboutlibraries)
}

// Baked into a generated iosMain source at build time so the About screen
// shows the real compile timestamp instead of relying on the bundle
// executable's mtime, which re-signing and installation refresh. The millis
// are formatted on device; the Android target uses the same generated-source
// approach via generateAndroidBuildTime below.
val generateIosBuildTime = tasks.register("generateIosBuildTime") {
    val outputDir = layout.buildDirectory.dir("generated/sources/iosBuildTime")
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile
        dir.mkdirs()
        File(dir, "IosBuildTime.kt").writeText(
            "package org.kasumi321.ushio.phitracker.data.platform\n\n" +
                "internal const val IOS_BUILD_TIME_MILLIS: Long = ${System.currentTimeMillis()}L\n"
        )
    }
}

// Android counterpart of the iOS build-time source above. Lives in this
// module because the library cannot read the app module's BuildConfig.
val generateAndroidBuildTime = tasks.register("generateAndroidBuildTime") {
    val outputDir = layout.buildDirectory.dir("generated/sources/androidBuildTime")
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile
        dir.mkdirs()
        File(dir, "AndroidBuildTime.kt").writeText(
            "package org.kasumi321.ushio.phitracker.data.platform\n\n" +
                "internal const val ANDROID_BUILD_TIME_MILLIS: Long = ${System.currentTimeMillis()}L\n"
        )
    }
}

kotlin {
    // expect/actual classes are still Beta (KT-61573); the flag silences the
    // diagnostic for the expect objects and KSP-generated actuals.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "org.kasumi321.ushio.phitracker.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        androidResources {
            enable = true
        }

        // Host-side (JVM) tests; Robolectric needs Android resources on the
        // test classpath. Source set/dir is androidHostTest under this plugin.
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            // Size-optimized release binaries (-Oz); no effect on debug builds.
            binaryOption("smallBinary", "true")
        }
    }

    sourceSets {
        androidMain {
            kotlin.srcDir(generateAndroidBuildTime)
            dependencies {
                implementation(libs.compose.uiToolingPreview)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.security.crypto)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.coil3.core)
            }
        }
        named("androidHostTest") {
            dependencies {
                implementation("androidx.test:core:1.7.0")
                implementation("org.robolectric:robolectric:4.17")
            }
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material3.adaptive)
            implementation(libs.material.kolor)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kaml)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.qrose)
            implementation(libs.kmp.zip)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.coil3.compose)
            implementation(libs.coil3.network.ktor3)
            implementation(libs.okio)
            implementation(libs.kermit)
            implementation(libs.kermit.io)
            implementation(libs.aboutlibraries.compose.m3)
            implementation(libs.multiplatform.markdown.renderer.m3)
            implementation(libs.haze)
            implementation(libs.haze.blur)
        }
        iosMain {
            kotlin.srcDir(generateIosBuildTime)
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.koin.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

aboutLibraries {
    collect {
        // Custom attributions (e.g. the bundled Roboto font) live in
        // config/libraries/*.json
        configPath = file("config")
    }
    export {
        outputFile = file("src/commonMain/composeResources/files/aboutlibraries.json")
    }
}

// The plugin also wires variant-scoped exports into generated platform res
// (build/generated/aboutLibraries/<variant>/res/raw/aboutlibraries.json).
// The About screen parses the Compose-resources copy via CMP Res instead,
// so those duplicates never ship: keep only the root export task.
tasks.matching {
    it.name.startsWith("prepareLibraryDefinitions") ||
        (it.name.startsWith("exportLibraryDefinitions") && it.name != "exportLibraryDefinitions")
}.configureEach {
    enabled = false
}

// Compose resources copy tasks for commonMain consume the export output file.
// Declare mustRunAfter so combined invocations like
//   exportLibraryDefinitions + assembleDebug
// do not trigger "uses this output without declaring an explicit or implicit dependency".
tasks.matching { it.name.startsWith("copy") && it.name.endsWith("ForCommonMain") }.configureEach {
    mustRunAfter(tasks.named("exportLibraryDefinitions"))
}

tasks.withType<Test>().configureEach {
    systemProperty("phitracker.projectDir", rootProject.projectDir.absolutePath)
    // Robolectric SDK 37 instrumentation reflects into JRE internals
    // (robolectric#11434); export the package to the unnamed module.
    jvmArgs("--add-exports", "java.base/jdk.internal.access=ALL-UNNAMED")
}

dependencies {
    // The Android-KMP library plugin has no build variants; uiTooling goes to
    // the shared Android runtime classpath instead of debugImplementation.
    androidRuntimeClasspath(libs.compose.uiTooling)
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
