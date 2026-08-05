package org.arissea.virtualiors.sstv

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object WatermarkContent {
    const val TITLE = "ARISS VirtualIORS"

    private val utcFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC)

    fun imageDetails(
        callsign: String,
        showCallsign: Boolean,
        showImageNumber: Boolean,
        imageIndex: Int,
        imageCount: Int,
    ): String = buildList {
        normalizedCallsign(callsign, showCallsign)?.let(::add)
        if (showImageNumber) add("$imageIndex/$imageCount")
    }.joinToString(SEPARATOR)

    fun cameraDetails(
        callsign: String,
        showCallsign: Boolean,
        showTimestamp: Boolean,
        timestamp: Instant,
    ): String = buildList {
        normalizedCallsign(callsign, showCallsign)?.let(::add)
        if (showTimestamp) add(utcFormatter.format(timestamp))
    }.joinToString(SEPARATOR)

    private fun normalizedCallsign(callsign: String, enabled: Boolean): String? =
        callsign.trim().uppercase().takeIf { enabled && it.isNotBlank() }

    private const val SEPARATOR = "  •  "
}
