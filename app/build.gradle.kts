plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Version : passée par la CI (-PversionName=1.2.0 -PversionCode=42), sinon valeurs de dev.
val appVersionName = (project.findProperty("versionName") as String?) ?: "1.0"
val appVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

// Signature release : fournie par la CI via variables d'environnement (secrets GitHub).
val keystorePath: String? = System.getenv("TAMACOOKIE_KEYSTORE")
val hasReleaseKey = keystorePath != null && file(keystorePath).exists()

android {
    namespace = "com.tamacookie"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tamacookie"
        minSdk = 33          // Android 13
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("TAMACOOKIE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TAMACOOKIE_KEY_ALIAS")
                keyPassword = System.getenv("TAMACOOKIE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        // Les classes Android (stubs) renvoient des valeurs par défaut au lieu de lever "Stub!"
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // Les avertissements existants sont figés dans la baseline ; tout nouveau problème bloque.
        baseline = file("lint-baseline.xml")
        abortOnError = true
    }
}

// Aucune dépendance AndroidX : uniquement le framework Android + Kotlin stdlib.
dependencies {
    testImplementation("junit:junit:4.13.2")
}
