plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.gsprasada.nexoracloud"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.gsprasada.nexoracloud"
        minSdk = 26
        targetSdk = 35
        versionCode = 54
        versionName = "5.1.1-alpha"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        val telegramApiId = providers.environmentVariable("TELEGRAM_API_ID").orNull?.takeIf { it.matches(Regex("\\d+")) } ?: "0"
        val telegramApiHash = providers.environmentVariable("TELEGRAM_API_HASH").orNull?.takeIf { it.matches(Regex("[0-9a-fA-F]{32}")) } ?: ""
        buildConfigField("int", "TELEGRAM_API_ID", telegramApiId)
        buildConfigField("String", "TELEGRAM_API_HASH", "\"$telegramApiHash\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets.getByName("main") {
        java.srcDir("../third_party/tdlib/example/android/tdlib/java")
        jniLibs.srcDir("../third_party/tdlib/example/android/tdlib/libs")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.zxing:core:3.5.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
