// Umbrella composite: open the repo root in IntelliJ/Android Studio to get every
// build in one window. Each child build is also fully standalone, e.g.
//   ./gradlew -p services build
//   ./gradlew -p apps/mobile :shared:core:desktopTest
rootProject.name = "pulse"

includeBuild("build-logic")
includeBuild("packages")
includeBuild("services")
includeBuild("apps/mobile")
