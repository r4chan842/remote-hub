package app.remotehub.app

import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationCompat
import app.remotehub.R
import app.remotehub.RemoteHubApplication
import app.remotehub.backup.BackupCodec
import app.remotehub.data.SecureStore
import app.remotehub.data.SecretBox
import app.remotehub.data.StoreKeys
import app.remotehub.data.randomPassword
import app.remotehub.domain.AuthMethod
import app.remotehub.domain.BackupRecord
import app.remotehub.domain.ConnectionInfo
import app.remotehub.domain.GitHubAccount
import app.remotehub.domain.NetworkState
import app.remotehub.domain.OperationKind
import app.remotehub.domain.OperationRecord
import app.remotehub.domain.OperationResult
import app.remotehub.domain.ProvisionStep
import app.remotehub.domain.ServerSession
import app.remotehub.domain.ServerSpec
import app.remotehub.domain.ServerState
import app.remotehub.domain.StepStatus
import app.remotehub.domain.TailnetStatus
import app.remotehub.github.GitHubClient
import app.remotehub.github.StoredServer
import app.remotehub.net.TailscaleNode
import app.remotehub.rdp.RdpSession
import app.remotehub.server.ProvisionEvent
import app.remotehub.server.ProvisionRequest
import app.remotehub.server.Provisioner
import app.remotehub.server.SessionWorkflow
import app.remotehub.server.base64
import app.remotehub.ui.RemoteMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Owns the whole linear flow: connect GitHub with a personal token, join the private
 * network, create the repository, dispatch the Windows workflow, follow it live, then
 * hand the session to the in-app remote desktop.
 */
