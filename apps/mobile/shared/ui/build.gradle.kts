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
            implementation(libs.compose.ui.backhandler)
            implementation(libs.compose.components.resources)
            implementation(libs.haze)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
        }
        commonTest.dependencies {
            implementation(libs.compose.ui.test)
        }
        getByName("desktopTest").dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

// Bundled resources (offline OpenStreetMap region packs) live in src/commonMain/composeResources.
compose.resources {
    publicResClass = false
    packageOfResClass = "dev.pulse.ui.res"
    generateResClass = always
}
