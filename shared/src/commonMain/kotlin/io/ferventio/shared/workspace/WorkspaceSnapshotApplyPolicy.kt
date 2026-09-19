package io.ferventio.shared.workspace

internal object WorkspaceSnapshotApplyPolicy {
    fun canApply(candidateRevision: Long, appliedRevision: Long): Boolean {
        require(candidateRevision >= 0L) { "Workspace snapshot revision must not be negative" }
        require(appliedRevision >= 0L) { "Applied workspace revision must not be negative" }
        return candidateRevision >= appliedRevision
    }
}
