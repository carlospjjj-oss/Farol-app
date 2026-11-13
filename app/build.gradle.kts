plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.farol.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.farol.app"
        minSdk = 26          // Android 8.0
        targetSdk = 35
        versionCode = 34
        versionName = "5.2"
    }

    // Assinatura: feita no GitHub Actions (.github/scripts/sign.sh) com rotação de chave.
    // Nenhuma chave nem senha fica no repositório: vêm dos Secrets do GitHub.
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // OCR on-device (modelo latino embutido, funciona offline). Usado só como plano B.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.annotation:annotation:1.9.1")

    testImplementation("junit:junit:4.13.2")
}
