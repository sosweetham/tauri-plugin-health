plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.tauri.health"
    compileSdk = 34

    defaultConfig {
        // Health Connect (connect-client) has a minSdk-26 floor. Consumer
        // apps must set bundle.android.minSdkVersion = 26 in tauri.conf.json
        // (Tauri's default is 24).
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":tauri-android"))
    // Health Connect — Google Fit's replacement; built into Android 14+.
    implementation("androidx.health.connect:connect-client:1.1.0")
    // All connect-client APIs are suspend functions.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Annotations only (@Serializable/@SerialName on the typeshare-generated
    // types) — no kotlinx runtime or compiler plugin; WireJson serializes
    // with Jackson, matching Tauri's bridge. Version matches the
    // jackson-databind tauri-android ships.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")

    testImplementation("junit:junit:4.13.2")
}
