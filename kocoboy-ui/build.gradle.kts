import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    android {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        namespace = "io.github.bluestormdna.kocoboy.ui"
        compileSdk = 37
        minSdk = 26
        androidResources.enable = true
    }
    jvm()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.library()
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Kocoboy"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":kocoboy-core"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.resources)
            implementation(libs.compose.preview)
            implementation(libs.filekit.compose)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.compose.material.icons.core)
        }
    }
}

compose.resources {
    packageOfResClass = "kocoboy.ui.resources"
}

dependencies {
    androidRuntimeClasspath(libs.compose.tooling)
}
