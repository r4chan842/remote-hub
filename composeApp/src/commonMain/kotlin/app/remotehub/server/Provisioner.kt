package app.remotehub.server

import app.remotehub.data.SecretBox
import app.remotehub.data.randomPassword
import app.remotehub.data.randomSlug
import app.remotehub.domain.ProvisionStep
import app.remotehub.domain.StepStatus
import app.remotehub.github.GitHubClient
import app.remotehub.github.GitHubException
import app.remotehub.github.WorkflowJob
import app.remotehub.github.WorkflowRun
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

data class ProvisionRequest(
    val serverName: String,
    val repositoryName: String,
    val tailscaleAuthKey: String,
    val durationMinutes: Int = SessionWorkflow.DEFAULT_MINUTES,
    val warningMinutes: Int = 15,
    val publicRepository: Boolean = true,
)

data class ProvisionOutcome(
    val runId: Long,
    val runNumber: Int,
    val runUrl: String,
    val username: String,
    val password: String,
    val sessionId: String,
    val startedAtEpochMs: Long,
    val durationMinutes: Int,
)

sealed interface ProvisionEvent {
    data class Steps(val steps: List<ProvisionStep>) : ProvisionEvent
    data class Log(val line: String) : ProvisionEvent
    data class Ready(val outcome: ProvisionOutcome) : ProvisionEvent
    data class Failed(val stepId: String, val message: String) : ProvisionEvent
}

/**
 * Drives the whole setup with the user's own token: validate, create the repository,
 * seal the secrets, commit the workflow, dispatch it and follow the job until the
 * Windows desktop has joined the private network.
 *
 * The password is generated here and sealed with the repository public key. It is
 * never written to the workflow log, so a public repository does not expose it.
 */
