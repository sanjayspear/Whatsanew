plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

subprojects {
    group = "dev.pulse.packages"
    version = "0.1.0"
}
