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

sealed interface EditResult {
    data object Done : EditResult
    /** Запись исчезла, соседи не смежны или время вне допустимого диапазона. */
    data object Rejected : EditResult
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

    /** Начинает учёт; граница учёта всегда на минутной сетке — [now] выравнивается вниз до минуты. */
    suspend fun startTracking(now: Long) = db.withTransaction {
        if (stateDao.get() == null) {
            val start = now - now % TimeMath.MINUTE_MS
            stateDao.upsert(TrackingState(trackingStart = start, accountedUntil = start))
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
            val ids = segmentDao.insertAll(segments)
            stateDao.upsert(state.copy(accountedUntil = cursor))
            // Подряд идущие записи одной категории не должны плодить строки в «Дне»:
            // склеиваем первую записанную с предыдущей, если категория совпала.
            // Внутри одного распределения категории уникальны (AllocationDraft.allocations()),
            // поэтому достаточно стыка со старыми данными.
            ids.firstOrNull()?.let { id -> segmentDao.byId(id)?.let { coalesce(it) } }
            AllocateResult.Saved
        }

    fun observeSegments(from: Long, to: Long): Flow<List<Segment>> = segmentDao.observeOverlapping(from, to)

    suspend fun segment(id: Long): Segment? = segmentDao.byId(id)

    /** Смежные соседи (предыдущий, следующий) или null, если границы не с кем делить. */
    suspend fun neighbours(segment: Segment): Pair<Segment?, Segment?> = db.withTransaction {
        val prev = segmentDao.previousOf(segment.startAt)?.takeIf { it.endAt == segment.startAt }
        val next = segmentDao.nextOf(segment.endAt)?.takeIf { it.startAt == segment.endAt }
        prev to next
    }

    suspend fun changeCategory(segmentId: Long, categoryId: Long): EditResult = db.withTransaction {
        val s = segmentDao.byId(segmentId) ?: return@withTransaction EditResult.Rejected
        val updated = s.copy(categoryId = categoryId)
        segmentDao.update(updated)
        coalesce(updated)
        EditResult.Done
    }

    /** Режет [segmentId] в точке [at] (строго внутри); вторая часть наследует категорию. */
    suspend fun split(segmentId: Long, at: Long): EditResult = db.withTransaction {
        val s = segmentDao.byId(segmentId) ?: return@withTransaction EditResult.Rejected
        if (at <= s.startAt || at >= s.endAt) return@withTransaction EditResult.Rejected
        segmentDao.update(s.copy(endAt = at))
        segmentDao.insertAll(listOf(Segment(startAt = at, endAt = s.endAt, categoryId = s.categoryId)))
        EditResult.Done
    }

    /** Двигает общую границу смежных [leftId] и [rightId] в [at] (строго между их внешними концами). */
    suspend fun moveBoundary(leftId: Long, rightId: Long, at: Long): EditResult = db.withTransaction {
        val l = segmentDao.byId(leftId) ?: return@withTransaction EditResult.Rejected
        val r = segmentDao.byId(rightId) ?: return@withTransaction EditResult.Rejected
        if (l.endAt != r.startAt) return@withTransaction EditResult.Rejected
        if (at <= l.startAt || at >= r.endAt) return@withTransaction EditResult.Rejected
        segmentDao.update(l.copy(endAt = at))
        segmentDao.update(r.copy(startAt = at))
        EditResult.Done
    }

    /** [keepId] поглощает смежный [otherId]; категория keep побеждает. */
    suspend fun merge(keepId: Long, otherId: Long): EditResult = db.withTransaction {
        val k = segmentDao.byId(keepId) ?: return@withTransaction EditResult.Rejected
        val o = segmentDao.byId(otherId) ?: return@withTransaction EditResult.Rejected
        val adjacent = k.endAt == o.startAt || o.endAt == k.startAt
        if (!adjacent) return@withTransaction EditResult.Rejected
        segmentDao.delete(o)
        segmentDao.update(k.copy(startAt = minOf(k.startAt, o.startAt), endAt = maxOf(k.endAt, o.endAt)))
        EditResult.Done
    }

    /**
     * Склеивает [segment] со смежными соседями той же категории.
     * Вызывать только внутри транзакции. Возвращает итоговую запись.
     */
    private suspend fun coalesce(segment: Segment): Segment {
        var s = segment
        segmentDao.previousOf(s.startAt)
            ?.takeIf { it.endAt == s.startAt && it.categoryId == s.categoryId }
            ?.let { prev ->
                segmentDao.delete(prev)
                s = s.copy(startAt = prev.startAt)
                segmentDao.update(s)
            }
        segmentDao.nextOf(s.endAt)
            ?.takeIf { it.startAt == s.endAt && it.categoryId == s.categoryId }
            ?.let { next ->
                segmentDao.delete(next)
                s = s.copy(endAt = next.endAt)
                segmentDao.update(s)
            }
        return s
    }

    /**
     * Склеивает все подряд идущие записи одной категории (накопились до введения склейки).
     * Идемпотентно; вызывается один раз при старте приложения. Возвращает число склеенных записей.
     */
    suspend fun coalesceAll(): Int = db.withTransaction {
        val all = segmentDao.all()
        var merged = 0
        var current: Segment? = null
        for (s in all) {
            val c = current
            if (c != null && c.endAt == s.startAt && c.categoryId == s.categoryId) {
                segmentDao.delete(s)
                current = c.copy(endAt = s.endAt).also { segmentDao.update(it) }
                merged++
            } else {
                current = s
            }
        }
        merged
    }
}
