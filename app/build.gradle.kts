import org.gradle.kotlin.dsl.implementation

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.kapt") // For annotation processing
    id("com.google.dagger.hilt.android") // Hilt plugin
    id("com.google.gms.google-services") // Firebase
}

android {
    namespace = "com.growtic.tradeveil"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.tradeveil"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    packagingOptions {
        exclude("META-INF/LICENSE.md")
        exclude("META-INF/NOTICE.md")
        exclude("META-INF/LICENSE")
        exclude("META-INF/NOTICE")
        exclude("META-INF/DEPENDENCIES")
    }
}

dependencies {
    // AndroidX Core & UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.legacy.support.v4)
    implementation("androidx.fragment:fragment-ktx:1.4.1")
    implementation("androidx.recyclerview:recyclerview:1.2.1")

    implementation ("com.google.android.material:material:1.9.0")


    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.4.1")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.4.1")
    implementation ("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")

    // Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.1"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    // Google Play Services
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.google.android.gms:play-services-ads:22.6.0")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.13.0")
    kapt("com.github.bumptech.glide:compiler:4.13.0")

    // Hilt
    implementation("androidx.hilt:hilt-navigation-fragment:1.0.0")
    implementation("com.google.dagger:hilt-android:2.51")
    kapt("com.google.dagger:hilt-compiler:2.51")

    // WorkManager & concurrency
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.1.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")

    // ProgressBar library
    implementation("com.github.TomLeCollegue:ProgressBar-Library-Android-Kotlin:0.1.1")

    // Quiz
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.github.f0ris.sweetalert:library:1.6.2")

    // Image & Animation
    implementation("com.airbnb.android:lottie:3.4.0")
    implementation("de.hdodenhof:circleimageview:3.1.0")

    // JavaMail (careful with size)
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}