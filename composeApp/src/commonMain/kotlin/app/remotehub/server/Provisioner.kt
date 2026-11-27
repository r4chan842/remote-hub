package app.remotehub.server

import app.remotehub.data.SecretBox
import app.remotehub.data.randomPassword
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
    val providerToken: String,
    val durationMinutes: Int,
    val warningMinutes: Int,
    val desktopWidth: Int,
    val desktopHeight: Int,
    val region: String,
    val machineSize: String,
    val osImage: String,
    val autoBackup: Boolean,
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

class Provisioner(
    private val client: GitHubClient,
    private val secretBox: SecretBox,
    private val owner: String,
    private val workflowTemplate: String,
    private val provider: String,
    private val clock: Clock = Clock.System,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val stepIds = listOf("account", "repo", "secrets", "dispatch", "runner", "join", "handshake", "ready")
    private val statuses = LinkedHashMap<String, StepStatus>()
    private val details = LinkedHashMap<String, String>()
    private val elapsed = LinkedHashMap<String, Long>()

    init {
        stepIds.forEach { statuses[it] = StepStatus.Pending }
    }

    private fun snapshot(): List<ProvisionStep> = stepIds.map {
        ProvisionStep(it, statuses.getValue(it), details[it], elapsed[it])
    }

    private suspend fun emit(onEvent: (ProvisionEvent) -> Unit) = onEvent(ProvisionEvent.Steps(snapshot()))

    private suspend fun mark(
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
            mark(current, StepStatus.Running, "${request.repositoryName}", t, onEvent)
            val repo = client.repository(owner, request.repositoryName)
                ?: client.createRepository(request.repositoryName, "RemoteHub session orchestration")
            mark(current, StepStatus.Done, repo.fullName, t, onEvent)

            current = "secrets"
            t = clock.now().toEpochMilliseconds()
            mark(current, StepStatus.Running, null, t, onEvent)
            val sessionId = newSessionId(request.serverName)
            val password = randomPassword(22)
            val username = "rhuser"
            val publicKey = client.publicKey(owner, request.repositoryName)
            val secrets = linkedMapOf(
                "TS_AUTHKEY" to request.tailscaleAuthKey,
                "RDP_USERNAME" to username,
                "RDP_PASSWORD" to password,
                "SESSION_ID" to sessionId,
                "SESSION_MINUTES" to request.durationMinutes.toString(),
                "SESSION_DESKTOP" to "${request.desktopWidth}x${request.desktopHeight}",
                "SESSION_REGION" to request.region,
                "SESSION_SIZE" to request.machineSize,
                "SESSION_IMAGE" to request.osImage,
            )
            if (request.providerToken.isNotBlank()) {
                secrets["TS_API_TOKEN"] = request.tailscaleApiToken
            }
            secrets.forEach { (name, value) ->
                val sealed = secretBox.seal(value, publicKey.key)
                client.putSecret(owner, request.repositoryName, name, sealed, publicKey.keyId)
                onEvent(ProvisionEvent.Log("secret $name sealed with key ${publicKey.keyId}"))
            }
            client.putContent(
                owner,
                request.repositoryName,
                SessionWorkflow.PATH,
                base64(workflowTemplate.encodeToByteArray()),
                "Configure host orchestration workflow",
            )
            client.putContent(
                owner,
                request.repositoryName,
                SessionWorkflow.HOST_SCRIPT_PATH,
                base64(SessionWorkflow.hostScript.encodeToByteArray()),
                "Add provider orchestration script",
            )
            client.putVariable(owner, request.repositoryName, "REMOTEHUB_PROVIDER", provider)
            mark(current, StepStatus.Done, "${secrets.size} secrets", t, onEvent)

            current = "dispatch"
            t = clock.now().toEpochMilliseconds()
            mark(current, StepStatus.Running, "session.yml", t, onEvent)
            val before = client.latestDispatchRun(owner, request.repositoryName)?.id ?: 0L
            client.dispatch(
                owner,
                request.repositoryName,
                "session.yml",
                repo.defaultBranch,
                mapOf(
                    "session_id" to sessionId,
                    "action" to "provision",
                    "minutes" to request.durationMinutes.toString(),
                ),
            )
            val run = awaitNewRun(owner, request.repositoryName, before)
            mark(current, StepStatus.Done, "#${run.runNumber}", t, onEvent)

            current = "runner"
            t = clock.now().toEpochMilliseconds()
            mark(current, StepStatus.Running, "run ${run.id}", t, onEvent)
            val startedAt = clock.now().toEpochMilliseconds()
            awaitRunnerReady(owner, request.repositoryName, run.id, sessionId, onEvent)
            mark(current, StepStatus.Done, null, t, onEvent)

            current = "join"
            t = clock.now().toEpochMilliseconds()
            mark(current, StepStatus.Running, "cloud-init", t, onEvent)
            mark(current, StepStatus.Done, null, t, onEvent)

            current = "handshake"
            t = clock.now().toEpochMilliseconds()
            mark(current, StepStatus.Running, null, t, onEvent)
            mark(current, StepStatus.Done, null, t, onEvent)

            current = "ready"
            mark(current, StepStatus.Done, sessionId, null, onEvent)

            val outcome = ProvisionOutcome(
                runId = run.id,
                runNumber = run.runNumber,
                runUrl = run.htmlUrl,
                username = username,
                password = password,
                sessionId = sessionId,
                startedAtEpochMs = startedAt,
                durationMinutes = request.durationMinutes,
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

    private suspend fun awaitRunnerReady(
        owner: String,
        repo: String,
        runId: Long,
        sessionId: String,
        onEvent: (ProvisionEvent) -> Unit,
    ) {
        var lastSignature = ""
        repeat(240) {
            val run = client.run(owner, repo, runId)
            val jobs = client.jobs(owner, repo, runId)
            val signature = jobs.joinToString("|") { job ->
                job.name + job.status + job.steps.joinToString(",") { it.name + it.status }
            }
            if (signature != lastSignature) {
                lastSignature = signature
                jobs.forEach { job -> onEvent(ProvisionEvent.Log("${job.name}: ${job.status}")) }
                applyJobProgress(jobs, onEvent)
            }
            if (run.status == "completed") {
                if (run.conclusion != "success") {
                    throw GitHubException(409, "workflow concluded ${run.conclusion}")
                }
                return
            }
            delay(5000)
        }
        throw GitHubException(408, "workflow did not reach a terminal state")
    }

    private suspend fun applyJobProgress(jobs: List<WorkflowJob>, onEvent: (ProvisionEvent) -> Unit) {
        val names = jobs.flatMap { it.steps }.map { it.name.lowercase() }
        fun any(vararg keys: String) = names.any { n -> keys.any { n.contains(it) } }
        if (any("provision host", "join tailnet", "tailscale up")) {
            statuses["join"] = StepStatus.Running
        }
        if (any("upload manifest", "publish endpoint", "redact")) {
            statuses["join"] = StepStatus.Done
            statuses["handshake"] = StepStatus.Running
        }
        if (any("orchestrate provider", "validate request")) {
            statuses["handshake"] = StepStatus.Done
        }
        emit(onEvent)
    }

    private fun newSessionId(name: String): String {
        val slug = name.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
            .trim('-').take(24).ifBlank { "session" }
        val suffix = randomPassword(6).lowercase().filter { it.isLetterOrDigit() }.take(6)
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
        if (i + 1 < bytes.size) {
            out.append(BASE64_ALPHABET[((b1 and 0x0F) shl 2) or (b2 shr 6)].toInt().toChar())
        } else {
            out.append('=')
        }
        if (i + 2 < bytes.size) {
            out.append(BASE64_ALPHABET[b2 and 0x3F].toInt().toChar())
        } else {
            out.append('=')
        }
        i += 3
    }
    return out.toString()
}
