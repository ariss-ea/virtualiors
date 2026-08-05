package dev.pathfinder.aprstx.core.aprs

import dev.pathfinder.aprstx.core.ax25.Callsign
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object AprsPayloads {
    /**
     * Uncompressed APRS position.
     * Example: !3840.00NR00229.00W&ARISS ISS RX iGate...
     */
    fun position(
        latitude: Double,
        longitude: Double,
        comment: String = "APRS TX Pathfinder",
        symbolTable: Char = '/',
        symbolCode: Char = '>',
        messaging: Boolean = false,
    ): String {
        require(latitude in -90.0..90.0) { "latitude must be in -90..90" }
        require(longitude in -180.0..180.0) { "longitude must be in -180..180" }
        val dti = if (messaging) '=' else '!'
        return buildString {
            append(dti)
            append(formatLatitude(latitude))
            append(symbolTable)
            append(formatLongitude(longitude))
            append(symbolCode)
            append(comment.takeIf { it.isNotBlank() }?.let { it } ?: "")
        }
    }

    fun issLikePosition(
        latitude: Double,
        longitude: Double,
        comment: String = "ARISS ISS RX iGate SDRSharp+Gpredict+Direwolf Alcaraz",
    ): String = position(
        latitude = latitude,
        longitude = longitude,
        comment = comment,
        symbolTable = 'R',
        symbolCode = '&',
        messaging = false,
    )

    fun status(text: String): String = ">" + text.take(200)

    /** APRS text message, with a 9-character padded addressee. */
    fun message(addressee: Callsign, text: String, id: String? = null): String {
        val padded = addressee.normalizedBase.padEnd(9, ' ').take(9)
        val suffix = id?.takeIf { it.isNotBlank() }?.let { "{${it.take(5)}" } ?: ""
        return ":$padded:${text.take(67)}$suffix"
    }

    /** Classic APRS telemetry data packet: T#nnn,a1,a2,a3,a4,a5,bbbbbbbb */
    fun telemetry(
        sequence: Int,
        analog1: Int,
        analog2: Int,
        analog3: Int,
        analog4: Int,
        analog5: Int,
        bits: String = "00000000",
    ): String {
        require(sequence in 0..999) { "telemetry sequence must be in 0..999" }
        val analogs = listOf(analog1, analog2, analog3, analog4, analog5).map {
            it.coerceIn(0, 255)
        }
        val bitText = bits.padEnd(8, '0').take(8).map { if (it == '1') '1' else '0' }.joinToString("")
        return buildString {
            append("T#")
            append(sequence.toString().padStart(3, '0'))
            analogs.forEach { append(','); append(it.toString().padStart(3, '0')) }
            append(',')
            append(bitText)
        }
    }

    fun telemetryNames(addressee: Callsign, vararg names: String): String = telemetryMetadata(addressee, "PARM", names.toList())
    fun telemetryUnits(addressee: Callsign, vararg units: String): String = telemetryMetadata(addressee, "UNIT", units.toList())

    /** Five comma-separated equation triples. Defaults are usually 0,1,0 for raw 0..255 values. */
    fun telemetryEquations(addressee: Callsign, equations: List<Triple<Double, Double, Double>>): String {
        require(equations.size == 5) { "EQNS needs exactly five equation triples" }
        val values = equations.flatMap { listOf(it.first, it.second, it.third) }
            .joinToString(",") { trimNumber(it) }
        return metadataMessage(addressee, "EQNS.$values")
    }

    fun telemetryBits(addressee: Callsign, labels: String, projectTitle: String = "APRS TX Pathfinder"): String =
        metadataMessage(addressee, "BITS.${labels.take(8).padEnd(8, '0')},${projectTitle.take(23)}")

    private fun telemetryMetadata(addressee: Callsign, prefix: String, values: List<String>): String =
        metadataMessage(addressee, "$prefix." + values.joinToString(",") { it.take(7) })

    private fun metadataMessage(addressee: Callsign, body: String): String = message(addressee, body)

    fun formatLatitude(latitude: Double): String {
        val hemisphere = if (latitude >= 0) 'N' else 'S'
        return formatDegreesMinutes(abs(latitude), 2, hemisphere)
    }

    fun formatLongitude(longitude: Double): String {
        val hemisphere = if (longitude >= 0) 'E' else 'W'
        return formatDegreesMinutes(abs(longitude), 3, hemisphere)
    }

    private fun formatDegreesMinutes(value: Double, degreeDigits: Int, suffix: Char): String {
        var degrees = value.toInt()
        var minutes = (value - degrees) * 60.0
        // Avoid 12 60.00 after rounding.
        if ((minutes * 100.0).roundToInt() >= 6000) {
            degrees += 1
            minutes = 0.0
        }
        val degText = degrees.toString().padStart(degreeDigits, '0')
        val minText = String.format(Locale.US, "%05.2f", minutes)
        return "$degText$minText$suffix"
    }

    private fun trimNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
}
