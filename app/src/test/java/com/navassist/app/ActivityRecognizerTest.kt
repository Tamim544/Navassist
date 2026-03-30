package com.navassist.app

import com.navassist.app.arc.ActivityRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the Activity Recognition Chain (ARC).
 *
 * Validates all 6 ARC steps without requiring Android hardware.
 */
class ActivityRecognizerTest {

    private lateinit var recognizer: ActivityRecognizer

    @Before
    fun setUp() {
        recognizer = ActivityRecognizer()
    }

    // ── Step 1+2: Data collection and preprocessing ───────────────

    @Test
    fun `initialising label returned with single sample`() {
        val label = recognizer.update(100, 500, 0f, 0f, 9.81f)
        assertEquals("Initialising", label)
    }

    @Test
    fun `returns non-null label after window fills`() {
        repeat(10) { i ->
            recognizer.update(100 + i, 400, 0f, 0f, 9.81f)
        }
        val label = recognizer.update(100, 400, 0f, 0f, 9.81f)
        assertNotNull(label)
        assertTrue(label.isNotEmpty())
    }

    // ── Step 5: Classification ─────────────────────────────────────

    @Test
    fun `dark environment classified when LDR below 200`() {
        // Fill window with dark readings (LDR = 100)
        repeat(15) { recognizer.update(200, 100, 0f, 0f, 9.81f) }
        val label = recognizer.update(200, 100, 0f, 0f, 9.81f)
        assertEquals("Dark environment", label)
    }

    @Test
    fun `near obstacle classified when distance below 50cm`() {
        repeat(15) { recognizer.update(30, 500, 0f, 0f, 9.81f) }
        val label = recognizer.update(30, 500, 0f, 0f, 9.81f)
        assertEquals("Near obstacle", label)
    }

    @Test
    fun `entering room classified when distance below 20cm`() {
        repeat(15) { recognizer.update(10, 500, 0f, 0f, 9.81f) }
        val label = recognizer.update(10, 500, 0f, 0f, 9.81f)
        assertEquals("Entering room", label)
    }

    @Test
    fun `stationary classified when clear path and no movement`() {
        repeat(15) { recognizer.update(300, 700, 0f, 0f, 9.81f) }
        val label = recognizer.update(300, 700, 0f, 0f, 9.81f)
        assertEquals("Stationary", label)
    }

    @Test
    fun `walking classified with significant accelerometer magnitude`() {
        // Accel magnitude ≈ sqrt(0 + 25 + 9.81^2) ≈ large value
        repeat(15) { recognizer.update(300, 700, 0f, 5f, 9.81f) }
        val label = recognizer.update(300, 700, 0f, 5f, 9.81f)
        assertEquals("Walking", label)
    }

    // ── Step 6: Post-processing (majority vote) ────────────────────

    @Test
    fun `majority vote smooths occasional different label`() {
        // Feed 14 "Stationary" readings then 1 "Walking" — should still give Stationary
        repeat(14) { recognizer.update(300, 700, 0f, 0f, 9.81f) }
        // One high-movement reading
        val label = recognizer.update(300, 700, 0f, 5f, 9.81f)
        // With 5-label smoothing and 4 prior "Stationary", majority should be Stationary
        assertEquals("Stationary", label)
    }

    // ── classifyLightLevel helper ──────────────────────────────────

    @Test
    fun `light level classified as dark below 200`() {
        assertEquals("dark", recognizer.classifyLightLevel(50))
        assertEquals("dark", recognizer.classifyLightLevel(199))
    }

    @Test
    fun `light level classified as dim between 200 and 600`() {
        assertEquals("dim", recognizer.classifyLightLevel(200))
        assertEquals("dim", recognizer.classifyLightLevel(400))
        assertEquals("dim", recognizer.classifyLightLevel(599))
    }

    @Test
    fun `light level classified as bright above 600`() {
        assertEquals("bright", recognizer.classifyLightLevel(600))
        assertEquals("bright", recognizer.classifyLightLevel(1000))
    }

    // ── reset ──────────────────────────────────────────────────────

    @Test
    fun `reset clears state and returns initialising again`() {
        repeat(5) { recognizer.update(100, 500, 0f, 0f, 9.81f) }
        recognizer.reset()
        val label = recognizer.update(100, 500, 0f, 0f, 9.81f)
        assertEquals("Initialising", label)
    }
}
