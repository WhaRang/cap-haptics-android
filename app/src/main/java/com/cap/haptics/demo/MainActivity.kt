package com.cap.haptics.demo

import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import com.cap.haptics.core.Haptics
import com.cap.haptics.core.HapticsConfig
import com.cap.haptics.core.HapticsCore
import com.cap.haptics.core.model.CompositionStep
import com.cap.haptics.core.model.HapticPattern
import com.cap.haptics.core.model.HapticPrimitive
import com.cap.haptics.core.model.HapticResult
import com.cap.haptics.core.model.HapticTier
import com.cap.haptics.core.model.ViewFeedback
import com.cap.haptics.core.model.PredefinedEffect
import com.cap.haptics.core.model.Waveform

/**
 * Native test harness for the cap-haptics SDK.
 *
 * Exists so patterns can be tuned by feel without Unity in the loop -- the Unity build cycle
 * is far too slow for the dozens of iterations tuning the degradation matrix will take. It
 * grows one screen per phase; see PLAN.md section 5.4.
 *
 * A2: raw waveform playback plus the forced-tier override.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var infoText: TextView
    private lateinit var resultText: TextView
    private lateinit var durationLabel: TextView
    private lateinit var amplitudeLabel: TextView
    private lateinit var intensityLabel: TextView
    private lateinit var durationBar: SeekBar
    private lateinit var amplitudeBar: SeekBar
    private lateinit var intensityBar: SeekBar

    private val durationMs: Long get() = durationBar.progress.toLong().coerceAtLeast(1)
    private val amplitude: Int get() = amplitudeBar.progress.coerceIn(1, Waveform.MAX_AMPLITUDE)
    private val intensity: Float get() = intensityBar.progress / 100f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        applyWindowInsets()

        infoText = findViewById(R.id.infoText)
        resultText = findViewById(R.id.resultText)
        durationLabel = findViewById(R.id.durationLabel)
        amplitudeLabel = findViewById(R.id.amplitudeLabel)
        intensityLabel = findViewById(R.id.intensityLabel)
        durationBar = findViewById(R.id.durationBar)
        amplitudeBar = findViewById(R.id.amplitudeBar)
        intensityBar = findViewById(R.id.intensityBar)

        Haptics.initialize(this, HapticsConfig(verboseLogging = true))

        buildPatternGrid()
        buildViewFeedbackGrid()
        wireSliders()
        wireTierOverride()
        wireButtons()

        refreshLabels()
        refreshInfo()
    }

    /**
     * From targetSdk 35 onward the platform draws edge to edge, so a scrolling root starts
     * underneath the status bar and its first control is unreachable. Padding the ScrollView
     * by the system bar and cutout insets puts it back; `clipToPadding=false` in the layout
     * keeps content scrolling through that padding rather than being clipped by it.
     */
    private fun applyWindowInsets() {
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun wireSliders() {
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) =
                refreshLabels()

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        }
        durationBar.setOnSeekBarChangeListener(listener)
        amplitudeBar.setOnSeekBarChangeListener(listener)
        intensityBar.setOnSeekBarChangeListener(listener)
    }

    /**
     * Generated from the enum rather than laid out by hand, so adding a pattern to the SDK
     * puts a button on this screen for free -- and so a pattern can never be silently
     * missing from the harness that is supposed to be testing it. The Unity panel does the
     * same thing in U3.
     */
    private fun buildPatternGrid() {
        buildGrid(
            container = findViewById(R.id.patternGrid),
            items = HapticPattern.entries,
            label = { it.name },
            onClick = ::playPattern,
        )
    }

    /**
     * Only constants this OS actually knows are offered. Handing `performHapticFeedback` an
     * unrecognised id would be a silent no-op, which is the failure mode this whole harness
     * exists to make visible.
     */
    private fun buildViewFeedbackGrid() {
        buildGrid(
            container = findViewById(R.id.viewFeedbackGrid),
            items = ViewFeedback.entries.filter { Build.VERSION.SDK_INT >= it.minApi },
            label = { it.name },
            onClick = { feedback ->
                val result = Haptics.performViewFeedback(feedback)
                val note = if (result == HapticResult.SUPPRESSED) {
                    " — user has haptics off"
                } else {
                    ""
                }
                report("${result.name} — $feedback$note")
            },
        )
    }

    private fun <T> buildGrid(
        container: LinearLayout,
        items: List<T>,
        label: (T) -> String,
        onClick: (T) -> Unit,
        columns: Int = 3,
    ) {
        items.chunked(columns).forEach { itemsInRow ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }

            itemsInRow.forEach { item ->
                row.addView(
                    Button(this).apply {
                        text = label(item)
                        // Labels here are enum names the harness does not get to shorten --
                        // IMPACT_MEDIUM, TEXT_HANDLE_MOVE. Autosizing shrinks them to fit one
                        // line instead of wrapping and knocking the rows out of alignment.
                        maxLines = 1
                        minWidth = 0
                        minimumWidth = 0
                        setPadding(8, paddingTop, 8, paddingBottom)
                        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                            this,
                            /* autoSizeMinTextSize = */ 7,
                            /* autoSizeMaxTextSize = */ 13,
                            /* autoSizeStepGranularity = */ 1,
                            TypedValue.COMPLEX_UNIT_SP,
                        )
                        layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                        setOnClickListener { onClick(item) }
                    }
                )
            }

            // Keep the last row's buttons the same width as every other row's.
            repeat(columns - itemsInRow.size) {
                row.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                })
            }

            container.addView(row)
        }
    }

    private fun playPattern(pattern: HapticPattern) {
        val result = Haptics.playPattern(pattern, intensity)
        report("${result.name} @ T${Haptics.activeTier.level} — ${pattern.name} ×$intensity")
    }

    private fun wireTierOverride() {
        findViewById<RadioGroup>(R.id.tierGroup).setOnCheckedChangeListener { _, checkedId ->
            val requested = when (checkedId) {
                R.id.tierT1 -> HapticTier.WAVEFORM
                R.id.tierT2 -> HapticTier.PREDEFINED
                R.id.tierT3 -> HapticTier.COMPOSED
                else -> null
            }
            val effective = Haptics.setForcedTier(requested)
            report("forced ${requested?.name ?: "AUTO"} -> active T${effective.level}")
            refreshInfo()
        }
    }

    private fun wireButtons() {
        // With the tier override these are the clearest demonstration of degradation in the
        // whole harness: identical button, native effect at T2/T3, waveform guess at T1.
        mapOf(
            R.id.btnTick to PredefinedEffect.TICK,
            R.id.btnClick to PredefinedEffect.CLICK,
            R.id.btnDoubleClick to PredefinedEffect.DOUBLE_CLICK,
            R.id.btnHeavyClick to PredefinedEffect.HEAVY_CLICK,
        ).forEach { (viewId, effect) ->
            findViewById<Button>(viewId).setOnClickListener {
                val result = Haptics.playEffect(effect)
                val support = Haptics.capabilities?.supportOf(effect)
                report("${result.name} @ T${Haptics.activeTier.level} — $effect ($support)")
            }
        }

        mapOf(
            R.id.btnPrimClick to HapticPrimitive.CLICK,
            R.id.btnPrimTick to HapticPrimitive.TICK,
            R.id.btnPrimLowTick to HapticPrimitive.LOW_TICK,
            R.id.btnPrimThud to HapticPrimitive.THUD,
            R.id.btnPrimQuickRise to HapticPrimitive.QUICK_RISE,
            R.id.btnPrimSlowRise to HapticPrimitive.SLOW_RISE,
            R.id.btnPrimQuickFall to HapticPrimitive.QUICK_FALL,
            R.id.btnPrimSpin to HapticPrimitive.SPIN,
        ).forEach { (viewId, primitive) ->
            findViewById<Button>(viewId).setOnClickListener {
                val result = Haptics.playPrimitive(primitive)
                val support = Haptics.capabilities?.supportOf(primitive)
                // Support NO means substitution happened -- logcat names the replacement.
                report("${result.name} @ T${Haptics.activeTier.level} — $primitive ($support)")
            }
        }

        findViewById<Button>(R.id.btnCompRiseClick).setOnClickListener {
            playComposition(
                "Rise→Click",
                listOf(
                    CompositionStep(HapticPrimitive.QUICK_RISE, scale = 0.5f),
                    CompositionStep(HapticPrimitive.CLICK, scale = 1f, delayMs = 60),
                ),
            )
        }

        findViewById<Button>(R.id.btnCompHeartbeat).setOnClickListener {
            playComposition(
                "Heartbeat",
                listOf(
                    CompositionStep(HapticPrimitive.THUD, scale = 0.8f),
                    CompositionStep(HapticPrimitive.THUD, scale = 0.5f, delayMs = 90),
                ),
            )
        }

        findViewById<Button>(R.id.btnOneShot).setOnClickListener {
            play(longArrayOf(0, durationMs), intArrayOf(0, amplitude))
        }

        findViewById<Button>(R.id.btnPulses).setOnClickListener {
            // Even indices silent, so this degrades cleanly when amplitude control is absent.
            val gap = 60L
            play(
                timings = longArrayOf(0, durationMs, gap, durationMs, gap, durationMs),
                amplitudes = intArrayOf(0, amplitude, 0, amplitude, 0, amplitude),
            )
        }

        findViewById<Button>(R.id.btnRamp).setOnClickListener {
            // A continuously-on envelope: every segment carries amplitude, none is silent.
            // It deliberately breaks the alternating convention, which is what makes it a
            // good probe for whether the motor really honours amplitude -- without amplitude
            // control it collapses to an undifferentiated buzz, and that is the honest
            // hardware answer rather than a bug.
            val steps = 16
            val stepMs = (durationMs / steps).coerceAtLeast(1L)
            play(
                timings = LongArray(steps) { stepMs },
                amplitudes = IntArray(steps) { index ->
                    (20 + (amplitude - 20) * index / (steps - 1))
                        .coerceIn(1, Waveform.MAX_AMPLITUDE)
                },
            )
        }

        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            Haptics.cancel()
            report("cancelled")
        }

        findViewById<Button>(R.id.btnFuzz).setOnClickListener { runFuzz() }
    }

    /**
     * Hammers the real `Haptics` object with input no sane caller would produce.
     *
     * The JVM suite fuzzes the pure layers, but it cannot reach the part of the promise that
     * matters most -- that a live `Vibrator`, on this OEM's firmware, never turns bad input
     * into a crash. Only a device can answer that, so the check lives here.
     *
     * Cancels afterwards, because some of what it sends is a repeating waveform.
     */
    private fun runFuzz() {
        val nastyLongs = listOf(0L, -1L, 1L, Long.MIN_VALUE, Long.MAX_VALUE, 30L)
        val nastyInts = listOf(0, -1, 255, 256, Int.MIN_VALUE, Int.MAX_VALUE, 120)
        val nastyFloats = listOf(
            0f, 1f, -1f, 0.5f, 1e30f, -1e30f,
            Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
        )
        val results = mutableMapOf<String, Int>()
        fun record(result: HapticResult) {
            results[result.name] = (results[result.name] ?: 0) + 1
        }

        val outcome = runCatching {
            repeat(300) { index ->
                val timings = LongArray(index % 5) { nastyLongs[(it + index) % nastyLongs.size] }
                val amplitudes = IntArray(index % 6) { nastyInts[(it + index) % nastyInts.size] }
                val intensity = nastyFloats[index % nastyFloats.size]

                Waveform.create(timings, amplitudes, index % 7 - 2)
                    ?.let { record(Haptics.playWaveform(it)) }

                record(Haptics.playPattern(HapticPattern.entries[index % HapticPattern.entries.size], intensity))
                record(Haptics.playPrimitive(HapticPrimitive.entries[index % HapticPrimitive.entries.size], intensity))
                record(Haptics.playComposition(emptyList()))
                record(
                    Haptics.playComposition(
                        List(index % 70) { CompositionStep(HapticPrimitive.CLICK, intensity, index - 5) }
                    )
                )
                Haptics.setForcedTier(HapticTier.fromLevel(index % 6))
            }
        }

        Haptics.setForcedTier(null)
        Haptics.cancel()

        report(
            outcome.fold(
                onSuccess = { "FUZZ SURVIVED — " + results.entries.joinToString { "${it.key}×${it.value}" } },
                onFailure = { "FUZZ CRASHED — ${it::class.simpleName}: ${it.message}" },
            )
        )
        refreshInfo()
    }

    private fun playComposition(label: String, steps: List<CompositionStep>) {
        val result = Haptics.playComposition(steps)
        report("${result.name} @ T${Haptics.activeTier.level} — $label (${steps.size} steps)")
    }

    /**
     * Validates before building so the rejection reason can be shown on screen. `create`
     * itself is silent by design -- it lives in the pure model layer and cannot log.
     */
    private fun play(timings: LongArray, amplitudes: IntArray? = null) {
        val problem = Waveform.validate(timings, amplitudes, Waveform.NO_REPEAT)
        if (problem != null) {
            report("REJECTED — $problem")
            return
        }
        val waveform = Waveform.create(timings, amplitudes) ?: return
        val result: HapticResult = Haptics.playWaveform(waveform)
        report("${result.name} @ T${Haptics.activeTier.level} — ${waveform.totalDurationMs}ms")
    }

    private fun report(message: String) {
        resultText.text = message
    }

    private fun refreshLabels() {
        durationLabel.text = "duration: ${durationMs}ms"
        amplitudeLabel.text = "amplitude: $amplitude / ${Waveform.MAX_AMPLITUDE}"
        intensityLabel.text = "intensity: $intensity"
    }

    private fun refreshInfo() {
        val capabilities = Haptics.capabilities
        infoText.text = buildString {
            appendLine("cap-haptics ${HapticsCore.VERSION}")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
            appendLine()
            appendLine("device tier : T${Haptics.deviceTier.level} ${Haptics.deviceTier.name}")
            appendLine("active tier : T${Haptics.activeTier.level} ${Haptics.activeTier.name}")
            appendLine()
            appendLine("view feedback: ${if (Haptics.isViewFeedbackAvailable) "available" else "no Activity"}")
            appendLine("system haptics: ${Haptics.systemHapticsEnabled ?: "unreadable"}")
            appendLine()
            appendLine(capabilities?.summary() ?: "not initialized")
            appendLine("---")
            appendLine(
                "Forcing a tier swaps the code path, not the hardware.\n\n" +
                    "The 'system haptics' line above is advisory: OEM skins add intensity " +
                    "controls no app can read. Samsung keeps separate sliders under Sounds " +
                    "and vibration > Vibration intensity. A SUPPRESSED result from the " +
                    "system channel is the only authoritative answer."
            )
        }
    }
}