class Provisioner(
    private val client: GitHubClient,
    private val secretBox: SecretBox,
    private val owner: String,
    private val workflowTemplate: String,
    private val clock: Clock = Clock.System,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The seven steps the user sees, in order. */
    private val stepIds = listOf("account", "repo", "secrets", "dispatch", "machine", "network", "ready")
    private val statuses = LinkedHashMap<String, StepStatus>()
    private val details = LinkedHashMap<String, String>()
    private val elapsed = LinkedHashMap<String, Long>()
    private var networkStart: Long? = null

    init {
        stepIds.forEach { statuses[it] = StepStatus.Pending }
    }

    private fun snapshot(): List<ProvisionStep> = stepIds.map {
        ProvisionStep(it, statuses.getValue(it), details[it], elapsed[it])
    }

    private fun emit(onEvent: (ProvisionEvent) -> Unit) = onEvent(ProvisionEvent.Steps(snapshot()))

    private fun mark(
        id: String,
        status: StepStatus,
        detail: String? = null,
        startedAt: Long? = null,
        onEvent: (ProvisionEvent) -> Unit,
    ) {
        statuses[id] = status
        if (detail != null) details[id] = detail
        if (startedAt != null && status != StepStatus.Running) {
            elapsed[id] = clock.now().toEpochMilliseconds() - startedAt
        }
        emit(onEvent)
    }

    suspend fun run(request: ProvisionRequest, onEvent: (ProvisionEvent) -> Unit): ProvisionOutcome {
        var current = "account"
        try {
            var t = clock.now().toEpochMilliseconds()
            mark(current, StepStatus.Running, owner, t, onEvent)
            val validation = client.validate()
            if (validation.missing.isNotEmpty()) {
                throw GitHubException(403, "missing scopes: ${validation.missing.joinToString(", ")}")
            }
            mark(current, StepStatus.Done, "@${validation.user.login}", t, onEvent)

            current = "repo"
            t = clock.now().toEpochMilliseconds()
            mark(current, StepStatus.Running, request.repositoryName, t, onEvent)
            val repo = client.repository(owner, request.repositoryName)
                ?: client.createRepository(
                    request.repositoryName,
                    "RemoteHub temporary Windows sessions",
                    private = !request.publicRepository,
                )
            mark(current, StepStatus.Done, repo.fullName, t, onEvent)

            current = "secrets"
            t = clock.now().toEpochMilliseconds()
            mark(current, StepStatus.Running, null, t, onEvent)
            val sessionId = newSessionId(request.serverName)
            val username = "rhuser"
            val password = randomPassword(24)
            val minutes = request.durationMinutes
                .coerceIn(SessionWorkflow.MIN_MINUTES, SessionWorkflow.MAX_MINUTES)

            val publicKey = client.publicKey(owner, request.repositoryName)
            val secrets = linkedMapOf(
                "TAILSCALE_AUTHKEY" to request.tailscaleAuthKey,
                "RDP_USERNAME" to username,
                "RDP_PASSWORD" to password,
            )
            secrets.forEach { (name, value) ->
                val sealed = secretBox.seal(value, publicKey.key)
                client.putSecret(owner, request.repositoryName, name, sealed, publicKey.keyId)
                onEvent(ProvisionEvent.Log("secret $name sealed"))
            }
            client.putContent(
                owner,
                request.repositoryName,
                SessionWorkflow.PATH,
                base64(workflowTemplate.encodeToByteArray()),
                "Configure the Windows session workflow",
            )
            mark(current, StepStatus.Done, "${secrets.size} secrets", t, onEvent)

            current = "dispatch"
            t = clock.now().toEpochMilliseconds()
            mark(current, StepStatus.Running, SessionWorkflow.FILE_NAME, t, onEvent)
            val before = client.latestDispatchRun(owner, request.repositoryName)?.id ?: 0L
            client.dispatch(
                owner,
                request.repositoryName,
                SessionWorkflow.FILE_NAME,
                repo.defaultBranch,
                mapOf("session_id" to sessionId, "minutes" to minutes.toString()),
            )
            val run = awaitNewRun(owner, request.repositoryName, before)
            mark(current, StepStatus.Done, "#${run.runNumber}", t, onEvent)

            current = "machine"
            t = clock.now().toEpochMilliseconds()
            mark(current, StepStatus.Running, "run ${run.id}", t, onEvent)
            val startedAt = clock.now().toEpochMilliseconds()
            awaitSessionReady(owner, request.repositoryName, run.id, onEvent)
            mark(current, StepStatus.Done, null, t, onEvent)

            current = "network"
            mark(current, StepStatus.Done, "rh-$sessionId", networkStart ?: t, onEvent)

            current = "ready"
            mark(current, StepStatus.Done, sessionId, clock.now().toEpochMilliseconds(), onEvent)

            val outcome = ProvisionOutcome(
                runId = run.id,
                runNumber = run.runNumber,
                runUrl = run.htmlUrl,
                username = username,
                password = password,
                sessionId = sessionId,
                startedAtEpochMs = startedAt,
                durationMinutes = minutes,
            )
            onEvent(ProvisionEvent.Ready(outcome))
            return outcome
        } catch (error: GitHubException) {
            statuses[current] = StepStatus.Failed
            details[current] = error.message
            emit(onEvent)
            onEvent(ProvisionEvent.Failed(current, error.message))
            throw error
        } catch (error: Throwable) {
            statuses[current] = StepStatus.Failed
            val message = error.message ?: error::class.simpleName.orEmpty()
            details[current] = message
            emit(onEvent)
            onEvent(ProvisionEvent.Failed(current, message))
            throw error
        }
    }

    private suspend fun awaitNewRun(owner: String, repo: String, previousId: Long): WorkflowRun {
        repeat(40) {
            delay(3000)
            val latest = client.latestDispatchRun(owner, repo)
            if (latest != null && latest.id > previousId) return latest
        }
        throw GitHubException(408, "workflow run did not appear")
    }

    /**
     * Follows the job until the endpoint has been published. The workflow names its
     * steps "1." .. "7."; the machine step covers 1..4 and the network step 5..6.
     * The job then stays alive for the whole session, so reaching step 7 means ready.
     */
    private suspend fun awaitSessionReady(
        owner: String,
        repo: String,
        runId: Long,
        onEvent: (ProvisionEvent) -> Unit,
    ) {
        var lastSignature = ""
        repeat(180) {
            val run = client.run(owner, repo, runId)
            val jobs = client.jobs(owner, repo, runId)
            val signature = jobs.joinToString("|") { job ->
                job.name + job.status + job.steps.joinToString(",") { it.name + it.status }
            }
            if (signature != lastSignature) {
                lastSignature = signature
                applyJobProgress(jobs, onEvent)
            }
            if (statuses["network"] == StepStatus.Done) return
            if (run.status == "completed" && run.conclusion != "success") {
                throw GitHubException(409, "workflow concluded ${run.conclusion}")
            }
            delay(5000)
        }
        throw GitHubException(408, "the session did not become ready in time")
    }

    private fun applyJobProgress(jobs: List<WorkflowJob>, onEvent: (ProvisionEvent) -> Unit) {
        val steps = jobs.flatMap { it.steps }
        fun stateOf(prefix: String): StepStatus? {
            val match = steps.firstOrNull { it.name.trim().startsWith(prefix) } ?: return null
            return when {
                match.conclusion == "success" -> StepStatus.Done
                match.conclusion == "failure" || match.conclusion == "cancelled" -> StepStatus.Failed
                match.status == "in_progress" -> StepStatus.Running
                match.status == "completed" -> StepStatus.Done
                else -> StepStatus.Pending
            }
        }

        // Steps 1..4 prepare the machine; 5..6 join the tailnet and publish the address.
        val machineStates = listOf("1.", "2.", "3.", "4.").mapNotNull { stateOf(it) }
        val networkStates = listOf("5.", "6.").mapNotNull { stateOf(it) }

        if (machineStates.isNotEmpty()) {
            statuses["machine"] = when {
                machineStates.any { it == StepStatus.Failed } -> StepStatus.Failed
                machineStates.all { it == StepStatus.Done } -> StepStatus.Done
                machineStates.any { it == StepStatus.Running } -> StepStatus.Running
                else -> StepStatus.Pending
            }
        }
        if (networkStates.isNotEmpty()) {
            if (networkStart == null && networkStates.any { it == StepStatus.Running }) {
                networkStart = clock.now().toEpochMilliseconds()
            }
            statuses["network"] = when {
                networkStates.any { it == StepStatus.Failed } -> StepStatus.Failed
                networkStates.all { it == StepStatus.Done } -> StepStatus.Done
                networkStates.any { it == StepStatus.Running } -> StepStatus.Running
                else -> StepStatus.Pending
            }
        }
        val failed = steps.firstOrNull { it.conclusion == "failure" }
        if (failed != null) details[if (statuses["machine"] == StepStatus.Failed) "machine" else "network"] = failed.name
        steps.filter { it.status == "in_progress" }.forEach {
            onEvent(ProvisionEvent.Log(it.name))
        }
        emit(onEvent)
    }

    private fun newSessionId(name: String): String {
        val slug = name.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
            .trim('-').take(20).ifBlank { "session" }
        val suffix = randomSlug(6)
        return "$slug-$suffix"
    }
}

private val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".encodeToByteArray()

fun base64(bytes: ByteArray): String {
    val out = StringBuilder((bytes.size + 2) / 3 * 4)
    var i = 0
    while (i < bytes.size) {
        val b0 = bytes[i].toInt() and 0xFF
        val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
        val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
        out.append(BASE64_ALPHABET[b0 shr 2].toInt().toChar())
        out.append(BASE64_ALPHABET[((b0 and 0x03) shl 4) or (b1 shr 4)].toInt().toChar())
        out.append(if (i + 1 < bytes.size) BASE64_ALPHABET[((b1 and 0x0F) shl 2) or (b2 shr 6)].toInt().toChar() else '=')
        out.append(if (i + 2 < bytes.size) BASE64_ALPHABET[b2 and 0x3F].toInt().toChar() else '=')
        i += 3
    }
    return out.toString()
}
