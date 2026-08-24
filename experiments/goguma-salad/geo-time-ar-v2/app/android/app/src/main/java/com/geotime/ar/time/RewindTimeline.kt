package com.geotime.ar.time

import com.geotime.ar.spatial.SpatialCandidate
import com.geotime.ar.spatial.SpatialSourceType
import com.geotime.ar.spatial.Vector3
import java.time.Instant

data class TimelineMoment(
    val id: String,
    val title: String,
    val recordedAt: Instant,
    val poiId: String? = null,
    val mediaUrl: String? = null,
    val mimeType: String? = null,
    val position: Vector3 = Vector3(0f, 1.4f, -3f),
    val minDistanceM: Float = 0f,
    val maxDistanceM: Float = 30f,
    val viewConeDegrees: Float = 80f,
)

data class MomentStack(
    val id: String,
    val moments: List<TimelineMoment>,
) {
    init {
        require(moments.isNotEmpty()) { "MomentStack에는 하나 이상의 기록이 필요합니다" }
    }

    val markerTitle: String = "시간 기록 ${moments.size}개"

    fun momentAt(index: Int): TimelineMoment = moments[index.coerceIn(moments.indices)]

    fun indexAfterHorizontalDrag(currentIndex: Int, deltaX: Float): Int = when {
        deltaX > 0f -> (currentIndex + 1).coerceAtMost(moments.lastIndex)
        deltaX < 0f -> (currentIndex - 1).coerceAtLeast(0)
        else -> currentIndex
    }

    fun asSpatialCandidate(): SpatialCandidate {
        val placement = moments.first()
        return SpatialCandidate(
            id = id,
            title = markerTitle,
            position = placement.position,
            minDistanceM = placement.minDistanceM,
            maxDistanceM = placement.maxDistanceM,
            viewConeDegrees = placement.viewConeDegrees,
            sourceType = SpatialSourceType.MOMENT,
        )
    }

    companion object {
        fun group(moments: List<TimelineMoment>): List<MomentStack> = moments
            .distinctBy(TimelineMoment::id)
            .groupBy { it.poiId ?: it.id }
            .map { (locationId, records) ->
                MomentStack(
                    id = "moment-stack:$locationId",
                    moments = records.sortedByDescending(TimelineMoment::recordedAt),
                )
            }
            .sortedByDescending { it.moments.first().recordedAt }
    }
}

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

    fun indexAfterHorizontalDrag(currentIndex: Int, deltaX: Float): Int = when {
        deltaX > 0f -> olderIndex(currentIndex)
        deltaX < 0f -> newerIndex(currentIndex)
        else -> currentIndex
    }

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
