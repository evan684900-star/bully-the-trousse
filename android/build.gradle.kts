// Build racine : ne déclare QUE le plugin utilisé par ":core" (Kotlin pur).
// Les plugins Android (nécessitant le SDK Android + le dépôt Google, tous
// deux absents de ce bac à sable) sont déclarés directement dans
// app/build.gradle.kts, pour que "gradle :core:test" reste utilisable ici
// sans jamais tenter de les résoudre.
plugins {
    kotlin("jvm") version "2.0.21" apply false
}