class SessionController(
    private val context: Context,
    private val store: SecureStore,
    private val secretBox: SecretBox,
    private val scope: CoroutineScope,
) {
    var onState: (AppState) -> Unit = {}
    var onEffect: (SideEffect) -> Unit = {}

    private var state = AppState()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private var github: GitHubClient? = null
    private val node = TailscaleNode(context)
    private val rdpSessions = mutableMapOf<String, RdpSession>()
    private val metricsCache = mutableMapOf<String, RemoteMetrics>()
    private var provisionJob: Job? = null
    private var watchJob: Job? = null
    private var notificationsAllowed = false
    private val alerted = mutableSetOf<String>()
    private var pendingLoginUrl: String? = null

    private fun publish(next: AppState) {
        state = next
        onState(next)
    }

    // ---------------------------------------------------------------- restore

    fun restore() {
        val token = store.get(StoreKeys.GITHUB_TOKEN)
        val login = store.get(StoreKeys.GITHUB_LOGIN)
        var next = state.copy(
            localeTag = store.get(StoreKeys.LOCALE) ?: "fa",
            themeMode = store.get(StoreKeys.THEME)?.toIntOrNull() ?: 0,
            authKeyInput = store.get(StoreKeys.TS_AUTH_KEY) ?: "",
            repositoryName = store.get(REPO_KEY) ?: "remote-hub-sessions",
            warningMinutes = store.get(StoreKeys.WARNING_MINUTES)?.toIntOrNull() ?: 15,
            expiryAlertEnabled = store.get(ALERT_KEY) != "false",
        )
        if (token != null && login != null) {
            github = GitHubClient(token)
            next = next.copy(
                account = GitHubAccount(
                    login = login,
                    displayName = login,
                    method = AuthMethod.PersonalAccessToken,
                    grantedScopes = store.get(StoreKeys.GITHUB_SCOPES)?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                    missingScopes = emptyList(),
                    tokenExpiresLabel = "",
                ),
            )
        }
        store.get(StoreKeys.SERVERS)?.let { raw ->
            runCatching {
                val list = json.decodeFromString<List<StoredServer>>(raw)
                next = next.copy(servers = list.map { it.toSession() })
            }
        }
        publish(next)
        if (token != null) refreshAccount()
        val key = store.get(StoreKeys.TS_AUTH_KEY)
        if (!key.isNullOrBlank()) connectNetwork(key, silent = true)
        next.servers.filter { it.state == ServerState.Provisioning }.forEach { watchRun(it.id) }
    }

    private fun persistServers() {
        val stored = state.servers.map {
            StoredServer(
                id = it.id,
                name = it.name,
                repository = it.repositoryFullName,
                runId = it.runId,
                runNumber = it.workflowRunNumber,
                runUrl = it.workflowRunUrl,
                startedAtEpochMs = it.startedAtEpochMs,
                durationMinutes = it.durationMinutes,
                warningMinutes = it.warningThresholdMinutes,
                address = it.connection?.address ?: "",
                port = it.connection?.port ?: 3389,
                username = it.connection?.username ?: "",
                password = it.connection?.password ?: "",
                state = it.state.name,
            )
        }
        store.put(StoreKeys.SERVERS, json.encodeToString(stored))
    }

    private fun StoredServer.toSession(): ServerSession = ServerSession(
        id = id,
        name = name,
        state = runCatching { ServerState.valueOf(state) }.getOrDefault(ServerState.Stopped),
        spec = ServerSpec(4, 16, 14, "Windows Server 2025", "github-runner", "github"),
        connection = if (username.isBlank()) null else ConnectionInfo(address, port, username, password, ""),
        steps = emptyList(),
        uptimeLabel = "",
        remainingLabel = "",
        remainingFraction = 0f,
        warningThresholdMinutes = warningMinutes,
        workflowRunNumber = runNumber,
        workflowRunUrl = runUrl,
        repositoryFullName = repository,
        operations = emptyList(),
        backups = emptyList(),
        lastError = null,
        runId = runId,
        startedAtEpochMs = startedAtEpochMs,
        durationMinutes = durationMinutes,
    )

    // ---------------------------------------------------------------- setup: GitHub

    fun connectGitHub(rawToken: String) {
        val token = rawToken.trim()
        if (token.isBlank()) {
            publish(state.copy(error = context.getString(R.string.err_token_empty)))
            return
        }
        scope.launch {
            publish(state.copy(busy = true, error = null))
            val client = GitHubClient(token)
            runCatching { client.validate() }
                .onSuccess { validation ->
                    github?.close()
                    github = client
                    store.put(StoreKeys.GITHUB_TOKEN, token)
                    store.put(StoreKeys.GITHUB_LOGIN, validation.user.login)
                    store.put(StoreKeys.GITHUB_METHOD, AuthMethod.PersonalAccessToken.name)
                    store.put(StoreKeys.GITHUB_SCOPES, validation.scopes.joinToString(","))
                    publish(
                        state.copy(
                            busy = false,
                            tokenInput = "",
                            error = if (validation.missing.isEmpty()) null else context.getString(R.string.err_scopes),
                            account = GitHubAccount(
                                login = validation.user.login,
                                displayName = validation.user.name ?: validation.user.login,
                                method = AuthMethod.PersonalAccessToken,
                                grantedScopes = validation.scopes,
                                missingScopes = validation.missing,
                                tokenExpiresLabel = "",
                            ),
                        ),
                    )
                }
                .onFailure { error ->
                    client.close()
                    publish(state.copy(busy = false, error = error.message ?: context.getString(R.string.err_token_invalid)))
                }
        }
    }

    private fun refreshAccount() {
        val client = github ?: return
        scope.launch {
            runCatching { client.validate() }.onSuccess { validation ->
                store.put(StoreKeys.GITHUB_SCOPES, validation.scopes.joinToString(","))
                publish(
                    state.copy(
                        account = state.account?.copy(
                            login = validation.user.login,
                            displayName = validation.user.name ?: validation.user.login,
                            grantedScopes = validation.scopes,
                            missingScopes = validation.missing,
                        ),
                    ),
                )
            }
        }
    }

    fun disconnectGitHub() {
        github?.close()
        github = null
        listOf(StoreKeys.GITHUB_TOKEN, StoreKeys.GITHUB_LOGIN, StoreKeys.GITHUB_METHOD, StoreKeys.GITHUB_SCOPES)
            .forEach { store.remove(it) }
        publish(state.copy(account = null))
    }

    // ---------------------------------------------------------------- setup: network

    fun connectNetwork(rawKey: String, silent: Boolean = false) {
        val key = rawKey.trim()
        if (key.isBlank()) {
            if (!silent) publish(state.copy(error = context.getString(R.string.err_key_empty)))
            return
        }
        scope.launch {
            publish(state.copy(busy = !silent, tailnet = state.tailnet.copy(state = NetworkState.Connecting)))
            store.put(StoreKeys.TS_AUTH_KEY, key)
            runCatching { node.start(hostname = "remotehub-phone", authKey = key, ephemeral = true) }
                .onSuccess { status ->
                    applyNodeStatus(status)
                    if (status.running) {
                        publish(state.copy(busy = false, error = null))
                        node.startPolling { applyNodeStatus(it) }
                    } else {
                        // Show what the tunnel actually reported instead of a bare status word.
                        val detail = status.logs.takeLast(3).joinToString(" · ")
                        val message = status.error.ifBlank { context.getString(R.string.err_network) }
                        publish(
                            state.copy(
                                busy = false,
                                error = if (detail.isBlank()) message else "$message\n$detail",
                                tailnet = state.tailnet.copy(state = NetworkState.Error, lastError = message),
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    publish(
                        state.copy(
                            busy = false,
                            error = if (silent) null else (error.message ?: context.getString(R.string.err_network)),
                            tailnet = state.tailnet.copy(
                                state = NetworkState.Error,
                                lastError = error.message ?: "tailscale start failed",
                            ),
                        ),
                    )
                }
        }
    }

    private fun applyNodeStatus(status: app.remotehub.net.NodeStatus) {
        val networkState = when {
            status.running -> NetworkState.Online
            status.authUrl.isNotBlank() -> NetworkState.Authenticating
            status.state == "Starting" -> NetworkState.Connecting
            status.error.isNotBlank() -> NetworkState.Error
            else -> NetworkState.Offline
        }
        if (status.authUrl.isNotBlank()) pendingLoginUrl = status.authUrl
        publish(
            state.copy(
                tailnet = state.tailnet.copy(
                    state = networkState,
                    tailnetName = status.tailnet,
                    nodeName = status.hostname,
                    nodeAddress = status.ipv4,
                    relayLabel = status.relay,
                    transportLabel = if (status.userspace) "userspace" else "tun",
                    lastError = status.error.ifBlank { null },
                ),
            ),
        )
    }

    fun disconnectNetwork() {
        scope.launch {
            rdpSessions.values.forEach { it.close() }
            rdpSessions.clear()
            node.stop()
            store.remove(StoreKeys.TS_AUTH_KEY)
            publish(state.copy(tailnet = state.tailnet.copy(state = NetworkState.Offline, nodeAddress = "")))
        }
    }

    fun openTailscaleLogin() {
        val url = pendingLoginUrl ?: node.loginUrl()
        if (url.isNotBlank()) onEffect(SideEffect.OpenUrl(url))
    }

    fun refreshNetworkStatus() {
        if (node.running) applyNodeStatus(node.status())
    }

    fun stopPolling() = node.stopPolling()

    // ---------------------------------------------------------------- create

    fun startCreate() {
        val missing = state.missingItem
        if (missing != null) {
            publish(state.copy(screen = AppScreen.Setup, highlightItem = missing))
            return
        }
        publish(state.copy(screen = AppScreen.Create, error = null))
    }

    fun setServerName(name: String) = publish(state.copy(form = state.form.copy(name = name)))

    fun setDuration(minutes: Int) = publish(state.copy(form = state.form.copy(durationMinutes = minutes)))

    fun provision() {
        val client = github
        val owner = state.account?.login
        if (client == null || owner == null) {
            publish(state.copy(error = context.getString(R.string.err_github_first), screen = AppScreen.Setup))
            return
        }
        val authKey = store.get(StoreKeys.TS_AUTH_KEY)
        if (authKey.isNullOrBlank()) {
            publish(state.copy(error = context.getString(R.string.err_key_empty), screen = AppScreen.Setup))
            return
        }
        val form = state.form
        if (form.name.isBlank()) {
            publish(state.copy(error = context.getString(R.string.err_name_empty)))
            return
        }
        publish(state.copy(screen = AppScreen.Progress, provisioning = true, steps = emptyList(), progressLog = emptyList(), error = null))
        provisionJob = scope.launch {
            val provisioner = Provisioner(
                client = client,
                secretBox = secretBox,
                owner = owner,
                workflowTemplate = SessionWorkflow.render(CHECKOUT_SHA, UPLOAD_SHA),
            )
            runCatching {
                provisioner.run(
                    ProvisionRequest(
                        serverName = form.name,
                        repositoryName = state.repositoryName,
                        tailscaleAuthKey = authKey,
                        durationMinutes = form.durationMinutes,
                        warningMinutes = state.warningMinutes,
                        publicRepository = state.publicRepository,
                    ),
                ) { event -> applyProvisionEvent(event) }
            }.onSuccess { outcome ->
                val session = ServerSession(
                    id = outcome.sessionId,
                    name = form.name,
                    state = ServerState.Ready,
                    spec = ServerSpec(4, 16, 14, "Windows Server 2025", "github-runner", "github"),
                    connection = ConnectionInfo("", 3389, outcome.username, outcome.password, ""),
                    steps = state.steps,
                    uptimeLabel = "",
                    remainingLabel = SessionMath.formatDuration(outcome.durationMinutes * 60_000L),
                    remainingFraction = 1f,
                    warningThresholdMinutes = state.warningMinutes,
                    workflowRunNumber = outcome.runNumber,
                    workflowRunUrl = outcome.runUrl,
                    repositoryFullName = "$owner/${state.repositoryName}",
                    operations = listOf(
                        OperationRecord(
                            outcome.sessionId,
                            OperationKind.Provision,
                            OperationResult.Success,
                            "",
                            "run #${outcome.runNumber}",
                        ),
                    ),
                    backups = emptyList(),
                    lastError = null,
                    runId = outcome.runId,
                    startedAtEpochMs = outcome.startedAtEpochMs,
                    durationMinutes = outcome.durationMinutes,
                )
                publish(
                    state.copy(
                        provisioning = false,
                        servers = state.servers.filterNot { it.id == session.id } + session,
                        activeServerId = session.id,
                    ),
                )
                persistServers()
                resolveEndpoint(session.id)
                watchRun(session.id)
            }.onFailure { error ->
                publish(state.copy(provisioning = false, error = error.message ?: context.getString(R.string.err_provision)))
            }
        }
    }

    private fun applyProvisionEvent(event: ProvisionEvent) {
        when (event) {
            is ProvisionEvent.Steps -> publish(state.copy(steps = event.steps))
            is ProvisionEvent.Log -> publish(state.copy(progressLog = (state.progressLog + event.line).takeLast(20)))
            is ProvisionEvent.Ready -> Unit
            is ProvisionEvent.Failed -> publish(state.copy(error = event.message))
        }
    }

    fun cancelProvision() {
        provisionJob?.cancel()
        provisionJob = null
        publish(state.copy(provisioning = false, screen = AppScreen.Setup))
    }

    fun retryProvision() = provision()

    // ---------------------------------------------------------------- follow the run

    private fun watchRun(serverId: String) {
        val client = github ?: return
        watchJob?.cancel()
        watchJob = scope.launch {
            while (true) {
                val session = state.servers.firstOrNull { it.id == serverId } ?: return@launch
                val parts = session.repositoryFullName.split("/")
                if (parts.size != 2) return@launch
                val outcome = runCatching {
                    val run = client.run(parts[0], parts[1], session.runId)
                    val jobs = client.jobs(parts[0], parts[1], session.runId)
                    run to jobs
                }.getOrNull() ?: break
                val (run, jobs) = outcome
                val steps = jobs.flatMap { job ->
                    job.steps.map { step ->
                        ProvisionStep(
                            id = step.name,
                            status = when {
                                step.conclusion == "success" -> StepStatus.Done
                                step.conclusion == "failure" -> StepStatus.Failed
                                step.status == "in_progress" -> StepStatus.Running
                                else -> StepStatus.Pending
                            },
                            detail = step.conclusion,
                            elapsedMillis = null,
                        )
                    }
                }
                val ended = run.status == "completed"
                updateServer(serverId) { current ->
                    current.copy(
                        steps = steps,
                        state = when {
                            ended && run.conclusion == "success" -> ServerState.Stopped
                            ended -> ServerState.Failed
                            current.connection?.address?.isNotBlank() == true -> current.state
                            else -> ServerState.Provisioning
                        },
                        lastError = if (ended && run.conclusion != "success") "run ${run.conclusion}" else null,
                    )
                }
                if (ended) {
                    persistServers()
                    return@launch
                }
                if (state.servers.firstOrNull { it.id == serverId }?.connection?.address.isNullOrBlank()) {
                    resolveEndpoint(serverId)
                }
                delay(10_000)
            }
        }
    }

    /** The phone is on the same tailnet, so the new node shows up as a peer. */
    private suspend fun resolveEndpoint(serverId: String) {
        val session = state.servers.firstOrNull { it.id == serverId } ?: return
        if (!node.running) return
        val hostname = "rh-${session.id}"
        val peer = withContext(Dispatchers.IO) { runCatching { node.peers() } }.getOrDefault(emptyList())
            .firstOrNull { it.hostname.equals(hostname, true) || it.hostname.equals(session.id, true) }
        if (peer != null && peer.ipv4.isNotBlank()) {
            updateServer(serverId) {
                it.copy(
                    connection = it.connection?.copy(address = peer.ipv4),
                    state = if (it.state == ServerState.Provisioning) ServerState.Ready else it.state,
                )
            }
            persistServers()
        }
    }

    private fun updateServer(id: String, transform: (ServerSession) -> ServerSession) {
        publish(state.copy(servers = state.servers.map { if (it.id == id) transform(it) else it }))
    }

    // ---------------------------------------------------------------- countdown

    fun tick() {
        if (state.servers.isEmpty()) return
        val now = Clock.System.now().toEpochMilliseconds()
        var changed = false
        val updated = state.servers.map { session ->
            if (session.startedAtEpochMs == 0L) return@map session
            if (session.state == ServerState.Stopped || session.state == ServerState.Failed) return@map session
            val remaining = SessionMath.remainingMillis(session.startedAtEpochMs, session.durationMinutes, now)
            val fraction = SessionMath.remainingFraction(session.startedAtEpochMs, session.durationMinutes, now)
            val next = SessionMath.stateFor(
                session.state, session.startedAtEpochMs, session.durationMinutes,
                session.warningThresholdMinutes, now,
            )
            if (next == ServerState.Expiring && state.expiryAlertEnabled && alerted.add(session.id)) {
                onEffect(
                    SideEffect.Notify(
                        context.getString(R.string.expiry_title),
                        "${session.name}: ${SessionMath.formatDuration(remaining)}",
                    ),
                )
            }
            changed = true
            session.copy(
                state = next,
                remainingLabel = SessionMath.formatDuration(remaining),
                remainingFraction = fraction,
                uptimeLabel = SessionMath.formatDuration(now - session.startedAtEpochMs),
            )
        }
        if (!changed) return
        publish(state.copy(servers = updated))
        updated.forEach { session ->
            rdpSessions[session.id]?.let { metricsCache[session.id] = it.metrics() }
        }
    }

    // ---------------------------------------------------------------- remote desktop

    fun remoteMetrics(serverId: String): RemoteMetrics =
        metricsCache[serverId] ?: RemoteMetrics(0, 0, 0, "—", state.trackpadMode, true)

    fun rdpSession(serverId: String): RdpSession? = rdpSessions[serverId]

    fun connectRemote(serverId: String) {
        val session = state.servers.firstOrNull { it.id == serverId } ?: return
        val connection = session.connection
        if (connection == null || connection.address.isBlank()) {
            publish(state.copy(screen = AppScreen.Remote, activeServerId = serverId, error = null))
            scope.launch {
                repeat(30) {
                    resolveEndpoint(serverId)
                    if (state.servers.firstOrNull { it.id == serverId }?.connection?.address?.isNotBlank() == true) {
                        connectRemote(serverId)
                        return@launch
                    }
                    delay(4000)
                }
                publish(state.copy(error = context.getString(R.string.err_no_address)))
            }
            return
        }
        if (!node.running) {
            publish(state.copy(error = context.getString(R.string.err_network_first)))
            return
        }
        scope.launch {
            publish(state.copy(screen = AppScreen.Remote, activeServerId = serverId, busy = true, error = null))
            rdpSessions[serverId]?.let {
                publish(state.copy(busy = false))
                return@launch
            }
            val rdp = RdpSession(context, node, session, store)
            rdpSessions[serverId] = rdp
            rdp.trackpadMode = state.trackpadMode
            rdp.onUpdate = { metrics ->
                metricsCache[serverId] = metrics
                publish(state.copy())
            }
            rdp.onError = { message -> publish(state.copy(error = message)) }
            runCatching { rdp.connect() }
                .onSuccess {
                    updateServer(serverId) { it.copy(state = ServerState.Connected) }
                    startSessionService(session.name)
                    publish(state.copy(busy = false))
                }
                .onFailure { error ->
                    rdpSessions.remove(serverId)
                    rdp.close()
                    publish(state.copy(busy = false, error = error.message ?: context.getString(R.string.err_rdp)))
                }
        }
    }

    fun disconnectRemote(serverId: String) {
        rdpSessions.remove(serverId)?.close()
        metricsCache.remove(serverId)
        updateServer(serverId) { if (it.state == ServerState.Connected) it.copy(state = ServerState.Ready) else it }
        stopSessionService()
        publish(state.copy(screen = AppScreen.Detail))
    }

    fun showKeyboard() {
        context.getSystemService(InputMethodManager::class.java)
            ?.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    fun pushClipboard(serverId: String) {
        val manager = context.getSystemService(ClipboardManager::class.java)
        val text = manager?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        if (text.isNullOrBlank()) {
            publish(state.copy(error = context.getString(R.string.err_clipboard_empty)))
            return
        }
        val sent = rdpSessions[serverId]?.sendClipboard(text) ?: false
        publish(state.copy(toast = if (sent) context.getString(R.string.clipboard_sent) else context.getString(R.string.clipboard_pending)))
    }

    fun sendCtrlAltDel(serverId: String) {
        rdpSessions[serverId]?.sendCtrlAltDel()
    }

    fun toggleInputMode() {
        val next = !state.trackpadMode
        publish(state.copy(trackpadMode = next))
        rdpSessions.values.forEach { it.trackpadMode = next }
    }

    // ---------------------------------------------------------------- lifecycle

    fun stopServer(serverId: String) {
        val client = github ?: return
        val session = state.servers.firstOrNull { it.id == serverId } ?: return
        scope.launch {
            val parts = session.repositoryFullName.split("/")
            if (parts.size != 2) return@launch
            runCatching { client.cancel(parts[0], parts[1], session.runId) }
            rdpSessions.remove(serverId)?.close()
            metricsCache.remove(serverId)
            updateServer(serverId) {
                it.copy(
                    state = ServerState.Stopped,
                    operations = it.operations + OperationRecord(
                        "stop-${Clock.System.now().toEpochMilliseconds()}",
                        OperationKind.Stop, OperationResult.Success, "", "cancelled",
                    ),
                )
            }
            persistServers()
            publish(state.copy(screen = AppScreen.Setup))
        }
    }

    /** Runs the same workflow again. A fresh machine, a fresh address, a fresh password. */
    fun rerunServer(serverId: String) {
        val session = state.servers.firstOrNull { it.id == serverId } ?: return
        rdpSessions.remove(serverId)?.close()
        metricsCache.remove(serverId)
        publish(
            state.copy(
                form = state.form.copy(name = session.name, durationMinutes = session.durationMinutes),
                activeServerId = serverId,
            ),
        )
        provision()
    }

    // ---------------------------------------------------------------- backup

    fun createBackup(serverId: String) {
        val client = github ?: return
        val session = state.servers.firstOrNull { it.id == serverId } ?: return
        scope.launch {
            publish(state.copy(busy = true))
            val passphrase = store.get(StoreKeys.BACKUP_PASSPHRASE)
                ?: randomPassword(32).also { store.put(StoreKeys.BACKUP_PASSPHRASE, it) }
            runCatching {
                val payload = json.encodeToString(
                    BackupSnapshot(
                        sessionId = session.id,
                        name = session.name,
                        address = session.connection?.address ?: "",
                        username = session.connection?.username ?: "",
                        createdAt = Clock.System.now().toEpochMilliseconds(),
                        operations = session.operations.map { "${it.kind}:${it.result}" },
                    ),
                )
                val sealed = withContext(Dispatchers.Default) {
                    BackupCodec.encrypt(payload.encodeToByteArray(), passphrase)
                }
                val parts = session.repositoryFullName.split("/")
                val path = "backups/${session.id}/${Clock.System.now().toEpochMilliseconds()}.enc"
                val response = client.putContent(parts[0], parts[1], path, base64(sealed), "Encrypted backup for ${session.id}")
                val record = BackupRecord(
                    id = path,
                    createdAtLabel = SessionMath.formatDuration(0),
                    sizeLabel = SessionMath.formatBytes(sealed.size.toLong()),
                    encrypted = true,
                    commitRef = response.commit?.sha?.take(8) ?: "",
                )
                updateServer(serverId) {
                    it.copy(
                        backups = it.backups + record,
                        operations = it.operations + OperationRecord(
                            "backup-${record.id}", OperationKind.Backup, OperationResult.Success, "", path,
                        ),
                    )
                }
                persistServers()
            }.onSuccess {
                publish(state.copy(busy = false, toast = context.getString(R.string.backup_stored)))
            }.onFailure { error ->
                publish(state.copy(busy = false, error = error.message ?: context.getString(R.string.err_backup)))
            }
        }
    }

    // ---------------------------------------------------------------- settings

    fun setTheme(mode: Int) {
        publish(state.copy(themeMode = mode))
        store.put(StoreKeys.THEME, mode.toString())
    }

    fun setLocale(tag: String) {
        publish(state.copy(localeTag = tag))
        store.put(StoreKeys.LOCALE, tag)
    }

    fun setExpiryAlert(enabled: Boolean) {
        publish(state.copy(expiryAlertEnabled = enabled))
        store.put(ALERT_KEY, enabled.toString())
    }

    fun setWarningMinutes(minutes: Int) {
        publish(state.copy(warningMinutes = minutes))
        store.put(StoreKeys.WARNING_MINUTES, minutes.toString())
    }

    fun setPublicRepository(public: Boolean) = publish(state.copy(publicRepository = public))

    fun setNotificationPermission(granted: Boolean) {
        notificationsAllowed = granted
    }

    fun postAlert(title: String, body: String) {
        if (!notificationsAllowed) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val notification = NotificationCompat.Builder(context, RemoteHubApplication.ALERT_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        manager.notify(title.hashCode(), notification)
    }

    private fun startSessionService(label: String) {
        val intent = Intent(context, app.remotehub.net.SessionService::class.java)
            .putExtra(app.remotehub.net.SessionService.EXTRA_LABEL, label)
        runCatching { context.startForegroundService(intent) }
    }

    private fun stopSessionService() {
        runCatching { context.stopService(Intent(context, app.remotehub.net.SessionService::class.java)) }
    }

    // ---------------------------------------------------------------- navigation

    fun openServer(serverId: String) {
        publish(state.copy(screen = AppScreen.Detail, activeServerId = serverId, error = null))
        scope.launch { resolveEndpoint(serverId) }
    }

    fun goSetup() = publish(state.copy(screen = AppScreen.Setup, error = null, highlightItem = null))

    fun goSettings() = publish(state.copy(screen = AppScreen.Settings))

    fun openGuide(id: String) = publish(state.copy(screen = AppScreen.Guide, guideId = id))

    fun closeGuide() = publish(state.copy(screen = AppScreen.Settings, guideId = null))

    fun togglePassword() = publish(state.copy(passwordVisible = !state.passwordVisible))

    fun clearError() = publish(state.copy(error = null))

    fun clearToast() = publish(state.copy(toast = null))

    fun clearCredentials() {
        rdpSessions.values.forEach { it.close() }
        rdpSessions.clear()
        scope.launch { node.stop() }
        github?.close()
        github = null
        store.clear()
        alerted.clear()
        publish(AppState(localeTag = state.localeTag, themeMode = state.themeMode))
    }

    fun release() {
        watchJob?.cancel()
        provisionJob?.cancel()
        rdpSessions.values.forEach { it.close() }
        rdpSessions.clear()
        node.release()
        github?.close()
        github = null
        stopSessionService()
    }

    companion object {
        const val REPO_KEY = "sessions.repo"
        const val ALERT_KEY = "settings.expiryalert"
        const val CHECKOUT_SHA = "11d5960a326750d5838078e36cf38b85af677262"
        const val UPLOAD_SHA = "ea165f8d65b6e75b540449e92b4886f43607fa02"
    }
}

@Serializable
data class BackupSnapshot(
    val sessionId: String,
    val name: String,
    val address: String,
    val username: String,
    val createdAt: Long,
    val operations: List<String>,
)
