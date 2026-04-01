package com.navassist.app.arc

class ActivityRecognizer {

    private val WINDOW_SIZE = 10
    private val SMOOTHING_SIZE = 5

    private val DISTANCE_CHANGE_THRESHOLD = 15
    private val ACCEL_CHANGE_THRESHOLD = 2.0f

    private val OBSTACLE_NEAR_CM = 50
    private val OBSTACLE_MID_CM = 150
    private val DARK_LDR_THRESHOLD = 200
    private val DIM_LDR_THRESHOLD = 600
    private val WALK_ACCEL_MAG = 10.3f

    data class Sample(
        val distanceCm: Int,
        val ldrValue: Int,
        val accelX: Float,
        val accelY: Float,
        val accelZ: Float
    )

    private val window = ArrayDeque<Sample>(WINDOW_SIZE + 1)
    private val labelHistory = ArrayDeque<String>(SMOOTHING_SIZE + 1)

    fun update(
        distanceCm: Int,
        ldrValue: Int,
        accelX: Float,
        accelY: Float,
        accelZ: Float
    ): String {
        val sample = Sample(distanceCm, ldrValue, accelX, accelY, accelZ)
        window.addLast(sample)
        if (window.size > WINDOW_SIZE) window.removeFirst()

        if (window.size < 2) return "Initialising"

        val normSamples = preprocess(window.toList())

        val segmentChanged = segmentChanged(window.toList())

        val features = extractFeatures(normSamples, window.toList())

        val rawLabel = classify(features, segmentChanged)

        labelHistory.addLast(rawLabel)
        if (labelHistory.size > SMOOTHING_SIZE) labelHistory.removeFirst()

        return majorityVote(labelHistory.toList())
    }

    private fun preprocess(samples: List<Sample>): List<FloatArray> {
        val dists = samples.map { it.distanceCm.toFloat() }
        val ldrs  = samples.map { it.ldrValue.toFloat() }
        val mags  = samples.map { accelMagnitude(it.accelX, it.accelY, it.accelZ) }

        return samples.indices.map { i ->
            floatArrayOf(
                normalise(dists[i], dists.min(), dists.max()),
                normalise(ldrs[i],  ldrs.min(),  ldrs.max()),
                normalise(mags[i],  mags.min(),  mags.max())
            )
        }
    }

    private fun normalise(value: Float, min: Float, max: Float): Float {
        val range = max - min
        return if (range < 1e-6f) 0.5f else (value - min) / range
    }

    private fun segmentChanged(samples: List<Sample>): Boolean {
        if (samples.size < 2) return false
        val prev = samples[samples.size - 2]
        val curr = samples.last()
        val distChange  = Math.abs(curr.distanceCm - prev.distanceCm)
        val accelChange = Math.abs(
            accelMagnitude(curr.accelX, curr.accelY, curr.accelZ) -
            accelMagnitude(prev.accelX, prev.accelY, prev.accelZ)
        )
        return distChange > DISTANCE_CHANGE_THRESHOLD || accelChange > ACCEL_CHANGE_THRESHOLD
    }

    data class Features(
        val meanDistanceCm: Float,
        val ldrVariance: Float,
        val meanAccelMagnitude: Float,
        val minDistanceCm: Int,
        val meanLdr: Float,
        val segmentChanged: Boolean
    )

    private fun extractFeatures(
        normSamples: List<FloatArray>,
        rawSamples: List<Sample>
    ): Features {
        val dists = rawSamples.map { it.distanceCm.toFloat() }
        val ldrs  = rawSamples.map { it.ldrValue.toFloat() }
        val mags  = rawSamples.map { accelMagnitude(it.accelX, it.accelY, it.accelZ) }

        val meanDist  = dists.average().toFloat()
        val meanLdr   = ldrs.average().toFloat()
        val meanMag   = mags.average().toFloat()
        val minDist   = rawSamples.minOf { it.distanceCm }

        val ldrVar = if (ldrs.size > 1) {
            ldrs.map { (it - meanLdr) * (it - meanLdr) }.average().toFloat()
        } else 0f

        return Features(meanDist, ldrVar, meanMag, minDist, meanLdr, false)
    }

    private fun classify(features: Features, segmentChanged: Boolean): String {
        return when {
            features.meanLdr < DARK_LDR_THRESHOLD ->
                "Dark environment"

            features.minDistanceCm < 20 ->
                "Entering room"

            features.minDistanceCm < OBSTACLE_NEAR_CM ->
                "Near obstacle"

            features.meanDistanceCm < OBSTACLE_MID_CM ->
                "Obstacle ahead"

            features.meanAccelMagnitude > WALK_ACCEL_MAG ->
                "Walking"

            else -> "Stationary"
        }
    }

    private fun majorityVote(labels: List<String>): String {
        return labels.groupBy { it }
            .maxByOrNull { it.value.size }
            ?.key ?: "Stationary"
    }

    private fun accelMagnitude(x: Float, y: Float, z: Float): Float =
        Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()

    fun classifyLightLevel(ldrValue: Int): String = when {
        ldrValue < DARK_LDR_THRESHOLD  -> "dark"
        ldrValue < DIM_LDR_THRESHOLD   -> "dim"
        else                           -> "bright"
    }

    fun reset() {
        window.clear()
        labelHistory.clear()
    }
}
