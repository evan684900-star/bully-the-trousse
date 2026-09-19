// Build racine : déclare TOUS les plugins du projet (Android ET Kotlin) avec
// leur version, en "apply false" — chaque module les applique ensuite sans
// redonner de version (voir app/build.gradle.kts et core/build.gradle.kts).
//
// Pourquoi com.android.application doit être ici et pas seulement dans
// app/build.gradle.kts : Gradle isole les plugins par classloader, et le
// classloader d'un sous-projet est un ENFANT de celui de la racine. Un
// classloader enfant voit son parent, mais jamais l'inverse. Tant qu'AGP
// n'était déclaré que dans app/, il atterrissait dans le classloader enfant
// alors que le plugin Kotlin venait du parent : en appliquant
// org.jetbrains.kotlin.android, le plugin Kotlin (parent) n'arrivait pas à
// charger com.android.build.gradle.api.BaseVariant (enfant), d'où un
// ClassNotFoundException systématique, indépendant des versions utilisées
// (reproduit avec Gradle 8.9/8.7, Kotlin 2.0.21/1.9.24, AGP 8.5.2/8.4.1).
// Les déclarer tous les deux ici les met dans le même classloader.
//
// Conséquence : évaluer ce fichier résout AGP, donc même "gradle :core:test"
// a désormais besoin d'un accès au dépôt Google (dl.google.com).
//
// Kotlin 1.9.24 (pas 2.0.x) : le plugin Compose intégré à Kotlin
// (org.jetbrains.kotlin.plugin.compose) n'existe qu'à partir de Kotlin 2.0,
// donc Compose est configuré à l'ancienne dans app/build.gradle.kts via
// composeOptions.kotlinCompilerExtensionVersion.
plugins {
    id("com.android.application") version "8.4.1" apply false
    kotlin("jvm") version "1.9.24" apply false
    kotlin("android") version "1.9.24" apply false
    kotlin("plugin.serialization") version "1.9.24" apply false
}
