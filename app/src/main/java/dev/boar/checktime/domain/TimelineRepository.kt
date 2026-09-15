package dev.boar.checktime.domain

import androidx.room.withTransaction
import dev.boar.checktime.data.AppDatabase
import dev.boar.checktime.data.Segment
import dev.boar.checktime.data.TrackingState
import kotlinx.coroutines.flow.Flow

sealed interface AllocateResult {
    data object Saved : AllocateResult
    /** accountedUntil изменился с момента открытия экрана — хвост надо перечитать. */
    data object Stale : AllocateResult
}

/**
 * Единственное место, которое пишет сегменты и двигает accountedUntil.
 * Инвариант: сегменты не пересекаются и покрывают [trackingStart, accountedUntil) без дыр.
 */
class TimelineRepository(private val db: AppDatabase) {
    private val stateDao = db.trackingStateDao()
    private val segmentDao = db.segmentDao()

    fun observeTrackingState(): Flow<TrackingState?> = stateDao.observe()

    suspend fun trackingState(): TrackingState? = stateDao.get()

    suspend fun startTracking(now: Long) = db.withTransaction {
        if (stateDao.get() == null) {
            stateDao.upsert(TrackingState(trackingStart = now, accountedUntil = now))
        }
    }

    /**
     * Записывает [allocations] подряд, начиная с accountedUntil, и сдвигает границу.
     * Отклоняется, если граница уже не равна [expectedAccountedUntil].
     */
    suspend fun allocate(expectedAccountedUntil: Long, allocations: List<Allocation>): AllocateResult =
        db.withTransaction {
            val state = stateDao.get() ?: return@withTransaction AllocateResult.Stale
            if (state.accountedUntil != expectedAccountedUntil) return@withTransaction AllocateResult.Stale
            require(allocations.all { it.minutes > 0 }) { "allocations must be positive" }

            var cursor = state.accountedUntil
            val segments = allocations.map { a ->
                val end = cursor + a.minutes * TimeMath.MINUTE_MS
                Segment(startAt = cursor, endAt = end, categoryId = a.categoryId).also { cursor = end }
            }
            segmentDao.insertAll(segments)
            stateDao.upsert(state.copy(accountedUntil = cursor))
            AllocateResult.Saved
        }

    fun observeSegments(from: Long, to: Long): Flow<List<Segment>> = segmentDao.observeOverlapping(from, to)
}
