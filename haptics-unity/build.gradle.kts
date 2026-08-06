plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.cap.haptics.unity"
    // 36, not 37 — same consumer constraint as :haptics-core: Unity 6000.3's AGP 8.10
    // cannot compile against android-37, and AAR metadata forces our compileSdk on consumers.
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Travels inside the AAR: R8 in the consuming app cannot see that JNI calls the
        // bridge, so without these it would strip it.
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // `implementation`, not `api`: the JNI facade exposes only primitives and Strings,
    // so no core type ever leaks across the bridge. If this ever needs to be `api`,
    // something has gone wrong with the ABI design (see PLAN.md section 3.3).
    implementation(project(":haptics-core"))

    testImplementation(libs.junit)
}
