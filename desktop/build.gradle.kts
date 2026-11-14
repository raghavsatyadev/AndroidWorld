import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

/** ProGuard for `packageRelease*`; the Compose plugin's default (7.x) is older. */
val PROGUARD_VERSION = "7.10.0"

kotlin {
    jvmToolchain(21)
    compilerOptions.optIn.addAll(
        "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        "androidx.compose.material3.ExperimentalMaterial3Api",
    )
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":adapters-jvm"))
    implementation(project(":adapters-adb"))
    implementation(project(":adapters-emulator"))
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jcodec)
    implementation(libs.zxing.core)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "io.github.raghavsatyadev.androidworld.desktop.MainKt"
        // `packageReleaseMsi`: ProGuard drops unused classes (most of the extended icons). Shrink
        // only: obfuscation and optimization break the libraries that load classes by name.
        buildTypes.release.proguard {
            version.set(PROGUARD_VERSION)
            configurationFiles.from(project.file("proguard-rules.pro"))
            obfuscate.set(false)
            optimize.set(false)
        }
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "AndroidWorld"
            packageVersion = providers.gradleProperty("appVersion").get()
            vendor = "Raghav Satyadev"
            description = "Android device and emulator toolkit"
            // From ./gradlew :desktop:suggestRuntimeModules, plus jdk.crypto.ec for HTTPS.
            modules(
                "java.instrument", "java.net.http", "java.sql", "jdk.httpserver",
                "jdk.management", "jdk.unsupported", "jdk.crypto.ec",
            )
            linux { iconFile.set(project.file("icons/app.png")) }
            windows {
                menu = true
                menuGroup = "AndroidWorld"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                iconFile.set(project.file("icons/app.ico"))
                // Must never change: lets newer MSIs upgrade older installs in place.
                upgradeUuid = "442bd94c-5591-49e8-9783-c5ae2387fe20"
            }
        }
    }
}

/**
 * ProGuard cannot read the Kotlin 2.5 stdlib's metadata yet (proguard-core fails on a new
 * contract effect). It only reads metadata when `kotlin.Metadata` is kept, and Compose's default
 * rules keep all of `kotlin.**`, so the release build uses a copy of those rules without it. The
 * app does no full Kotlin reflection, so the stripped metadata is not missed.
 */
val composeRulesWithoutMetadata by tasks.registering {
    val unpacked = tasks.named("unpackDefaultComposeDesktopJvmApplicationResources").map {
        it.outputs.files.asFileTree.matching { include("**/default-compose-desktop-rules.pro") }
    }
    val out = layout.buildDirectory.file("compose/proguard/compose-rules.pro")
    inputs.files(unpacked)
    outputs.file(out)
    doLast {
        val original = unpacked.get().singleFile
        val text = original.readText()
        val keepKotlin = "-keep class kotlin.** { *; }"
        check(keepKotlin in text) { "Compose's ProGuard rules changed: recheck $original" }
        out.get().asFile.writeText(text.replace(keepKotlin, "-keep class !kotlin.Metadata,kotlin.** { *; }"))
    }
}

// After the Compose plugin, which sets its own rules file in afterEvaluate.
afterEvaluate {
    tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractProguardTask>().configureEach {
        defaultComposeRulesFile.set(composeRulesWithoutMetadata.map { RegularFile { it.outputs.files.singleFile } })
    }
}
