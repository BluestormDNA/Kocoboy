import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":kocoboy-ui"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "io.github.bluestormdna.kocoboy.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            macOS { packageName = "kocoboy-macos" }
            windows { packageName = "kocoboy-windows" }
            linux { packageName = "kocoboy-linux" }
        }

        buildTypes.release.proguard {
            version.set("7.9.0")
            optimize.set(true)
            obfuscate.set(false)
        }
    }
}
