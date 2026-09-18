pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Corrige l'avertissement "Undefined Toolchain Download Repositories" :
    // sans ce plugin, Gradle ne sait pas où télécharger un JDK si celui
    // requis par le projet (17) n'est pas déjà installé localement.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "bully-the-trousse"

// ":core" est du Kotlin pur (physique, économie, sauvegarde) : aucune
// dépendance Android, donc compilable et testable avec juste Gradle+JDK,
// même sans le SDK Android installé (voir core/README.md).
include(":core")

// ":app" est le module Android (Jetpack Compose) qui utilise ":core". Ses
// fichiers existent déjà sur le disque (voir app/), mais son inclusion ici
// est volontairement en commentaire : résoudre ses plugins (Android Gradle
// Plugin, dépôt Google) demande un accès réseau que ce bac à sable
// bloque, et l'inclure quand même ferait échouer "gradle :core:test" au
// moment de configurer TOUS les modules du projet. Décommente cette ligne
// en ouvrant le projet dans Android Studio (voir README.md à la racine).
// include(":app")
