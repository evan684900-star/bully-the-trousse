// Module Android (Jetpack Compose) qui affiche le jeu et consomme la
// logique pure Kotlin de ":core" (physique, économie). Nécessite le SDK
// Android pour compiler : ouvre ce dossier "android/" dans Android Studio,
// qui installera ce qu'il faut et proposera de décommenter
// include(":app") dans settings.gradle.kts automatiquement.
plugins {
    // Aucune version ici : toutes sont déclarées une seule fois dans le
    // build.gradle.kts racine (apply false). C'est indispensable pour AGP,
    // pas seulement une question de style — voir l'explication sur les
    // classloaders parent/enfant dans ce fichier racine.
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Le plugin Firebase n'est PAS listé ici : voir juste en dessous.
}

// Firebase (compte, sauvegarde cloud, classement — voir FirebaseBridge.kt).
//
// Le plugin google-services lit app/google-services.json, un fichier qui vient
// de la console Firebase et qu'on ne peut pas versionner à la place de
// quelqu'un. L'appliquer sans ce fichier fait échouer TOUT le build, y compris
// pour quelqu'un qui veut juste jouer hors ligne — d'où cette application
// conditionnelle plutôt qu'une ligne à décommenter à la main :
//
//   - fichier absent  -> le plugin est ignoré, l'app compile et tourne, et
//                        FirebaseBridge.isAvailable vaut false (tout le volet
//                        en ligne est simplement coupé) ;
//   - fichier présent -> le plugin s'applique, Firebase s'initialise tout seul
//                        au démarrage, et le compte/classement s'activent.
//
// Voir android/README.md pour la marche à suivre dans la console.
if (project.file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.bullythetrousse.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bullythetrousse.app"
        minSdk = 26 // Android 8.0+ : couvre la grande majorité des appareils actifs
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        // Compilateur Compose "classique" (Kotlin < 2.0 n'a pas le plugin
        // org.jetbrains.kotlin.plugin.compose intégré) : version alignée sur
        // Kotlin 1.9.24 (voir build.gradle.kts racine).
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core"))

    // BOM alignée sur le compilateur Compose 1.5.14 ci-dessus (une BOM plus
    // récente demanderait un compilateur Compose plus récent, qui lui-même
    // demanderait Kotlin 2.0+ — voir la note sur Kotlin 1.9.24 plus haut).
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")

    // Firebase (voir FirebaseBridge.kt et android/README.md). Ces dépendances
    // se résolvent sans le plugin google-services (elles viennent de google(),
    // déjà déclaré dans settings.gradle.kts). Sans google-services.json,
    // Firebase ne s'initialise pas : FirebaseBridge.isAvailable vaut false et
    // aucun appel réseau n'est tenté.
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
