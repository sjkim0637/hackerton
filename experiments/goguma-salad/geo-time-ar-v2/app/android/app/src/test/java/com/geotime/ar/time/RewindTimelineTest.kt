package com.geotime.ar.time

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RewindTimelineTest {
    @Test
    fun `Moment가 최신순으로 정렬되고 NOW가 첫 위치가 된다`() {
        val older = TimelineMoment("older", "오래된 기록", Instant.parse("2022-01-01T00:00:00Z"))
        val newer = TimelineMoment("newer", "최근 기록", Instant.parse("2025-01-01T00:00:00Z"))

        val timeline = RewindTimeline.from(listOf(older, newer))

        assertSame(RewindStop.Now, timeline.stopAt(0))
        assertEquals("newer", (timeline.stopAt(1) as RewindStop.Moment).value.id)
        assertEquals("older", (timeline.stopAt(2) as RewindStop.Moment).value.id)
    }

    @Test
    fun `이전과 다음 이동은 Timeline 범위를 벗어나지 않는다`() {
        val moment = TimelineMoment("one", "기록", Instant.parse("2025-01-01T00:00:00Z"))
        val timeline = RewindTimeline.from(listOf(moment))

        assertEquals(1, timeline.olderIndex(0))
        assertEquals(1, timeline.olderIndex(1))
        assertEquals(0, timeline.newerIndex(1))
        assertEquals(0, timeline.newerIndex(0))
    }

    @Test
    fun `오른쪽 Drag는 과거로 가고 왼쪽 Drag는 NOW 방향으로 간다`() {
        val moment = TimelineMoment("one", "기록", Instant.parse("2025-01-01T00:00:00Z"))
        val timeline = RewindTimeline.from(listOf(moment))

        assertEquals(1, timeline.indexAfterHorizontalDrag(0, 100f))
        assertEquals(0, timeline.indexAfterHorizontalDrag(1, -100f))
    }
}
