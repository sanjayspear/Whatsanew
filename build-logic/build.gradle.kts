plugins {
    `kotlin-dsl`
}

group = "dev.pulse.buildlogic"



dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpPure") {
            id = "pulse.kmp.pure"
            implementationClass = "dev.pulse.buildlogic.KmpPureConventionPlugin"
        }
        register("kmpLibrary") {
            id = "pulse.kmp.library"
            implementationClass = "dev.pulse.buildlogic.KmpLibraryConventionPlugin"
        }
        register("kmpCompose") {
            id = "pulse.kmp.compose"
            implementationClass = "dev.pulse.buildlogic.KmpComposeConventionPlugin"
        }
        register("springService") {
            id = "pulse.spring.service"
            implementationClass = "dev.pulse.buildlogic.SpringServiceConventionPlugin"
        }
    }
}
