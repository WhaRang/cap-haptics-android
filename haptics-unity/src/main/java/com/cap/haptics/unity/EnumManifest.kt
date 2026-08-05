package com.cap.haptics.unity

import com.cap.haptics.core.model.HapticPattern
import com.cap.haptics.core.model.HapticPrimitive
import com.cap.haptics.core.model.HapticResult
import com.cap.haptics.core.model.HapticTier
import com.cap.haptics.core.model.PredefinedEffect
import com.cap.haptics.core.model.ViewFeedback

/**
 * Every enum the bridge marshals as an integer, with its wire id.
 *
 * ### Why this exists
 *
 * The C# side mirrors these enums by hand, and two hand-written enums always drift
 * eventually. PLAN.md section 8 listed three ways to stop that:
 *
 * a. discipline plus a test asserting the counts match,
 * b. generating the C# from the Kotlin with a Gradle task,
 * c. a shared manifest both sides read.
 *
 * This is (c), checked at runtime. (b) is the tidier idea on paper but means parsing Kotlin
 * source in a build script, which breaks the first time someone reformats an enum. Runtime
 * validation catches exactly the same drift with none of that machinery — and catches it on
 * the device, against the AAR that is actually installed, which is where the mismatch would
 * otherwise bite.
 *
 * The C# side compares this against its own enums at init and fails loudly on any
 * disagreement. Paired with [BridgeVersion], a stale AAR reports precisely what is wrong
 * rather than throwing `NoSuchMethodError` from somewhere unhelpful.
 */
internal object EnumManifest {

    fun toJson(): String = Json.obj(
        "bridgeVersion" to Json.number(BridgeVersion.CURRENT),
        "patterns" to entries(HapticPattern.entries.map { it.name to it.id }),
        "primitives" to entries(HapticPrimitive.entries.map { it.name to it.id }),
        "effects" to entries(PredefinedEffect.entries.map { it.name to it.id }),
        "viewFeedback" to entries(ViewFeedback.entries.map { it.name to it.id }),
        "tiers" to entries(HapticTier.entries.map { it.name to it.level }),
        "results" to entries(HapticResult.entries.map { it.name to it.code }),
    )

    private fun entries(values: List<Pair<String, Int>>): String = Json.array(
        values.map { (name, id) ->
            Json.obj("name" to Json.string(name), "id" to Json.number(id))
        }
    )
}
