plugins {
    alias((libs.plugins.kotlinMultiplatform))
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":kocoboy-ui"))
            implementation(libs.compose.ui)
        }
    }
}
