// Build racine : centralise la VERSION de tous les plugins Kotlin utilisés
// par les deux modules (":core" et ":app"), tous en "apply false" — chaque
// module les applique ensuite sans redonner de version (voir
// app/build.gradle.kts et core/build.gradle.kts). Déclarer une version à
// deux endroits différents pour un même plugin provoque des erreurs comme
// "plugin already on the classpath with an unknown version".
//
// com.android.application (le plugin Android lui-même, qui a besoin du SDK
// Android + du dépôt Google, absents de ce bac à sable) reste déclaré
// directement dans app/build.gradle.kts, pour que "gradle :core:test" reste
// utilisable ici sans jamais tenter de le résoudre. Les plugins Kotlin
// ci-dessous se résolvent via Maven Central / le Gradle Plugin Portal, pas
// besoin du dépôt Google, donc pas de souci pour les garder ici.
plugins {
    kotlin("jvm") version "2.0.21" apply false
    kotlin("android") version "2.0.21" apply false
    kotlin("plugin.serialization") version "2.0.21" apply false
    kotlin("plugin.compose") version "2.0.21" apply false
}
