package dev.boar.checktime.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppDatabaseTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun groupsAndCategoriesRoundTrip() = runTest {
        val dao = db.categoryDao()
        val gid = dao.insertGroup(Group(name = "Личное", color = 1, sortOrder = 0))
        dao.insertCategory(Category(groupId = gid, name = "Сон", color = 2, sortOrder = 1))
        dao.insertCategory(Category(groupId = gid, name = "Еда", color = 3, sortOrder = 0))

        assertEquals(listOf("Личное"), dao.groups().map { it.name })
        assertEquals(listOf("Еда", "Сон"), dao.categoriesOfGroup(gid).map { it.name })
        assertEquals(2, dao.categoryCount(gid))
    }

    @Test
    fun overlappingSegmentsQuery() = runTest {
        val gid = db.categoryDao().insertGroup(Group(name = "g", color = 0, sortOrder = 0))
        val cid = db.categoryDao().insertCategory(Category(groupId = gid, name = "c", color = 0, sortOrder = 0))
        db.segmentDao().insertAll(
            listOf(
                Segment(startAt = 0, endAt = 100, categoryId = cid),
                Segment(startAt = 100, endAt = 200, categoryId = cid),
                Segment(startAt = 200, endAt = 300, categoryId = cid),
            ),
        )
        val hit = db.segmentDao().overlapping(150, 250)
        assertEquals(listOf(100L, 200L), hit.map { it.startAt })
        assertEquals(3, db.categoryDao().segmentCount(cid))
    }

    @Test
    fun trackingStateIsSingleRow() = runTest {
        val dao = db.trackingStateDao()
        assertNull(dao.get())
        dao.upsert(TrackingState(trackingStart = 10, accountedUntil = 10))
        dao.upsert(TrackingState(trackingStart = 10, accountedUntil = 70))
        assertEquals(70L, dao.get()!!.accountedUntil)
    }
}
