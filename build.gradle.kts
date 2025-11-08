import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessPlugin

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.spotless)
}

allprojects {
    apply<SpotlessPlugin>()
    configure<SpotlessExtension> {
        kotlin {
            ktlint()
            target("**/*.kt")
            targetExclude("**/build/**")
            trimTrailingWhitespace()
            endWithNewline()
        }
        kotlinGradle {
            ktlint()
            target("**/*.gradle.kts")
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}
