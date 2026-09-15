package dev.boar.checktime.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllocationDraftTest {
    private val draft = AllocationDraft(totalMinutes = 47, stepMinutes = 5, order = listOf(10L, 20L, 30L))

    @Test fun startsEmpty() {
        assertEquals(0, draft.allocated)
        assertEquals(47, draft.remaining)
        assertFalse(draft.isComplete)
    }

    @Test fun incrementAddsStep() {
        val d = draft.increment(10L).increment(10L)
        assertEquals(10, d.of(10L))
        assertEquals(37, d.remaining)
    }

    @Test fun incrementNeverExceedsRemaining() {
        var d = draft
        repeat(20) { d = d.increment(10L) }
        assertEquals(47, d.of(10L))
        assertEquals(0, d.remaining)
        assertTrue(d.isComplete)
    }

    @Test fun decrementClampsAtZero() {
        val d = draft.increment(10L).decrement(10L).decrement(10L)
        assertEquals(0, d.of(10L))
    }

    @Test fun decrementBelowStepGoesToZero() {
        val d = draft.assignRest(10L).decrement(10L) // 47 -> 42
        assertEquals(42, d.of(10L))
        val e = AllocationDraft(totalMinutes = 3, stepMinutes = 5, order = listOf(10L)).assignRest(10L).decrement(10L)
        assertEquals(0, e.of(10L))
    }

    @Test fun assignRestFillsRemainder() {
        val d = draft.increment(10L).assignRest(20L)
        assertEquals(5, d.of(10L))
        assertEquals(42, d.of(20L))
        assertTrue(d.isComplete)
    }

    @Test fun clearResetsCategory() {
        val d = draft.assignRest(10L).clear(10L)
        assertEquals(47, d.remaining)
    }

    @Test fun allocationsFollowOrderAndSkipZeros() {
        val d = draft.increment(30L).increment(10L).assignRest(30L)
        assertEquals(listOf(Allocation(10L, 5), Allocation(30L, 42)), d.allocations())
    }

    @Test fun zeroTotalIsNeverComplete() {
        assertFalse(AllocationDraft(totalMinutes = 0, stepMinutes = 5, order = listOf(1L)).isComplete)
    }
}
