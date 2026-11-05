import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Version : passée par la CI (-PversionName=1.2.0 -PversionCode=42), sinon valeurs de dev.
val appVersionName = (project.findProperty("versionName") as String?) ?: "1.0"
val appVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

// Signature release (clé ECDSA P-256) : `keystore.properties` à la racine en local (ignoré
// par git), variables d'environnement TAMACOOKIE_* en CI (secrets GitHub). Sans clé, l'APK
// release sort NON signé — jamais signé avec la clé de debug, qui est publique de fait.
val keystoreProperties =
    Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }

fun signingValue(key: String, env: String): String? = keystoreProperties.getProperty(key) ?: System.getenv(env)

val keystorePath: String? = signingValue("storeFile", "TAMACOOKIE_KEYSTORE")
val keystorePassword: String? = signingValue("storePassword", "TAMACOOKIE_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = signingValue("keyAlias", "TAMACOOKIE_KEY_ALIAS")
val releaseKeyPassword: String? = signingValue("keyPassword", "TAMACOOKIE_KEY_PASSWORD")
val hasReleaseKey =
    keystorePath != null && file(keystorePath).exists() &&
        listOf(keystorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

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
                storePassword = keystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                // minSdk 33 : v1 (JAR) inutile ; v2 + v3/v3.1 (rotation de clé) + v4.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        abortOnError = false
    }
}

// Aucune dépendance AndroidX : uniquement le framework Android + Kotlin stdlib.
dependencies {
    testImplementation("junit:junit:4.13.2")
}
