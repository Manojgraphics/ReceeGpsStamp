package com.receegpsstamp.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards [RestoreGuard] — a corrupt/empty cloud doc (parse → emptyList) must NEVER wipe a populated
 * local collection during restore. Empty incoming + non-empty local ⇒ keep local; otherwise cloud wins.
 */
class RestoreGuardTest {

    @Test fun empty_cloud_keeps_populated_local() {
        // The data-loss case: corrupt/empty cloud doc must not blank existing local data.
        assertEquals(listOf("a", "b"), RestoreGuard.keep(emptyList(), listOf("a", "b")))
    }

    @Test fun nonempty_cloud_overwrites_local() {
        assertEquals(listOf("x"), RestoreGuard.keep(listOf("x"), listOf("a", "b")))
    }

    @Test fun nonempty_cloud_into_empty_local() {
        // Fresh device (local empty) — a normal restore brings the cloud data in.
        assertEquals(listOf("x", "y"), RestoreGuard.keep(listOf("x", "y"), emptyList()))
    }

    @Test fun both_empty_stays_empty() {
        assertEquals(emptyList<String>(), RestoreGuard.keep(emptyList<String>(), emptyList<String>()))
    }
}
