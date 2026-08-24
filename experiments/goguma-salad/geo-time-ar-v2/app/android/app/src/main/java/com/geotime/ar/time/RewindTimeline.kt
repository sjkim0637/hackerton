package com.geotime.ar.time

import java.time.Instant

data class TimelineMoment(
    val id: String,
    val title: String,
    val recordedAt: Instant,
)

sealed class RewindStop {
    object Now : RewindStop()
    data class Moment(val value: TimelineMoment) : RewindStop()
}

class RewindTimeline private constructor(
    val stops: List<RewindStop>,
) {
    fun stopAt(index: Int): RewindStop = stops[index.coerceIn(stops.indices)]

    fun olderIndex(currentIndex: Int): Int =
        (currentIndex + 1).coerceAtMost(stops.lastIndex)

    fun newerIndex(currentIndex: Int): Int =
        (currentIndex - 1).coerceAtLeast(0)

    companion object {
        fun from(moments: List<TimelineMoment>): RewindTimeline {
            val ordered = moments
                .distinctBy(TimelineMoment::id)
                .sortedByDescending(TimelineMoment::recordedAt)
                .map(RewindStop::Moment)
            return RewindTimeline(listOf(RewindStop.Now) + ordered)
        }

        fun empty(): RewindTimeline = from(emptyList())
    }
}
