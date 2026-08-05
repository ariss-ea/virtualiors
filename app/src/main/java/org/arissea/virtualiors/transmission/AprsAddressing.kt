package org.arissea.virtualiors.transmission

import dev.pathfinder.aprstx.core.ax25.Callsign
import org.arissea.virtualiors.model.AppConfig
import org.arissea.virtualiors.model.DEFAULT_APRS_DESTINATION
import org.arissea.virtualiors.model.DEFAULT_CALLSIGN

data class ResolvedAprsAddressing(
    val source: Callsign,
    val destination: Callsign,
    val path: List<Callsign>,
) {
    val headerSummary: String
        get() {
            val route = if (path.isEmpty()) {
                "DIRECT"
            } else {
                "PATH: ${path.joinToString(separator = ",") { it.format() }}"
            }
            return "${source.format()} > ${destination.format()} · $route"
        }
}

object AprsAddressingResolver {
    fun resolve(input: AppConfig): ResolvedAprsAddressing {
        val config = input.normalized()
        return ResolvedAprsAddressing(
            source = safeCallsign(config.normalizedCallsign, DEFAULT_CALLSIGN),
            destination = safeCallsign(config.normalizedDestination, DEFAULT_APRS_DESTINATION),
            path = config.aprsPath.split(',')
                .mapNotNull { raw -> raw.trim().takeIf { it.isNotBlank() } }
                .take(8)
                .mapNotNull { raw ->
                    runCatching { Callsign.parse(ax25Compatible(raw, "")) }.getOrNull()
                },
        )
    }

    internal fun ax25Compatible(raw: String, fallback: String): String {
        val normalized = raw.trim().uppercase()
        val pieces = normalized.split('-', limit = 2)
        val base = pieces.firstOrNull().orEmpty().filter(Char::isLetterOrDigit).take(6)
        val ssid = pieces.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 15)
        if (base.isBlank()) return fallback
        return if (ssid == null || ssid == 0) base else "$base-$ssid"
    }

    private fun safeCallsign(raw: String, fallback: String): Callsign =
        runCatching { Callsign.parse(ax25Compatible(raw, fallback)) }
            .getOrElse { Callsign.parse(fallback) }
}

object AprsHeaderPresentation {
    fun summaryOrNull(input: AppConfig): String? =
        if (input.aprs.enabled) AprsAddressingResolver.resolve(input).headerSummary else null
}
