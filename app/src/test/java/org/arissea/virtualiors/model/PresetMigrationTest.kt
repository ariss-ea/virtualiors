package org.arissea.virtualiors.model

import org.arissea.virtualiors.persistence.LegacyPresetData
import org.arissea.virtualiors.persistence.PresetMigration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetMigrationTest {
    @Test
    fun v1AudioPresetMigratesToVersionTwoWithoutLosingUrisOrVox() {
        val migrated = PresetMigration.v1ToV2(
            LegacyPresetData(
                name = "Classroom",
                audioUris = listOf("content://one.wav", "content://two.wav"),
                useExternalTtsAudio = false,
                ttsAudioUri = null,
                speakEvery = 4,
                cooldownSeconds = 90,
                shuffle = true,
                ttsPhrase = "Hello class",
                voxTone = true,
            ),
        )
        assertEquals(CURRENT_SCHEMA_VERSION, migrated.config.schemaVersion)
        assertEquals(SstvSourceType.AUDIO_FILES, migrated.config.sourceType)
        assertEquals(listOf("content://one.wav", "content://two.wav"), migrated.config.audioSources.map { it.uri })
        assertTrue(migrated.config.voice.enabled)
        assertEquals(4, migrated.config.voice.everyImages)
        assertTrue(migrated.config.globalVoxTone)
        assertFalse(migrated.config.aprs.enabled)
    }

    @Test
    fun builtInDemosSeparateSstvAndAprsExamples() {
        val demos = BuiltInPresets.all()
        assertEquals(setOf("Demo Robot36", "Demo PD120", "Demo APRS"), demos.map { it.name }.toSet())
        assertTrue(demos.all { it.config.sourceType == SstvSourceType.IMAGE_FILES })
        assertTrue(demos.all { it.config.audioSources.isEmpty() })
        assertFalse(demos.first { it.name == "Demo Robot36" }.config.aprs.enabled)
        assertFalse(demos.first { it.name == "Demo PD120" }.config.aprs.enabled)
        val aprsDemo = demos.first { it.name == "Demo APRS" }.config.aprs
        assertTrue(aprsDemo.enabled)
        assertTrue(aprsDemo.beaconEnabled)
        assertTrue(aprsDemo.telemetryEnabled)
        assertTrue(aprsDemo.customTextEnabled)
        assertEquals("VirtualIORS - Amateur Radio on the ISS Emulator", aprsDemo.customText)
    }

    @Test
    fun newTextDefaultsStayEmptyWithoutReplacingUserContent() {
        assertEquals("Hello from Virtual I O R S", VoiceConfig().phrase)
        assertEquals("", AprsConfig().customText)

        val migrated = PresetMigration.v1ToV2(
            LegacyPresetData(
                name = "Custom voice",
                audioUris = emptyList(),
                useExternalTtsAudio = false,
                ttsAudioUri = null,
                speakEvery = 2,
                cooldownSeconds = 120,
                shuffle = false,
                ttsPhrase = "Keep my classroom message",
                voxTone = false,
            ),
        )
        assertEquals("Keep my classroom message", migrated.config.voice.phrase)
    }
}
