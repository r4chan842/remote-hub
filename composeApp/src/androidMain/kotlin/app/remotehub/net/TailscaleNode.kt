package app.remotehub.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import app.remotehub.platform.AndroidEnv
import app.remotehub.tsbridge.Node
import app.remotehub.tsbridge.Tsbridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class NodeStatus(
    val state: String = "Stopped",
    val running: Boolean = false,
    val hostname: String = "",
    val tailnet: String = "",
    val ipv4: String = "",
    val ipv6: String = "",
    val authUrl: String = "",
    val error: String = "",
    val health: List<String> = emptyList(),
    val peerCount: Int = 0,
    val online: Boolean = false,
    val relay: String = "",
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
    val logs: List<String> = emptyList(),
    val version: String = "",
    val userspace: Boolean = true,
    val forwarders: List<String> = emptyList(),
)

@Serializable
data class TailnetPeer(
    val hostname: String = "",
    val dnsName: String = "",
    val ipv4: String = "",
    val online: Boolean = false,
    val os: String = "",
    val relay: String = "",
    val curAddr: String = "",
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
    val lastSeen: String = "",
    val tags: List<String> = emptyList(),
)

class TailscaleNode(private val context: Context = AndroidEnv.appContext) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var node: Node? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var pollJob: Job? = null
    private var forwardAddress: String? = null

    val running: Boolean get() = node != null

    fun stateDir(name: String): String = Tsbridge.stateDir(context.filesDir.absolutePath, name)

    suspend fun start(
        hostname: String,
        authKey: String,
        ephemeral: Boolean = true,
    ): NodeStatus = withContext(Dispatchers.IO) {
        stop()
        val created = Tsbridge.newNode(stateDir(hostname), hostname, authKey, ephemeral)
        node = created
        created.startAsync()
        registerNetworkCallback()
        created.awaitRunning(90_000)
        status()
    }

    fun status(): NodeStatus {
        val current = node ?: return NodeStatus()
        return runCatching { json.decodeFromString(NodeStatus.serializer(), current.statusJSON()) }
            .getOrElse { NodeStatus(error = it.message ?: "status decode failed") }
    }

    fun peers(): List<TailnetPeer> {
        val current = node ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<TailnetPeer>>(current.peersJSON())
        }.getOrDefault(emptyList())
    }

    fun loginUrl(): String = node?.loginURL() ?: ""

    fun selfIp(): String = node?.selfIP() ?: ""

    suspend fun openRdpForward(target: String, port: Int): String = withContext(Dispatchers.IO) {
        val current = node ?: error("tailscale node is not running")
        val address = current.openForward("127.0.0.1:0", "$target:$port")
        forwardAddress = address
        address
    }

    fun localForwardPort(): Int? = forwardAddress?.substringAfterLast(':')?.toIntOrNull()

    suspend fun probe(host: String, port: Int, timeoutMillis: Int = 6000): Boolean =
        withContext(Dispatchers.IO) {
            val current = node ?: return@withContext false
            runCatching { current.probe("$host:$port", timeoutMillis.toLong()) }.isSuccess
        }

    suspend fun latency(host: String, port: Int, timeoutMillis: Int = 6000): Int =
        withContext(Dispatchers.IO) {
            node?.latencyMillis("$host:$port", timeoutMillis.toLong())?.toInt() ?: -1
        }

    fun startPolling(intervalMillis: Long = 4000, onStatus: (NodeStatus) -> Unit) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                onStatus(status())
                delay(intervalMillis)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun registerNetworkCallback() {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                node?.setNetworkUp(true)
                node?.notifyNetworkChange()
            }

            override fun onLost(network: Network) {
                node?.notifyNetworkChange()
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                node?.setNetworkUp(validated)
                node?.notifyNetworkChange()
            }
        }
        networkCallback = callback
        runCatching { manager.registerDefaultNetworkCallback(callback) }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        stopPolling()
        networkCallback?.let { callback ->
            val manager = context.getSystemService(ConnectivityManager::class.java)
            runCatching { manager?.unregisterNetworkCallback(callback) }
        }
        networkCallback = null
        forwardAddress?.let { node?.closeForward(it) }
        forwardAddress = null
        node?.stop()
        node = null
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        runCatching { node?.logout() }
        Unit
    }

    suspend fun wipe(hostname: String) = withContext(Dispatchers.IO) {
        node?.wipe()
        node = null
        runCatching { java.io.File(stateDir(hostname)).deleteRecursively() }
        Unit
    }

    fun release() {
        pollJob?.cancel()
        node?.stop()
        node = null
        scope.cancel()
    }
}
