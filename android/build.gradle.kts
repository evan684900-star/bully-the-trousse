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
// Kotlin 1.9.24 (pas 2.0.x) : Kotlin 2.0.21 + AGP 8.5.2 provoque un
// NoClassDefFoundError reproductible sur com.android.build.gradle.api.BaseVariant
// en appliquant org.jetbrains.kotlin.android (l'API de variantes historique
// que le plugin Kotlin/Android inspecte à l'application, incompatible avec
// cette combinaison précise de versions). 1.9.24 est la dernière version 1.9,
// documentée comme compatible avec AGP 8.5.2. Comme le plugin Compose
// intégré à Kotlin (org.jetbrains.kotlin.plugin.compose) n'existe qu'à partir
// de Kotlin 2.0, Compose est configuré à l'ancienne dans app/build.gradle.kts
// via composeOptions.kotlinCompilerExtensionVersion.
plugins {
    kotlin("jvm") version "1.9.24" apply false
    kotlin("android") version "1.9.24" apply false
    kotlin("plugin.serialization") version "1.9.24" apply false
}
