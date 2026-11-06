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
        // Le démon tourne en JDK 21 (gradle/gradle-daemon-jvm.properties) : compiler contre l'API du JDK 17, sinon
        // un appel comme `list.removeFirst()` se lierait à la méthode du JDK 21, absente d'Android 13/14 (l'APK
        // embarque le cœur). L'app compile contre android.jar (AGP) : rien à faire.
        freeCompilerArgs.add("-Xjdk-release=17")
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
