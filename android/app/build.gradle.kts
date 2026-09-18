// Module Android (Jetpack Compose) qui affiche le jeu et consomme la
// logique pure Kotlin de ":core" (physique, économie). Nécessite le SDK
// Android pour compiler : ouvre ce dossier "android/" dans Android Studio,
// qui installera ce qu'il faut et proposera de décommenter
// include(":app") dans settings.gradle.kts automatiquement.
plugins {
    id("com.android.application") version "8.5.2"
    id("org.jetbrains.kotlin.android") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
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

    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
}
