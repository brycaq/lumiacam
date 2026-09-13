// Top-level build file
plugins {
    id("com.android.application") version "8.5.0" apply false
    // Kotlin 2.0+ is required for the org.jetbrains.kotlin.plugin.compose Gradle plugin used
    // below (pre-2.0 projects configure the Compose compiler via composeOptions instead).
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
