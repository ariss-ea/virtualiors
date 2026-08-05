package org.arissea.virtualiors.transmission

import org.arissea.virtualiors.model.AppConfig
import org.arissea.virtualiors.model.AprsConfig
import org.arissea.virtualiors.model.VoiceConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransmissionSchedulerTest {
    private val scheduler = TransmissionScheduler()

    @Test
    fun voiceBeforeAprsWithWaitingBetweenEveryBlock() {
        val plan = scheduler.planCycle(
            imageNumber = 2,
            config = AppConfig(
                voice = VoiceConfig(enabled = true, everyImages = 2),
                aprs = AprsConfig(enabled = true, everyImages = 2),
            ),
        )
        assertEquals(
            listOf(
                BlockKind.SSTV_IMAGE,
                BlockKind.WAITING,
                BlockKind.VOICE,
                BlockKind.WAITING,
                BlockKind.APRS_BEACON,
                BlockKind.WAITING,
            ),
            plan.map { it.kind },
        )
    }

    @Test
    fun onlyVoiceHasRequiredOrder() {
        val plan = scheduler.planCycle(3, AppConfig(voice = VoiceConfig(enabled = true, everyImages = 3)))
        assertEquals(
            listOf(BlockKind.SSTV_IMAGE, BlockKind.WAITING, BlockKind.VOICE, BlockKind.WAITING),
            plan.map { it.kind },
        )
    }

    @Test
    fun onlyAprsHasRequiredOrder() {
        val plan = scheduler.planCycle(4, AppConfig(aprs = AprsConfig(enabled = true, everyImages = 4)))
        assertEquals(
            listOf(BlockKind.SSTV_IMAGE, BlockKind.WAITING, BlockKind.APRS_BEACON, BlockKind.WAITING),
            plan.map { it.kind },
        )
    }

    @Test
    fun noOptionalBlocksLeavesImageAndWaiting() {
        assertEquals(
            listOf(BlockKind.SSTV_IMAGE, BlockKind.WAITING),
            scheduler.planCycle(1, AppConfig()).map { it.kind },
        )
    }

    @Test
    fun globalVoxUsesToneForSstvAndVoiceButAprsPreambleForPackets() {
        val plan = scheduler.planCycle(
            1,
            AppConfig(
                globalVoxTone = true,
                voice = VoiceConfig(enabled = true, everyImages = 1),
                aprs = AprsConfig(enabled = true, everyImages = 1, customTextEnabled = true),
            ),
        )
        val audioBlocks = plan.filter { it.kind == BlockKind.SSTV_IMAGE || it.kind == BlockKind.VOICE }
        val aprsBlocks = plan.filter { it.kind in TransmissionScheduler.APRS_KINDS }
        assertTrue(audioBlocks.all { it.voxToneBefore })
        assertTrue(aprsBlocks.all { !it.voxToneBefore })
        assertTrue(aprsBlocks.all { it.aprsPreambleSeconds == 1.0 })
        assertTrue(plan.filter { it.kind == BlockKind.WAITING }.all { !it.voxToneBefore })
        assertFalse(scheduler.planCycle(1, AppConfig()).first().voxToneBefore)
    }

    @Test
    fun aprsUsesNormalPreambleWhenVoxHelpIsOff() {
        val aprs = scheduler.planCycle(
            1,
            AppConfig(aprs = AprsConfig(enabled = true, everyImages = 1)),
        ).first { it.kind == BlockKind.APRS_BEACON }
        assertFalse(aprs.voxToneBefore)
        assertEquals(TransmissionScheduler.DEFAULT_APRS_PREAMBLE_SECONDS, aprs.aprsPreambleSeconds, 0.0)
    }

    @Test
    fun multipleAprsPacketsUsePacketSpacing() {
        val plan = scheduler.planCycle(
            1,
            AppConfig(aprs = AprsConfig(enabled = true, everyImages = 1, packetSpacingSeconds = 5, telemetryEnabled = true)),
        )
        val packetWait = plan.first { it.waitReason == WaitReason.APRS_PACKET_SPACING }
        assertEquals(5_000L, packetWait.durationMs)
    }
}
