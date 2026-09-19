package io.ferventio.app.moderation

/**
 * Android compatibility aliases for the shared multiplatform nuke executor.
 *
 * The implementation and behavioral contract now live in `shared.moderation`, so Android and
 * shared Compose surfaces cannot drift in execution limits, pacing, cancellation, or results.
 */
typealias NukeExecutionPolicy = io.ferventio.shared.moderation.NukeExecutionPolicy
typealias NukeTargetFailure = io.ferventio.shared.moderation.NukeTargetFailure
typealias NukeExecutionResult = io.ferventio.shared.moderation.NukeExecutionResult
typealias NukeModerationAction = io.ferventio.shared.moderation.NukeModerationAction
typealias NukeExecutionCoordinator = io.ferventio.shared.moderation.NukeExecutionCoordinator
