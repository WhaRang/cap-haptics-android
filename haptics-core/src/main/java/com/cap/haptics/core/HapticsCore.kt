package com.cap.haptics.core

/**
 * Library-wide constants for the cap-haptics core SDK.
 *
 * This is the Unity-agnostic layer: nothing in [com.cap.haptics.core] may reference
 * Unity types. The Unity adapter lives in the separate `:haptics-unity` module.
 */
object HapticsCore {

    /** Semantic version of the core library. */
    const val VERSION: String = "1.0.0"

    /** Log tag used by every class in the library. See `util/HLog`. */
    const val LOG_TAG: String = "CapHaptics"
}
