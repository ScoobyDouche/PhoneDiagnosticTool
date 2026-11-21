package com.phonediagnostic.data

import java.util.Locale

enum class SensorCategory { MOTION, POSITION, ENVIRONMENT, BODY, GESTURE, OTHER }

/**
 * Names, units and value counts for every sensor type AOSP defines, so the
 * list reads "Hinge angle · °" rather than "Type 36". Type numbers are the
 * `Sensor.TYPE_*` constants, written as literals so this stays a plain table
 * that unit tests can exercise without the Android runtime.
 */
object SensorCatalog {

    data class Kind(
        val name: String,
        val unit: String = "",
        val category: SensorCategory = SensorCategory.OTHER,
        /** How many leading entries of `SensorEvent.values` carry meaning. */
        val valueCount: Int = 3
    )

    private val KINDS: Map<Int, Kind> = mapOf(
        1 to Kind("Accelerometer", "m/s²", SensorCategory.MOTION),
        2 to Kind("Magnetic field", "µT", SensorCategory.POSITION),
        3 to Kind("Orientation", "°", SensorCategory.POSITION),
        4 to Kind("Gyroscope", "rad/s", SensorCategory.MOTION),
        5 to Kind("Light", "lx", SensorCategory.ENVIRONMENT, 1),
        6 to Kind("Pressure", "hPa", SensorCategory.ENVIRONMENT, 1),
        7 to Kind("Temperature", "°C", SensorCategory.ENVIRONMENT, 1),
        8 to Kind("Proximity", "cm", SensorCategory.POSITION, 1),
        9 to Kind("Gravity", "m/s²", SensorCategory.MOTION),
        10 to Kind("Linear acceleration", "m/s²", SensorCategory.MOTION),
        11 to Kind("Rotation vector", "", SensorCategory.MOTION, 4),
        12 to Kind("Humidity", "%", SensorCategory.ENVIRONMENT, 1),
        13 to Kind("Ambient temperature", "°C", SensorCategory.ENVIRONMENT, 1),
        14 to Kind("Magnetic field (uncalibrated)", "µT", SensorCategory.POSITION, 6),
        15 to Kind("Game rotation vector", "", SensorCategory.MOTION, 4),
        16 to Kind("Gyroscope (uncalibrated)", "rad/s", SensorCategory.MOTION, 6),
        17 to Kind("Significant motion", "", SensorCategory.GESTURE, 1),
        18 to Kind("Step detector", "", SensorCategory.BODY, 1),
        19 to Kind("Step counter", "steps", SensorCategory.BODY, 1),
        20 to Kind("Geomagnetic rotation vector", "", SensorCategory.POSITION, 4),
        21 to Kind("Heart rate", "bpm", SensorCategory.BODY, 1),
        22 to Kind("Tilt detector", "", SensorCategory.GESTURE, 1),
        23 to Kind("Wake gesture", "", SensorCategory.GESTURE, 1),
        24 to Kind("Glance gesture", "", SensorCategory.GESTURE, 1),
        25 to Kind("Pick-up gesture", "", SensorCategory.GESTURE, 1),
        26 to Kind("Wrist tilt gesture", "", SensorCategory.GESTURE, 1),
        27 to Kind("Device orientation", "", SensorCategory.POSITION, 1),
        28 to Kind("Pose (6DOF)", "", SensorCategory.POSITION, 7),
        29 to Kind("Stationary detector", "", SensorCategory.GESTURE, 1),
        30 to Kind("Motion detector", "", SensorCategory.GESTURE, 1),
        31 to Kind("Heart beat", "", SensorCategory.BODY, 1),
        32 to Kind("Dynamic sensor meta", "", SensorCategory.OTHER, 1),
        33 to Kind("Additional info", "", SensorCategory.OTHER, 1),
        34 to Kind("Off-body detector", "", SensorCategory.BODY, 1),
        35 to Kind("Accelerometer (uncalibrated)", "m/s²", SensorCategory.MOTION, 6),
        36 to Kind("Hinge angle", "°", SensorCategory.POSITION, 1),
        37 to Kind("Head tracker", "", SensorCategory.MOTION, 6),
        38 to Kind("Accelerometer (limited axes)", "m/s²", SensorCategory.MOTION, 6),
        39 to Kind("Gyroscope (limited axes)", "rad/s", SensorCategory.MOTION, 6),
        40 to Kind("Accelerometer (limited axes, uncalibrated)", "m/s²", SensorCategory.MOTION, 9),
        41 to Kind("Gyroscope (limited axes, uncalibrated)", "rad/s", SensorCategory.MOTION, 9),
        42 to Kind("Heading", "°", SensorCategory.POSITION, 2)
    )

    /** The sensors people expect a phone to have, in the order they are listed. */
    private val STANDARD_TYPES = listOf(1, 4, 2, 6, 5, 8, 9, 11, 19, 13, 12, 36)

    /** Body sensors need a runtime permission this app does not ask for. */
    private val PERMISSION_GATED = setOf(18, 19, 21, 31)

    /** First type number vendors may use for their own sensors. */
    private const val VENDOR_BASE = 65536

    fun describe(type: Int, stringType: String?): Kind =
        KINDS[type] ?: Kind(vendorName(type, stringType))

    /**
     * Vendor sensors only identify themselves by a dotted string such as
     * `com.samsung.sensor.light_cct`; its last segment is the readable part.
     */
    internal fun vendorName(type: Int, stringType: String?): String {
        val tail = stringType.orEmpty().substringAfterLast('.').replace('_', ' ').trim()
        if (tail.isEmpty()) return "Type $type"
        val name = tail.replaceFirstChar { it.titlecase(Locale.US) }
        return if (type >= VENDOR_BASE) "$name (vendor)" else name
    }

    /** Whether a brief listener registration can be expected to deliver a reading. */
    fun canSample(type: Int): Boolean = type !in PERMISSION_GATED

    /** "9.812, 0.034, 0.120 m/s²": only the values that mean something, with the unit. */
    fun formatValues(type: Int, stringType: String?, values: FloatArray): String {
        val kind = describe(type, stringType)
        val shown = values.take(kind.valueCount.coerceAtLeast(1))
        val text = shown.joinToString(", ") { String.format(Locale.US, "%.3f", it) }
        return if (kind.unit.isEmpty()) text else "$text ${kind.unit}"
    }

    /** Each standard sensor with whether one of [presentTypes] provides it. */
    fun coverage(presentTypes: Collection<Int>): List<Pair<String, Boolean>> {
        val present = presentTypes.toSet()
        return STANDARD_TYPES.map { type -> KINDS.getValue(type).name to (type in present) }
    }
}
