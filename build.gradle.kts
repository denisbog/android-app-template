// Versions verified together on this machine (Gradle 8.13): AGP 8.13.0, Kotlin 2.2.20,
// Compose BOM 2025.09.00. Bump them together — the Compose compiler ships with Kotlin now,
// so `org.jetbrains.kotlin.plugin.compose` must match the Kotlin version exactly.
plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
}
