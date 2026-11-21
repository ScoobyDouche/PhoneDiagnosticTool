package com.phonediagnostic.data

/**
 * Hands-on hardware checks — the ones you would run before buying a used
 * phone. Several pass on their own once the hardware has shown it works
 * (every touch cell covered, both volume keys seen, near and far both
 * reported); the rest need the person holding the phone to say what they saw
 * or heard. None needs a runtime permission: the microphone check is left out
 * deliberately, as it would need RECORD_AUDIO.
 */
enum class CheckupTest(val automatic: Boolean) {
    TOUCH(automatic = true),
    MULTITOUCH(automatic = false),
    DISPLAY(automatic = false),
    SPEAKER(automatic = false),
    EARPIECE(automatic = false),
    VIBRATION(automatic = false),
    FLASHLIGHT(automatic = false),
    BUTTONS(automatic = true),
    PROXIMITY(automatic = true),
    LIGHT(automatic = true),
    MOTION(automatic = true),
    CHARGING(automatic = true),
    HEADPHONES(automatic = true)
}

enum class CheckStatus { NOT_RUN, PASS, FAIL, SKIPPED }

data class CheckupResults(val statuses: Map<CheckupTest, CheckStatus> = emptyMap()) {

    fun status(test: CheckupTest): CheckStatus = statuses[test] ?: CheckStatus.NOT_RUN

    fun with(test: CheckupTest, status: CheckStatus) = copy(statuses = statuses + (test to status))

    val passed: Int get() = statuses.values.count { it == CheckStatus.PASS }
    val failed: Int get() = statuses.values.count { it == CheckStatus.FAIL }
    val run: Int get() = statuses.values.count { it == CheckStatus.PASS || it == CheckStatus.FAIL }
    val isEmpty: Boolean get() = statuses.values.all { it == CheckStatus.NOT_RUN }

    companion object {
        /**
         * Light sensor passes once readings span a real change — covering the
         * sensor, or moving between a lamp and shade. A factor of 5 with at
         * least 10 lx of difference rules out noise around a constant level.
         */
        fun lightPasses(minLux: Float, maxLux: Float): Boolean =
            maxLux - minLux >= 10f && maxLux >= (minLux.coerceAtLeast(0.5f) * 5f)

        /**
         * Accelerometer passes once the phone has been turned over enough that
         * gravity moved between axes: two of the three axes must each have spanned 12 m/s².
         */
        fun motionPasses(spans: FloatArray): Boolean = spans.size >= 3 && spans.take(3).count { it >= 12f } >= 2
    }
}
