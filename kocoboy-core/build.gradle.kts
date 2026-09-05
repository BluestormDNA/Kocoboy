import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

tasks.withType<Test> {
    testLogging {
        showStandardStreams = true
    }
}

kotlin {
    jvm()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.library()
    }
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        freeCompilerArgs.add("-Xwarning-level=NOTHING_TO_INLINE:disabled")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines)
            implementation(libs.kotlinx.datetime)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines)
        }
    }
}
