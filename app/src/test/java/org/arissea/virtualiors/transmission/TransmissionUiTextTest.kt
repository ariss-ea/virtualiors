package org.arissea.virtualiors.transmission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TransmissionUiTextTest {
    @Test
    fun aprsKindsUseShortClassroomLabelsAndNeverExposeMonitorText() {
        val expected = mapOf(
            BlockKind.APRS_BEACON to "APRS beacon",
            BlockKind.APRS_GPS_POSITION to "APRS position",
            BlockKind.APRS_TELEMETRY to "APRS telemetry",
            BlockKind.APRS_TEXT to "APRS text",
        )

        expected.forEach { (kind, label) ->
            val text = TransmissionUiText.forState(state(kind, "VIORS>CQ:>technical payload"))
            assertEquals("Transmitting", text.status)
            assertEquals(label, text.detail)
            assertFalse(text.detail.contains('>'))
            assertFalse(text.status.contains('\n'))
            assertFalse(text.detail.contains('\n'))
        }
    }

    @Test
    fun waitingUsesOneConciseNextLabel() {
        assertEquals("Next: APRS", TransmissionUiText.forState(state(BlockKind.WAITING, next = "APRS Beacon")).detail)
        assertEquals("Next: Voice", TransmissionUiText.forState(state(BlockKind.WAITING, next = "Voice")).detail)
        assertEquals("Next: Image", TransmissionUiText.forState(state(BlockKind.WAITING, next = "Next image")).detail)
    }

    @Test
    fun generatedImageLabelIsPreservedAsTheSingleDetailLine() {
        val text = TransmissionUiText.forState(state(BlockKind.SSTV_IMAGE, "Robot36 image 1/12"))
        assertEquals("Transmitting", text.status)
        assertEquals("Robot36 image 1/12", text.detail)
    }

    @Test
    fun cameraPreparationUsesTheTwoRealProcessStates() {
        val takingPhoto = TransmissionUiText.forState(
            state(BlockKind.WAITING, detail = "Taking photo", durationMs = 0),
        )
        val preparingImage = TransmissionUiText.forState(
            state(BlockKind.WAITING, detail = "Preparing image", durationMs = 0),
        )

        assertEquals("Preparing", takingPhoto.status)
        assertEquals("Taking photo", takingPhoto.detail)
        assertEquals("Preparing", preparingImage.status)
        assertEquals("Preparing image", preparingImage.detail)
        assertFalse(takingPhoto.detail.contains('\n'))
        assertFalse(preparingImage.detail.contains('\n'))
    }

    @Test
    fun technicalEncodingDetailsRemainHiddenDuringPreparation() {
        val text = TransmissionUiText.forState(
            state(
                BlockKind.WAITING,
                detail = "Encoding Robot36 on device",
                next = "SSTV image",
                durationMs = 0,
            ),
        )
        assertEquals("Preparing", text.status)
        assertEquals("Preparing image", text.detail)
    }

    private fun state(
        kind: BlockKind,
        detail: String = "Transmitter cooldown",
        next: String = "Next image",
        durationMs: Long = 1_000,
    ) = TransmissionUiState(
        kind = kind,
        detail = detail,
        nextLabel = next,
        durationMs = durationMs,
        elapsedMs = 250,
        progress = 0.25f,
        voxActive = false,
        aprsPreambleActive = false,
    )
}
