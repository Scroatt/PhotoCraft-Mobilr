import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ai.storyteller.photocraft.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "ai.storyteller.photocraft.android"
        // Android 10: MediaStore.Downloads (usado para salvar exportações) e WebView moderno.
        minSdk = 29
        targetSdk = 35
        // Mesma versão do bundle web (scripts/fetch-web.sh).
        versionCode = 1
        versionName = "0.5.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// O bundle web não vai para o Git: sem ele o app abriria uma tela em branco.
val checkWebBundle by tasks.registering {
    val index = file("src/main/assets/web/index.html")
    doFirst {
        if (!index.exists()) {
            throw GradleException(
                "Bundle web ausente em app/src/main/assets/web. Rode scripts/fetch-web.sh na raiz do repositório."
            )
        }
    }
}

tasks.named("preBuild") {
    dependsOn(checkWebBundle)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.webkit:webkit:1.12.1")
}
