import com.google.protobuf.gradle.id
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// JVM-only adapter implementing the :domain emulator-screen ports over the emulator's gRPC API;
// proto is Apache-2.0 from the Android SDK (emulator/lib).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.protobuf)
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.grpc.netty.shaded)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    implementation(libs.protobuf.java)
    compileOnly(libs.javax.annotation.api)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.asProvider().get()}" }
    plugins { create("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}" } }
    generateProtoTasks { all().configureEach { plugins { create("grpc") } } }
}

configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.google.protobuf" && requested.name == "protobuf-java") {
            useVersion(libs.versions.protobuf.asProvider().get())
        }
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()

    System.getProperty("androidWorld.emulatorSmoke")?.let {
        systemProperty("androidWorld.emulatorSmoke", it)
    }
    System.getProperty("androidWorld.emulatorSmokeRotate")?.let {
        systemProperty("androidWorld.emulatorSmokeRotate", it)
    }
}
