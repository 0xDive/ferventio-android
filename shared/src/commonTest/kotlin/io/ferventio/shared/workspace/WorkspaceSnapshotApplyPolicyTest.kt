package io.ferventio.shared.workspace

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorkspaceSnapshotApplyPolicyTest {
    @Test
    fun staleRevisionsAreRejectedWhileEqualAndNewerRevisionsAreAccepted() {
        assertFalse(WorkspaceSnapshotApplyPolicy.canApply(candidateRevision = 8L, appliedRevision = 9L))
        assertTrue(WorkspaceSnapshotApplyPolicy.canApply(candidateRevision = 9L, appliedRevision = 9L))
        assertTrue(WorkspaceSnapshotApplyPolicy.canApply(candidateRevision = 10L, appliedRevision = 9L))
    }

    @Test
    fun negativeRevisionsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            WorkspaceSnapshotApplyPolicy.canApply(candidateRevision = -1L, appliedRevision = 0L)
        }
        assertFailsWith<IllegalArgumentException> {
            WorkspaceSnapshotApplyPolicy.canApply(candidateRevision = 0L, appliedRevision = -1L)
        }
    }
}
