package dev.boar.checktime.domain

data class Allocation(val categoryId: Long, val minutes: Int)

/**
 * Неизменяемый черновик распределения [totalMinutes] минут по категориям.
 * [order] — id категорий в порядке показа; сегменты пишутся в этом порядке.
 */
data class AllocationDraft(
    val totalMinutes: Int,
    val stepMinutes: Int,
    val order: List<Long>,
    val minutes: Map<Long, Int> = emptyMap(),
) {
    val allocated: Int get() = minutes.values.sum()
    val remaining: Int get() = totalMinutes - allocated
    val isComplete: Boolean get() = totalMinutes > 0 && remaining == 0

    fun of(categoryId: Long): Int = minutes[categoryId] ?: 0

    fun increment(categoryId: Long) = set(categoryId, of(categoryId) + stepMinutes)
    fun decrement(categoryId: Long) = set(categoryId, of(categoryId) - stepMinutes)
    fun assignRest(categoryId: Long) = set(categoryId, of(categoryId) + remaining)
    fun clear(categoryId: Long) = set(categoryId, 0)

    /** Значение зажимается в [0, текущее + remaining], чтобы сумма не превысила total. */
    fun set(categoryId: Long, value: Int): AllocationDraft {
        val max = of(categoryId) + remaining
        return copy(minutes = minutes + (categoryId to value.coerceIn(0, max)))
    }

    fun allocations(): List<Allocation> =
        order.filter { of(it) > 0 }.map { Allocation(it, of(it)) }
}
