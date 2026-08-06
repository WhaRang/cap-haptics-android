// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}

/**
 * Where the release AARs get installed for Unity to pick up: the UPM package's own plugin
 * folder (U0 moved them out of `Assets/Plugins/Android`).
 *
 * Override with `-PcapHaptics.unityPluginDir=...`, or a line in `gradle.properties`.
 */
val unityPluginDir: String = providers
    .gradleProperty("capHaptics.unityPluginDir")
    .getOrElse(
        layout.projectDirectory
            .dir("../cap-haptics-unity/Packages/com.cap.haptics/Plugins/Android")
            .asFile
            .absolutePath
    )

/**
 * Builds both AARs and drops them into the Unity project.
 *
 * Automated from the start because copying by hand is how stale-AAR bugs happen: the symptom
 * is C# calling into Kotlin that no longer exists, and the error arrives from inside JNI
 * rather than from anything you just changed. `BridgeVersion` is the second line of defence;
 * this task is the first.
 *
 * Two AARs, not one. An AAR does not bundle its transitive dependencies, so `:haptics-core`
 * has to travel alongside `:haptics-unity`. Shipping both is simpler and less fragile than
 * reaching for a fat-AAR plugin.
 *
 * The `-release` suffix is stripped so the filenames stay stable across builds — Unity keys
 * its `.meta` files off the name, and a changing filename means a new import every time.
 */
tasks.register<Copy>("installUnityPlugin") {
    group = "cap-haptics"
    description = "Assembles both AARs and copies them into the Unity project."

    dependsOn(":haptics-core:assembleRelease", ":haptics-unity:assembleRelease")

    from(layout.projectDirectory.dir("haptics-core/build/outputs/aar")) {
        include("*-release.aar")
    }
    from(layout.projectDirectory.dir("haptics-unity/build/outputs/aar")) {
        include("*-release.aar")
    }

    into(unityPluginDir)
    rename { it.replace("-release", "") }

    // Not `logger.lifecycle(...)`: inside a task action the bare `logger` resolves to the
    // *project's* logger, dragging a Project reference into execution, which the
    // configuration cache rejects. Only serialisable state may cross that line -- and
    // `destination` is a plain String, so it can.
    val destination = unityPluginDir
    doLast {
        println("cap-haptics: installed AARs to $destination")
    }
}
