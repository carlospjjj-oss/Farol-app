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
        versionCode = 29
        versionName = "4.2"
    }

    // Chave FIXA: sem ela, cada build do GitHub assinaria com uma chave nova e o Android
    // recusaria instalar a atualização por cima ("App não instalado").
    signingConfigs {
        create("farol") {
            storeFile = file("farol.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "oD1blIXG1_JUQjmumWXzcCE8"
            keyAlias = "farol"
            keyPassword = System.getenv("KEYSTORE_PASSWORD") ?: "oD1blIXG1_JUQjmumWXzcCE8"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("farol")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("farol")
        }
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
