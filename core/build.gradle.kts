import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Cœur du jeu en Kotlin/JVM pur : règles, monde, cerveau, physique, ports, fonctionnalités (sans dessin),
// espèces (sans rendu). Aucune classe Android n'est visible ici : le compilateur garantit que le cœur
// n'en dépend pas (les tests JVM ne passent plus par les stubs `isReturnDefaultValues` de l'app).
plugins {
    id("org.jetbrains.kotlin.jvm")
    jacoco
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

jacoco {
    // Même version que celle de l'app (`android.testCoverage.jacocoVersion`) : lit les classes des JDK récents.
    toolVersion = "0.8.13"
}

tasks.test {
    useJUnit()
}

// Couverture : ./gradlew :core:jacocoTestReport → core/build/reports/jacoco/test/ (html/ + jacocoTestReport.xml).
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

// Aucune dépendance runtime (hors bibliothèque standard Kotlin, ajoutée par le plugin).
dependencies {
    testImplementation("junit:junit:4.13.2")
}
