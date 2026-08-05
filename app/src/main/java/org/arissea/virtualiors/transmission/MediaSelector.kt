package org.arissea.virtualiors.transmission

import kotlin.random.Random

data class MediaSelection<T>(
    val item: T,
    val index: Int,
    val total: Int,
) {
    init {
        require(total > 0)
        require(index in 0 until total)
    }

    val position: Int get() = index + 1
    val counter: String get() = "$position/$total"
}

class MediaSelector(
    private val pickRandomIndex: (Int) -> Int = { size -> Random.Default.nextInt(size) },
) {
    fun <T> select(items: List<T>, cycleIndex: Int, shuffle: Boolean): MediaSelection<T> {
        require(items.isNotEmpty()) { "Cannot select from an empty media list" }
        val index = if (shuffle) pickRandomIndex(items.size) else Math.floorMod(cycleIndex, items.size)
        require(index in items.indices) { "Selected media index $index is outside the current list" }
        return MediaSelection(item = items[index], index = index, total = items.size)
    }
}
