plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.cap.haptics.core"
    // 36, not 37: AGP stamps compileSdk into the AAR metadata as the minimum the *consumer*
    // must compile against, and Unity 6000.3 ships AGP 8.10 which tops out at android-36.
    // Nothing here needs 37 — the newest symbols used are API 34 constants.
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // @RequiresApi only. Deliberately the library's sole production dependency -- the less
    // an SDK drags into its consumers' builds, the better.
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
