package com.cap.haptics.demo

import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cap.haptics.core.Haptics
import com.cap.haptics.core.HapticsConfig
import com.cap.haptics.core.HapticsCore
import com.cap.haptics.core.model.HapticResult
import com.cap.haptics.core.model.HapticTier
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
    private lateinit var durationBar: SeekBar
    private lateinit var amplitudeBar: SeekBar

    private val durationMs: Long get() = durationBar.progress.toLong().coerceAtLeast(1)
    private val amplitude: Int get() = amplitudeBar.progress.coerceIn(1, Waveform.MAX_AMPLITUDE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        infoText = findViewById(R.id.infoText)
        resultText = findViewById(R.id.resultText)
        durationLabel = findViewById(R.id.durationLabel)
        amplitudeLabel = findViewById(R.id.amplitudeLabel)
        durationBar = findViewById(R.id.durationBar)
        amplitudeBar = findViewById(R.id.amplitudeBar)

        Haptics.initialize(this, HapticsConfig(verboseLogging = true))

        wireSliders()
        wireTierOverride()
        wireButtons()

        refreshLabels()
        refreshInfo()
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
            appendLine(capabilities?.summary() ?: "not initialized")
            appendLine("---")
            appendLine(
                "Forcing a tier swaps the code path, not the hardware. System vibration " +
                    "settings can still silence everything above; A6 surfaces what of that " +
                    "is readable."
            )
        }
    }
}
