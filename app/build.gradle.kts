plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pochi.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pochi.assistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 12
        versionName = "5.7.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    signingConfigs {
        if (rootProject.file("pochi-release.jks").exists()) {
            create("release") {
                storeFile = rootProject.file("pochi-release.jks")
                storePassword = "pochi-release"
                keyAlias = "pochi"
                keyPassword = "pochi-release"
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
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
    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.foundation:foundation:1.6.8")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation("androidx.compose.material:material-icons-extended:1.6.8")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.2")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.appcompat:appcompat:1.7.0")

    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
}
