package com.geotime.ar.time

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class MomentStackTest {
    @Test
    fun `같은 POI의 기록을 최신순으로 묶는다`() {
        val older = moment("old", "poi-1", "2024-01-01T00:00:00Z")
        val newer = moment("new", "poi-1", "2026-01-01T00:00:00Z")

        val stacks = MomentStack.group(listOf(older, newer))

        assertEquals(1, stacks.size)
        assertEquals("시간 기록 2개", stacks.single().markerTitle)
        assertEquals(listOf("new", "old"), stacks.single().moments.map { it.id })
    }

    @Test
    fun `콘텐츠 화면에서 오른쪽은 과거 왼쪽은 최근 기록으로 이동한다`() {
        val stack = MomentStack(
            id = "stack",
            moments = listOf(
                moment("new", "poi-1", "2026-01-01T00:00:00Z"),
                moment("old", "poi-1", "2024-01-01T00:00:00Z"),
            ),
        )

        assertEquals(1, stack.indexAfterHorizontalDrag(0, 100f))
        assertEquals(0, stack.indexAfterHorizontalDrag(1, -100f))
    }

    private fun moment(id: String, poiId: String, recordedAt: String) = TimelineMoment(
        id = id,
        title = id,
        recordedAt = Instant.parse(recordedAt),
        poiId = poiId,
    )
}
