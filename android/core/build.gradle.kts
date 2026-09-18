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

kotlin {
    // 21 pour coller au JDK réellement disponible ici ; l'app Android
    // (module ":app") ciblera son propre niveau via l'Android Gradle Plugin,
    // indépendamment de ce module.
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
