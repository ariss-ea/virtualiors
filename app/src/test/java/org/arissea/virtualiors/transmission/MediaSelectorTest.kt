package org.arissea.virtualiors.transmission

import org.arissea.virtualiors.sstv.WatermarkContent
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSelectorTest {
    @Test
    fun sequentialSelectionKeepsCyclePosition() {
        val items = (1..12).map { "item-$it" }
        val selector = MediaSelector { error("Random picker must not run in sequential mode") }

        assertEquals("1/12", selector.select(items, cycleIndex = 0, shuffle = false).counter)
        assertEquals("7/12", selector.select(items, cycleIndex = 6, shuffle = false).counter)
        assertEquals("2/12", selector.select(items, cycleIndex = 13, shuffle = false).counter)
    }

    @Test
    fun shuffledSelectionReportsThePickedListPositions() {
        val items = (1..12).map { "item-$it" }
        val picks = ArrayDeque(listOf(6, 1, 10))
        val selector = MediaSelector { picks.removeFirst() }

        val selections = List(3) { cycle -> selector.select(items, cycleIndex = cycle, shuffle = true) }

        assertEquals(listOf("item-7", "item-2", "item-11"), selections.map { it.item })
        assertEquals(listOf("7/12", "2/12", "11/12"), selections.map { it.counter })
    }

    @Test
    fun shuffledPositionFollowsTheCurrentReorderedList() {
        val original = (1..12).map { "item-$it" }
        var pickedIndex = 6
        val selector = MediaSelector { pickedIndex }
        val beforeReorder = selector.select(original, cycleIndex = 0, shuffle = true)

        val reordered = original.toMutableList().apply { add(0, removeAt(6)) }
        pickedIndex = 0
        val afterReorder = selector.select(reordered, cycleIndex = 1, shuffle = true)

        assertEquals("item-7", beforeReorder.item)
        assertEquals("7/12", beforeReorder.counter)
        assertEquals("item-7", afterReorder.item)
        assertEquals("1/12", afterReorder.counter)
    }

    @Test
    fun imageSelectionFeedsTheSameCounterToWatermarkAndTransmissionLabel() {
        val items = (1..12).map { "image-$it" }
        val selected = MediaSelector { 6 }.select(items, cycleIndex = 0, shuffle = true)

        val watermark = WatermarkContent.imageDetails(
            callsign = "VIORS",
            showCallsign = false,
            showImageNumber = true,
            imageIndex = selected.position,
            imageCount = selected.total,
        )
        val transmissionLabel = "Robot36 image ${selected.counter}"

        assertEquals("7/12", watermark)
        assertEquals("Robot36 image 7/12", transmissionLabel)
    }
}
