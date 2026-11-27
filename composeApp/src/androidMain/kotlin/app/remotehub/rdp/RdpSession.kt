package app.remotehub.rdp

import android.content.Context
import android.graphics.Bitmap
import app.remotehub.data.SecureStore
import app.remotehub.domain.ServerSession
import app.remotehub.diag.Diagnostics
import app.remotehub.net.TailscaleNode
import app.remotehub.ui.RemoteMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RdpSession(
    private val context: Context,
    private val node: TailscaleNode,
    private val server: ServerSession,
    private val store: SecureStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var handle: Long = 0
    private var forwardPort: Int = 0
    private var pumpJob: Job? = null
    private var statsJob: Job? = null
    private var bitmap: Bitmap? = null
    private var lastFrames: Long = 0
    private var lastSampleAt: Long = 0
    private var lastBytes: Long = 0
    private var fingerprint: String = ""

    var trackpadMode: Boolean = false
    var onUpdate: (RemoteMetrics) -> Unit = {}
    var onError: (String) -> Unit = {}
    var onFrame: (Bitmap) -> Unit = {}

    private val listener = object : RdpListener {
        override fun onState(state: Int, message: String) {
            Diagnostics.info("rdp", "engine state=$state ${message}".trim())
            when (state) {
                NativeRdp.STATE_FAILED -> onError(message)
                NativeRdp.STATE_CERT_CHANGED -> onError("host certificate changed: $message")
                NativeRdp.STATE_CONNECTED -> {
                    fingerprint = runCatching { NativeRdp.fingerprint(handle) }.getOrDefault("")
                    if (fingerprint.isNotBlank()) {
                        store.put("$FINGERPRINT_PREFIX${server.id}", fingerprint)
                    }
                }
                else -> Unit
            }
        }

        override fun onResize(width: Int, height: Int) {
            ensureBitmap(width, height)
        }

        override fun onClipboard(utf8: ByteArray) {
            val text = utf8.decodeToString()
            val manager = context.getSystemService(android.content.ClipboardManager::class.java)
            manager?.setPrimaryClip(android.content.ClipData.newPlainText("remote", text))
        }
    }

    suspend fun connect() = withContext(Dispatchers.IO) {
        if (!NativeRdp.available) {
            error("native RDP engine unavailable: ${NativeRdp.failureReason}")
        }
        val connection = server.connection ?: error("no connection details")
        Diagnostics.info("rdp", "opening tunnel to ${connection.address}:${connection.port} as ${connection.username}")
        val forward = node.openRdpForward(connection.address, connection.port)
        forwardPort = forward.substringAfterLast(':').toInt()
        handle = NativeRdp.create(listener)
        if (handle == 0L) error("could not allocate an RDP session")

        val width = 1600
        val height = 900
        NativeRdp.allocFrame(handle, width, height)
        ensureBitmap(width, height)

        val pinned = store.get("$FINGERPRINT_PREFIX${server.id}") ?: ""
        val ok = NativeRdp.connect(
            handle = handle,
            host = "127.0.0.1",
            port = forwardPort,
            username = connection.username,
            password = connection.password,
            domain = "",
            width = width,
            height = height,
            colorDepth = 24,
            nla = true,
            graphicsPipeline = true,
            h264 = true,
            clipboard = true,
            audio = false,
            pinnedFingerprint = pinned,
            quality = 1,
        )
        if (!ok) {
            Diagnostics.error("rdp", "FreeRDP refused the connection to 127.0.0.1:$forwardPort (nla=true, pinned=${pinned.isNotBlank()})")
            error("RDP connect call failed")
        }

        startPump()
        startStats(connection.address, connection.port)
    }

    private fun ensureBitmap(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val current = bitmap
        if (current != null && current.width == width && current.height == height) return
        current?.recycle()
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xFF101317.toInt())
        }
    }

    private fun startPump() {
        pumpJob?.cancel()
        pumpJob = scope.launch {
            var failures = 0
            while (isActive) {
                val target = bitmap
                if (target != null && handle != 0L) {
                    // A render failure must not spin silently or take the app down.
                    val result = runCatching { NativeRdp.render(handle, target) }
                        .onFailure { failure ->
                            failures += 1
                            if (failures == 1 || failures % 60 == 0) {
                                Diagnostics.warn("rdp", "frame render failed ($failures)", failure)
                            }
                        }
                        .getOrDefault(0)
                    if (result == 1) {
                        withContext(Dispatchers.Main) { onFrame(target) }
                    }
                    if (failures >= 240) {
                        Diagnostics.error("rdp", "stopping the frame pump after repeated render failures")
                        withContext(Dispatchers.Main) { onError("RDP frame rendering failed repeatedly") }
                        return@launch
                    }
                }
                delay(16)
            }
        }
    }

    private fun startStats(host: String, port: Int) {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                delay(1000)
                if (handle == 0L) continue
                val sample = runCatching { collectMetrics(host, port) }
                    .onFailure { Diagnostics.warn("rdp", "metrics sample failed", it) }
                    .getOrNull() ?: continue
                withContext(Dispatchers.Main) { onUpdate(sample) }
            }
        }
    }

    private suspend fun collectMetrics(host: String, port: Int): RemoteMetrics {
        val frames = NativeRdp.frames(handle)
        val now = System.currentTimeMillis()
        val elapsed = if (lastSampleAt == 0L) 1000 else now - lastSampleAt
        val fps = if (elapsed > 0) ((frames - lastFrames) * 1000 / elapsed).toInt() else 0
        lastFrames = frames
        lastSampleAt = now
        val width = NativeRdp.width(handle)
        val height = NativeRdp.height(handle)
        val latency = node.latency(host, port, 3000)
        val bytes = width.toLong() * height * 4 * fps
        val kbps = if (bytes > 0) (bytes * 8 / 1000).toInt() else 0
        lastBytes = bytes
        return RemoteMetrics(
        latencyMillis = if (latency < 0) 0 else latency,
        framesPerSecond = fps,
        kilobitsPerSecond = kbps,
        resolutionLabel = if (width > 0) "${width}x$height" else "—",
        trackpadMode = trackpadMode,
        connecting = !NativeRdp.connected(handle),
        )
    }

    fun metrics(): RemoteMetrics = RemoteMetrics(
        latencyMillis = 0,
        framesPerSecond = 0,
        kilobitsPerSecond = 0,
        resolutionLabel = "${NativeRdp.width(handle)}x${NativeRdp.height(handle)}",
        trackpadMode = trackpadMode,
        connecting = !NativeRdp.connected(handle),
    )

    fun sendPointer(flags: Int, x: Int, y: Int) {
        runCatching { NativeRdp.mouse(handle, flags, x, y) }
    }

    fun sendWheel(delta: Int, x: Int, y: Int) {
        runCatching { NativeRdp.wheel(handle, NativeRdp.PTR_WHEEL or delta, x, y) }
    }

    fun sendTouch(flags: Int, finger: Int, x: Int, y: Int) {
        runCatching { NativeRdp.touch(handle, flags, finger, 0, x, y) }
    }

    fun sendKey(scancode: Int, down: Boolean) = runCatching { NativeRdp.key(handle, scancode, down) }.isSuccess

    fun sendText(text: String) {
        text.forEach { code -> runCatching { NativeRdp.unicode(handle, code.code) } }
    }

    fun sendCtrlAltDel() = runCatching {
        NativeRdp.key(handle, 0x1D, true)
        NativeRdp.key(handle, 0x38, true)
        NativeRdp.key(handle, 0x153, true)
        NativeRdp.key(handle, 0x153, false)
        NativeRdp.key(handle, 0x38, false)
        NativeRdp.key(handle, 0x1D, false)
    }

    fun sendClipboard(text: String): Boolean = runCatching { NativeRdp.sendClipboard(handle, text) }.getOrDefault(false)

    fun resize(width: Int, height: Int): Boolean {
        NativeRdp.allocFrame(handle, width, height)
        ensureBitmap(width, height)
        return NativeRdp.resize(handle, width, height)
    }

    fun currentBitmap(): Bitmap? = bitmap

    fun handleForInput(): Long = handle

    fun close() {
        pumpJob?.cancel()
        statsJob?.cancel()
        if (handle != 0L) {
            Diagnostics.info("rdp", "closing session handle=$handle")
            runCatching { NativeRdp.disconnect(handle) }
            runCatching { NativeRdp.destroy(handle) }
            handle = 0
        }
        runCatching { bitmap?.recycle() }
        bitmap = null
        scope.cancel()
    }

    companion object {
        const val FINGERPRINT_PREFIX = "rdp.fingerprint."
    }
}
