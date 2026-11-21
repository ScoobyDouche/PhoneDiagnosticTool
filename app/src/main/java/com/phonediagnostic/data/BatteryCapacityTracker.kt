package com.phonediagnostic.data

import android.content.Context

/** A full-capacity figure measured over part of a real charge. */
data class MeasuredCapacity(
    val fullMah: Int,
    /** Percentage points the measurement covered; wider is more trustworthy. */
    val spanPercent: Int,
    val measuredAtMs: Long
)

/**
 * Works out the battery's real full capacity without any special access.
 *
 * The fuel gauge's charge counter (µAh, public API) is unreliable as an
 * absolute value on many phones, but its *change* is accurate. While the phone
 * charges, this remembers the counter and level where the charge began; once
 * the level has risen by at least [MIN_SPAN_PERCENT] points the capacity is
 * Δcounter / Δlevel × 100. Offsets in the counter cancel out.
 *
 * Fed from every battery read (live view, background monitor, tile), so a
 * normal overnight charge with the monitor on is enough.
 */
class BatteryCapacityTracker private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun observe(charging: Boolean, level: Int, chargeCounterUah: Long?, nowMs: Long = System.currentTimeMillis()) {
        if (!charging || chargeCounterUah == null || level !in 0..100) {
            if (prefs.contains(KEY_START_LEVEL)) prefs.edit().remove(KEY_START_LEVEL).remove(KEY_START_COUNTER).apply()
            return
        }
        if (!prefs.contains(KEY_START_LEVEL)) {
            prefs.edit().putInt(KEY_START_LEVEL, level).putLong(KEY_START_COUNTER, chargeCounterUah).apply()
            return
        }
        val startLevel = prefs.getInt(KEY_START_LEVEL, level)
        val startCounter = prefs.getLong(KEY_START_COUNTER, chargeCounterUah)
        val estimate = estimateFullMah(startLevel, startCounter, level, chargeCounterUah) ?: return
        val span = level - startLevel
        if (prefs.getInt(KEY_FULL_MAH, 0) == estimate && prefs.getInt(KEY_SPAN, 0) == span) return
        // Keep the widest measurement from this charge; a later, narrower one
        // from a new charge still replaces an old one so the figure tracks wear.
        prefs.edit()
            .putInt(KEY_FULL_MAH, estimate)
            .putInt(KEY_SPAN, span)
            .putLong(KEY_AT, nowMs)
            .apply()
    }

    fun latest(): MeasuredCapacity? {
        val mah = prefs.getInt(KEY_FULL_MAH, 0).takeIf { it > 0 } ?: return null
        return MeasuredCapacity(mah, prefs.getInt(KEY_SPAN, 0), prefs.getLong(KEY_AT, 0L))
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS = "battery_capacity"
        private const val KEY_START_LEVEL = "start_level"
        private const val KEY_START_COUNTER = "start_counter"
        private const val KEY_FULL_MAH = "full_mah"
        private const val KEY_SPAN = "span"
        private const val KEY_AT = "at"

        /** Below this rise the 1 % level granularity makes the estimate too coarse. */
        const val MIN_SPAN_PERCENT = 20

        /**
         * Pure. Null unless the span is wide enough and the result is a
         * plausible phone battery (500 – 20 000 mAh).
         */
        fun estimateFullMah(startLevel: Int, startCounterUah: Long, level: Int, counterUah: Long): Int? {
            val span = level - startLevel
            if (span < MIN_SPAN_PERCENT) return null
            val deltaMah = (counterUah - startCounterUah) / 1000.0
            if (deltaMah <= 0) return null
            val full = (deltaMah * 100.0 / span).toInt()
            return full.takeIf { it in 500..20_000 }
        }

        @Volatile
        private var instance: BatteryCapacityTracker? = null

        fun get(context: Context): BatteryCapacityTracker =
            instance ?: synchronized(this) {
                instance ?: BatteryCapacityTracker(context).also { instance = it }
            }
    }
}
