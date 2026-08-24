package com.cap.haptics.unity

/**
 * The version of the JNI contract between this AAR and the C# side.
 *
 * The single most common way to lose an afternoon on a Unity plugin is a stale AAR: C# calls
 * a method the packaged Kotlin does not have, and JNI answers with a `NoSuchMethodError`
 * from somewhere unhelpful — or worse, calls a method whose meaning has changed and silently
 * plays the wrong thing.
 *
 * So the C# side reads this at init and refuses to continue on a mismatch. Failing loudly at
 * startup beats failing mysteriously later.
 *
 * **Bump [CURRENT] whenever the bridge's shape changes**: a method added, removed or
 * renamed, a parameter list changed, or the meaning of an existing id altered. Adding a new
 * enum *value* does not count — ids are append-only, and old C# simply never sends the new
 * one.
 */
object BridgeVersion {
    // v2: systemHapticsEnabled in the capabilities JSON changed from `true/false/null`
    // to the tri-state string "YES"/"NO"/"UNKNOWN" — a wire-format change a v1 C# parser
    // would misread as `false`, which is exactly the kind of silent drift this constant
    // exists to catch.
    const val CURRENT: Int = 2
}
