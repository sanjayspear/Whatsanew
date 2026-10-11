plugins {
    id("pulse.kmp.compose")
}

kotlin {
    // iOS app links one static framework containing core + ui.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "PulseKit"
            isStatic = true
            export(project(":shared:core"))
            linkerOpts("-lsqlite3")
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core"))
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            implementation(libs.haze)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
        }
        commonTest.dependencies {
            implementation(libs.compose.ui.test)
        }
    }
}
