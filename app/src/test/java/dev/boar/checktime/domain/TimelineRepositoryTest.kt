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
        assertEquals(0L, repo.trackingState()!!.trackingStart)
        assertEquals(0L, repo.trackingState()!!.accountedUntil)
    }

    @Test fun startTrackingAlignsToMinute() = runTest {
        repo.startTracking(now = 90_500)
        assertEquals(60_000L, repo.trackingState()!!.accountedUntil)
        assertEquals(60_000L, repo.trackingState()!!.trackingStart)
    }

    @Test fun allocateWritesOrderedSegmentsAndAdvances() = runTest {
        repo.startTracking(now = 0)
        val result = repo.allocate(expectedAccountedUntil = 0, allocations = listOf(Allocation(work, 20), Allocation(rest, 10)))
        assertEquals(AllocateResult.Saved, result)

        val segs = db.segmentDao().all()
        assertEquals(listOf(work, rest), segs.map { it.categoryId })
        assertContiguous(segs, from = 0, to = 30 * m)
        assertEquals(30 * m, repo.trackingState()!!.accountedUntil)
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

    /** 0–30 work, 30–45 rest, 45–75 work; accountedUntil = 75 мин. */
    private suspend fun threeSegments(): List<Segment> {
        repo.startTracking(now = 0)
        repo.allocate(0, listOf(Allocation(work, 30), Allocation(rest, 15), Allocation(work, 30)))
        return db.segmentDao().all()
    }

    @Test fun changeCategoryRewritesOnlyThatSegment() = runTest {
        // Соседи одной категории после смены склеиваются (см. changeCategoryCoalescesWithNeighbours),
        // поэтому здесь middle меняется на категорию, отличную от соседей.
        repo.startTracking(now = 0)
        repo.allocate(0, listOf(Allocation(work, 30), Allocation(rest, 15)))
        val segs = db.segmentDao().all()
        assertEquals(EditResult.Done, repo.changeCategory(segs[1].id, work))
        val after = db.segmentDao().all()
        assertEquals(1, after.size)
        assertEquals(work, after[0].categoryId)
        assertContiguous(after, 0, 45 * m)
    }

    @Test fun changeCategoryCoalescesWithNeighbours() = runTest {
        val segs = threeSegments() // work 0–30, rest 30–45, work 45–75
        assertEquals(EditResult.Done, repo.changeCategory(segs[1].id, work))
        val all = db.segmentDao().all()
        assertEquals(1, all.size)
        assertEquals(work, all[0].categoryId)
        assertContiguous(all, 0, 75 * m)
    }

    @Test fun allocateExtendsPreviousSegmentOfSameCategory() = runTest {
        repo.startTracking(now = 0)
        repo.allocate(0, listOf(Allocation(work, 30)))
        repo.allocate(30 * m, listOf(Allocation(work, 20)))
        val all = db.segmentDao().all()
        assertEquals(1, all.size)
        assertEquals(0L, all[0].startAt)
        assertEquals(50 * m, all[0].endAt)
        assertContiguous(all, 0, 50 * m)
    }

    @Test fun allocateKeepsSeparateSegmentForDifferentCategory() = runTest {
        repo.startTracking(now = 0)
        repo.allocate(0, listOf(Allocation(work, 30)))
        repo.allocate(30 * m, listOf(Allocation(rest, 20)))
        assertEquals(listOf(work, rest), db.segmentDao().all().map { it.categoryId })
    }

    @Test fun splitDoesNotCoalesceBack() = runTest {
        val segs = threeSegments()
        assertEquals(EditResult.Done, repo.split(segs[0].id, 10 * m))
        assertEquals(4, db.segmentDao().all().size)
    }

    @Test fun coalesceAllMergesExistingRuns() = runTest {
        repo.startTracking(now = 0)
        // «старые» данные пишем напрямую через DAO, минуя склейку
        db.segmentDao().insertAll(
            listOf(
                Segment(startAt = 0, endAt = 10 * m, categoryId = work),
                Segment(startAt = 10 * m, endAt = 20 * m, categoryId = work),
                Segment(startAt = 20 * m, endAt = 30 * m, categoryId = rest),
                Segment(startAt = 30 * m, endAt = 40 * m, categoryId = rest),
                Segment(startAt = 40 * m, endAt = 50 * m, categoryId = work),
            ),
        )
        assertEquals(2, repo.coalesceAll()) // 5 записей → 3 итоговых = 2 склейки
        val all = db.segmentDao().all()
        assertEquals(listOf(work, rest, work), all.map { it.categoryId })
        assertEquals(listOf(0L, 20 * m, 40 * m), all.map { it.startAt })
        assertContiguous(all, 0, 50 * m)
        assertEquals(0, repo.coalesceAll()) // идемпотентно
    }

    @Test fun changeCategoryOfMissingSegmentIsRejected() = runTest {
        threeSegments()
        assertEquals(EditResult.Rejected, repo.changeCategory(999, work))
    }

    @Test fun splitCreatesTwoContiguousPartsWithSameCategory() = runTest {
        val segs = threeSegments()
        assertEquals(EditResult.Done, repo.split(segs[0].id, 10 * m))
        val after = db.segmentDao().all()
        assertEquals(4, after.size)
        assertEquals(listOf(0L, 10 * m, 30 * m, 45 * m), after.map { it.startAt })
        assertEquals(work, after[1].categoryId)
        assertContiguous(after, 0, 75 * m)
    }

    @Test fun splitOutsideOrOnBoundaryIsRejected() = runTest {
        val segs = threeSegments()
        assertEquals(EditResult.Rejected, repo.split(segs[0].id, 0))
        assertEquals(EditResult.Rejected, repo.split(segs[0].id, 30 * m))
        assertEquals(EditResult.Rejected, repo.split(segs[0].id, 31 * m))
        assertEquals(3, db.segmentDao().all().size)
    }

    @Test fun moveBoundaryShiftsBothNeighbours() = runTest {
        val segs = threeSegments()
        assertEquals(EditResult.Done, repo.moveBoundary(segs[0].id, segs[1].id, 20 * m))
        val after = db.segmentDao().all()
        assertEquals(20 * m, after[0].endAt)
        assertEquals(20 * m, after[1].startAt)
        assertContiguous(after, 0, 75 * m)
    }

    @Test fun moveBoundaryRejectsNonAdjacentAndOutOfRange() = runTest {
        val segs = threeSegments()
        assertEquals(EditResult.Rejected, repo.moveBoundary(segs[0].id, segs[2].id, 20 * m)) // не смежные
        assertEquals(EditResult.Rejected, repo.moveBoundary(segs[0].id, segs[1].id, 0))      // = start левого
        assertEquals(EditResult.Rejected, repo.moveBoundary(segs[0].id, segs[1].id, 45 * m)) // = end правого
        assertEquals(EditResult.Rejected, repo.moveBoundary(segs[1].id, segs[0].id, 20 * m)) // перепутан порядок
        assertContiguous(db.segmentDao().all(), 0, 75 * m)
        assertEquals(30 * m, db.segmentDao().all()[0].endAt)
    }

    @Test fun mergeAbsorbsNeighbourAndKeepsOwnCategory() = runTest {
        val segs = threeSegments()
        assertEquals(EditResult.Done, repo.merge(keepId = segs[1].id, otherId = segs[2].id))
        val after = db.segmentDao().all()
        assertEquals(2, after.size)
        assertEquals(rest, after[1].categoryId)
        assertEquals(30 * m, after[1].startAt)
        assertEquals(75 * m, after[1].endAt)
        assertContiguous(after, 0, 75 * m)
    }

    @Test fun mergeWithPreviousWorksToo() = runTest {
        val segs = threeSegments()
        assertEquals(EditResult.Done, repo.merge(keepId = segs[1].id, otherId = segs[0].id))
        val after = db.segmentDao().all()
        assertEquals(listOf(rest, work), after.map { it.categoryId })
        assertEquals(0L, after[0].startAt)
        assertEquals(45 * m, after[0].endAt)
        assertContiguous(after, 0, 75 * m)
    }

    @Test fun mergeRejectsNonAdjacent() = runTest {
        val segs = threeSegments()
        assertEquals(EditResult.Rejected, repo.merge(segs[0].id, segs[2].id))
        assertEquals(3, db.segmentDao().all().size)
    }

    @Test fun editsNeverMoveAccountedUntil() = runTest {
        val segs = threeSegments()
        assertEquals(EditResult.Done, repo.merge(keepId = segs[2].id, otherId = segs[1].id))
        assertEquals(EditResult.Done, repo.split(segs[0].id, 10 * m))
        assertEquals(75 * m, repo.trackingState()!!.accountedUntil)
        assertEquals(AllocateResult.Saved, repo.allocate(75 * m, listOf(Allocation(work, 5))))
        assertContiguous(db.segmentDao().all(), 0, 80 * m)
    }

    @Test fun neighboursAreAdjacentOnly() = runTest {
        val segs = threeSegments()
        val (p0, n0) = repo.neighbours(segs[0])
        assertNull(p0)
        assertEquals(segs[1].id, n0!!.id)
        val (p2, n2) = repo.neighbours(segs[2])
        assertEquals(segs[1].id, p2!!.id)
        assertNull(n2)
    }
}
