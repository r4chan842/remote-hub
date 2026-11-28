package app.remotehub.app

import app.remotehub.domain.ProvisionStep
import app.remotehub.domain.StepStatus

/**
 * The lifecycle of creating a server, as a value that outlives any screen.
 *
 * The old model was a single `provisioning: Boolean` plus a step list, both written
 * directly by the UI-facing controller. That could not express "the repository exists
 * but the workflow failed", could not be restored after process death, and gave the
 * Progress screen nothing to render when the user navigated away and back.
 *
 * Phase names map onto the real workflow stages rather than inventing a vocabulary:
 * the backend runs account → repo → secrets → dispatch → machine → network → ready.
 */
enum class ProvisionPhase {
    Idle,
    Validating,
    Initializing,
    CreatingRepository,
    RunningWorkflow,
    Connecting,
    Completed,
    Failed,
    Cancelled;

    val isActive: Boolean
        get() = this == Validating || this == Initializing || this == CreatingRepository ||
            this == RunningWorkflow || this == Connecting

    val isTerminal: Boolean
        get() = this == Completed || this == Failed || this == Cancelled

    companion object {
        /**
         * Which phase a workflow step belongs to. Anything unknown keeps the current
         * phase rather than guessing, so a new backend step cannot silently rewind
         * the progress indicator.
         */
        fun forStep(stepId: String, current: ProvisionPhase): ProvisionPhase = when (stepId) {
            "account" -> Initializing
            "repo", "secrets" -> CreatingRepository
            "dispatch", "machine" -> RunningWorkflow
            "network", "ready" -> Connecting
            else -> current
        }
    }
}

/**
 * One provisioning attempt.
 *
 * Immutable so it can live inside [AppState], be persisted as a string, and be compared
 * in tests. The controller owns the only instance; screens read it and never write it.
 */
data class ProvisionOperation(
    val phase: ProvisionPhase = ProvisionPhase.Idle,
    val serverName: String = "",
    val durationMinutes: Int = 0,
    val steps: List<ProvisionStep> = emptyList(),
    val log: List<String> = emptyList(),
    val startedAtEpochMs: Long = 0L,
    val finishedAtEpochMs: Long? = null,
    val errorMessage: String? = null,
    val errorCode: String? = null,
    val serverId: String? = null,
    val runNumber: Int? = null,
    val attempt: Int = 0,
) {
    val isActive: Boolean get() = phase.isActive
    val isTerminal: Boolean get() = phase.isTerminal
    val hasRun: Boolean get() = phase != ProvisionPhase.Idle

    /**
     * Fraction of the seven stages that finished. Running counts as half a stage so the
     * bar moves while a long stage is in flight instead of sitting still.
     */
    val progressFraction: Float
        get() {
            if (steps.isEmpty()) return if (isActive) 0.02f else 0f
            val total = steps.size.toFloat()
            val done = steps.count { it.status == StepStatus.Done || it.status == StepStatus.Skipped }
            val running = steps.count { it.status == StepStatus.Running }
            return ((done + running * 0.5f) / total).coerceIn(0f, 1f)
        }

    val failedStep: ProvisionStep? get() = steps.firstOrNull { it.status == StepStatus.Failed }

    val runningStep: ProvisionStep? get() = steps.firstOrNull { it.status == StepStatus.Running }

    /** Begin an attempt. Refuses to start while one is already in flight. */
    fun start(name: String, minutes: Int, nowEpochMs: Long): ProvisionOperation? {
        if (isActive) return null
        return copy(
            phase = ProvisionPhase.Validating,
            serverName = name,
            durationMinutes = minutes,
            steps = emptyList(),
            log = emptyList(),
            startedAtEpochMs = nowEpochMs,
            finishedAtEpochMs = null,
            errorMessage = null,
            errorCode = null,
            serverId = null,
            runNumber = null,
            attempt = attempt + 1,
        )
    }

    fun withSteps(next: List<ProvisionStep>): ProvisionOperation {
        if (!isActive) return this
        var nextPhase = phase
        // Walk in order so the furthest reported stage wins.
        next.forEach { step ->
            if (step.status == StepStatus.Running || step.status == StepStatus.Done) {
                val candidate = ProvisionPhase.forStep(step.id, nextPhase)
                if (candidate.ordinal > nextPhase.ordinal) nextPhase = candidate
            }
        }
        return copy(steps = next, phase = nextPhase)
    }

    fun withLog(line: String): ProvisionOperation =
        if (!isActive) this else copy(log = (log + line).takeLast(MAX_LOG))

    fun complete(id: String, run: Int?, nowEpochMs: Long): ProvisionOperation =
        copy(
            phase = ProvisionPhase.Completed,
            serverId = id,
            runNumber = run,
            finishedAtEpochMs = nowEpochMs,
            steps = steps.map {
                if (it.status == StepStatus.Done || it.status == StepStatus.Skipped) it
                else it.copy(status = StepStatus.Done)
            },
        )

    fun fail(message: String, code: String?, nowEpochMs: Long): ProvisionOperation =
        copy(
            phase = ProvisionPhase.Failed,
            errorMessage = message,
            errorCode = code,
            finishedAtEpochMs = nowEpochMs,
            steps = steps.map {
                if (it.status == StepStatus.Running) it.copy(status = StepStatus.Failed) else it
            },
        )

    fun cancel(nowEpochMs: Long): ProvisionOperation =
        copy(
            phase = ProvisionPhase.Cancelled,
            finishedAtEpochMs = nowEpochMs,
            steps = steps.map {
                if (it.status == StepStatus.Running) it.copy(status = StepStatus.Pending) else it
            },
        )

    /** Clear a finished attempt so the Create form is usable again. */
    fun acknowledge(): ProvisionOperation =
        if (isActive) this else ProvisionOperation(attempt = attempt)

    companion object {
        const val MAX_LOG = 40
    }
}
