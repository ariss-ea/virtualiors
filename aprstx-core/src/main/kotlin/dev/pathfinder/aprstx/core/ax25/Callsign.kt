package dev.pathfinder.aprstx.core.ax25

/**
 * AX.25 callsign with optional SSID.
 * Examples: EA5KAB, EA5KAB-10, RS0ISS.
 */
data class Callsign(
    val base: String,
    val ssid: Int = 0,
) {
    init {
        require(base.isNotBlank()) { "Callsign cannot be blank" }
        require(base.length <= 6) { "AX.25 callsign base must be at most 6 characters" }
        require(base.all { it.isLetterOrDigit() }) { "AX.25 callsign must contain only letters and digits" }
        require(ssid in 0..15) { "AX.25 SSID must be in 0..15" }
    }

    val normalizedBase: String = base.uppercase()

    fun format(includeSsidZero: Boolean = false): String =
        if (ssid == 0 && !includeSsidZero) normalizedBase else "$normalizedBase-$ssid"

    override fun toString(): String = format()

    companion object {
        fun parse(raw: String): Callsign {
            val text = raw.trim().uppercase()
            require(text.isNotBlank()) { "Callsign cannot be blank" }
            val parts = text.split('-', limit = 2)
            val base = parts[0]
            val ssid = if (parts.size == 2) {
                require(parts[1].isNotBlank()) { "SSID cannot be blank" }
                parts[1].toIntOrNull() ?: error("SSID must be numeric")
            } else {
                0
            }
            return Callsign(base, ssid)
        }
    }
}
