plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.cap.haptics.unity"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
