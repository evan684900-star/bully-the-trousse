// Module Kotlin pur (physique, économie, sauvegarde) : aucune dépendance
// Android, donc compilable et testable avec "gradle :core:test" même sans
// le SDK Android installé. C'est le module que Claude peut vérifier
// directement dans son bac à sable ; ":app" (Compose) doit être ouvert
// dans Android Studio pour être compilé.
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
}

// Pas de jvmToolchain() explicite : ce module n'a besoin d'aucune version
// précise, donc on laisse Gradle utiliser tel quel le JDK qui lance le
// build (celui d'Android Studio, ou celui du système). Fixer une version
// exacte forcerait Gradle à la retélécharger si elle diffère de celle
// disponible localement — inutile ici, et source d'erreurs "Toolchain
// download repositories have not been configured" si le réseau ou les
// dépôts de toolchain ne sont pas accessibles.

tasks.test {
    useJUnitPlatform()
}
