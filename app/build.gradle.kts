plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)   // NUEVO: habilita el procesador de Room
}

android {
    namespace = "com.ejemplo.perlaorder"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.ejemplo.perlaorder"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // --- Room: base de datos local SQLite (carta + pedido en curso) ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)          // soporte de corrutinas y Flow
    ksp(libs.androidx.room.compiler)                // genera el código de los DAO

    // --- Retrofit + Gson: envío de la comanda a la API REST local (Ktor) ---
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)    // objeto Kotlin <-> JSON
    implementation(libs.okhttp.logging)             // ver peticiones en Logcat

    // --- Corrutinas + MVVM ---
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose)  // viewModel() en Compose
    implementation(libs.androidx.lifecycle.runtime.compose)    // collectAsStateWithLifecycle()
}