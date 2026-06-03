import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.compose")
}

// --- CITIM CHEIA DIN LOCAL.PROPERTIES ---
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}
val geminiApiKey: String = localProperties.getProperty("GEMINI_API_KEY") ?: ""
// ----------------------------------------

android {
    namespace = "com.example.f1predictor"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.f1predictor"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
        viewBinding = true
        buildConfig = true
        compose = true
    }
}

dependencies {
    // 1. Forțăm Core KTX la versiunea stabilă
    implementation("androidx.core:core-ktx") {
        version { strictly("1.13.1") }
    }

    // 2. Forțăm Activity KTX să NU mai facă update la 1.12.4
    implementation("androidx.activity:activity-ktx") {
        version { strictly("1.9.0") }
    }

    // 3. Forțăm Activity simplu
    implementation("androidx.activity:activity") {
        version { strictly("1.9.0") }
    }

    // 4. Restul dependențelor necesare pentru XML Views
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // --- CONECTARE LA SERVERUL LOCAL (F1 Hub) ---
    // Retrofit pentru apeluri API către laptopul tău
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    // Coroutines pentru rulare pe fundal
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // --- FIREBASE ---
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    // ADĂUGAT: Avem nevoie de Firestore pentru clasamentul din mini-joc!
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // --- JETPACK COMPOSE (ADĂUGATE PENTRU MINI-JOC) ---
    val composeBom = platform("androidx.compose:compose-bom:2024.04.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Testare
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Google Gemini AI (Păstrat pentru fallback)
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
}