package dev.boar.checktime.domain

import dev.boar.checktime.data.AppDatabase
import dev.boar.checktime.data.Category
import dev.boar.checktime.data.Group
import dev.boar.checktime.data.Segment
import dev.boar.checktime.inMemoryDb
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TimelineRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TimelineRepository
    private var work = 0L
    private var rest = 0L
    private val m = TimeMath.MINUTE_MS

    @Before
    fun setUp() = runTest {
        db = inMemoryDb()
        repo = TimelineRepository(db)
        val gid = db.categoryDao().insertGroup(Group(name = "g", color = 0, sortOrder = 0))
        work = db.categoryDao().insertCategory(Category(groupId = gid, name = "Работа", color = 0, sortOrder = 0))
        rest = db.categoryDao().insertCategory(Category(groupId = gid, name = "Отдых", color = 0, sortOrder = 1))
    }

    @After
    fun tearDown() = db.close()

    /** Инвариант спеки: сегменты покрывают [from, to) без дыр и пересечений. */
    private fun assertContiguous(segments: List<Segment>, from: Long, to: Long) {
        var cursor = from
        for (s in segments) {
            assertEquals("gap/overlap before segment at ${s.startAt}", cursor, s.startAt)
            cursor = s.endAt
        }
        assertEquals("coverage end", to, cursor)
    }

    @Test fun startTrackingIsIdempotent() = runTest {
        assertNull(repo.trackingState())
        repo.startTracking(now = 1_000)
        repo.startTracking(now = 9_000)
        assertEquals(1_000L, repo.trackingState()!!.trackingStart)
        assertEquals(1_000L, repo.trackingState()!!.accountedUntil)
    }

    @Test fun allocateWritesOrderedSegmentsAndAdvances() = runTest {
        repo.startTracking(now = 1_000)
        val result = repo.allocate(expectedAccountedUntil = 1_000, allocations = listOf(Allocation(work, 20), Allocation(rest, 10)))
        assertEquals(AllocateResult.Saved, result)

        val segs = db.segmentDao().all()
        assertEquals(listOf(work, rest), segs.map { it.categoryId })
        assertContiguous(segs, from = 1_000, to = 1_000 + 30 * m)
        assertEquals(1_000 + 30 * m, repo.trackingState()!!.accountedUntil)
    }

    @Test fun consecutiveAllocationsStayContiguous() = runTest {
        repo.startTracking(now = 0)
        repo.allocate(0, listOf(Allocation(work, 30)))
        repo.allocate(30 * m, listOf(Allocation(rest, 5), Allocation(work, 40)))
        assertContiguous(db.segmentDao().all(), from = 0, to = 75 * m)
    }

    @Test fun staleExpectationIsRejectedWithoutWriting() = runTest {
        repo.startTracking(now = 0)
        repo.allocate(0, listOf(Allocation(work, 30)))
        val result = repo.allocate(expectedAccountedUntil = 0, allocations = listOf(Allocation(rest, 30)))
        assertEquals(AllocateResult.Stale, result)
        assertEquals(1, db.segmentDao().all().size)
        assertEquals(30 * m, repo.trackingState()!!.accountedUntil)
    }

    @Test fun allocateBeforeStartIsStale() = runTest {
        assertEquals(AllocateResult.Stale, repo.allocate(0, listOf(Allocation(work, 1))))
    }

    @Test fun emptyAllocationChangesNothing() = runTest {
        repo.startTracking(now = 0)
        assertEquals(AllocateResult.Saved, repo.allocate(0, emptyList()))
        assertEquals(0L, repo.trackingState()!!.accountedUntil)
    }
}
