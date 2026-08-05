package org.arissea.virtualiors.model

object BuiltInPresets {
    val names = setOf("Demo Robot36", "Demo PD120", "Demo APRS")

    fun all(): List<Preset> {
        val robot = (1..12).map { index ->
            val suffix = index.toString().padStart(2, '0')
            MediaSource(resourceName = "demo_robot36_$suffix", displayName = "Robot36 demo $index")
        }
        val pd120 = (1..12).map { index ->
            val suffix = index.toString().padStart(2, '0')
            MediaSource(resourceName = "demo_pd120_$suffix", displayName = "PD120 demo $index")
        }
        val base = AppConfig(
            sourceType = SstvSourceType.IMAGE_FILES,
            imageSources = robot,
            sstvMode = SstvModeOption.ROBOT36,
            cooldownSeconds = 120,
            shuffle = false,
            globalVoxTone = true,
        )
        return listOf(
            Preset(
                name = "Demo Robot36",
                config = base,
                builtIn = true,
            ),
            Preset(
                name = "Demo PD120",
                config = base.copy(imageSources = pd120, sstvMode = SstvModeOption.PD120),
                builtIn = true,
            ),
            Preset(
                name = "Demo APRS",
                config = base.copy(
                    aprs = AprsConfig(
                        enabled = true,
                        everyImages = 1,
                        beaconEnabled = true,
                        telemetryEnabled = true,
                        customTextEnabled = true,
                        customText = "VirtualIORS - Amateur Radio on the ISS Emulator",
                    ),
                ),
                builtIn = true,
            ),
        )
    }
}
