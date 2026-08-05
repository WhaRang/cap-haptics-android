plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.cap.haptics.demo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.cap.haptics.demo"
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
}

dependencies {
    // The native test harness consumes the SDK exactly as any third-party app would --
    // it never sees :haptics-unity. That module is the Unity adapter only.
    implementation(project(":haptics-core"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}