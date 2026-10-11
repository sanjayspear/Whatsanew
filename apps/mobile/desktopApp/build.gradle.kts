plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":shared:ui"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "dev.pulse.desktop.MainKt"
    }
}

// `./gradlew -p apps/mobile :desktopApp:screenshots -Ppulse.android=false` renders PNGs into docs/screenshots/
val screenshotDir = layout.projectDirectory.dir("../../../docs/screenshots").asFile.absolutePath
tasks.register<JavaExec>("screenshots") {
    group = "verification"
    description = "Renders real Pulse screens offscreen (headless) to PNG."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.pulse.desktop.ScreenshotsKt")
    args(screenshotDir)
    jvmArgs("-Djava.awt.headless=true")
}
