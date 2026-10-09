import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Assinatura de release: só ativa quando o keystore é informado (CI ou máquina de publicação).
// Sem ela, o build de release sai sem assinatura e não pode ser instalado.
val releaseKeystore: String? = System.getenv("ANDROID_KEYSTORE_FILE")

android {
    namespace = "ai.storyteller.photocraft.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "ai.storyteller.photocraft.android"
        // Android 10: MediaStore.Downloads (usado para salvar exportações) e WebView moderno.
        minSdk = 29
        targetSdk = 35
        // Na publicação, o CI passa a versão da tag (android-v<versão>) e um código sempre crescente.
        versionCode = System.getenv("ANDROID_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("ANDROID_VERSION_NAME") ?: "0.5.0"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        buildConfig = true
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
