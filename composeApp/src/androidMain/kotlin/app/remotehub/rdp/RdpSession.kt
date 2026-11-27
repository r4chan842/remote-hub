package app.remotehub.rdp

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import app.remotehub.data.SecureStore
import app.remotehub.domain.ServerSession
import app.remotehub.diag.Diagnostics
import app.remotehub.net.TailscaleNode
import app.remotehub.ui.RemoteMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One live RDP session.
 *
 * The session has an explicit phase instead of deriving "connecting" from the
 * socket flag. That derivation was the cause of the permanent connecting overlay:
 * when the engine dropped the link, `connected` went false, the UI read that as
 * "still handshaking", and the last frame stayed on screen under a spinner forever.
 */
class RdpSession(
    private val context: Context,
    private val node: TailscaleNode,
    private val server: ServerSession,
    private val store: SecureStore,
) {
    private enum class Phase { Handshake, Live, Reconnecting, Closed }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var handle: Long = 0
    private var forwardPort: Int = 0
    private var pumpJob: Job? = null
    private var statsJob: Job? = null

    @Volatile private var bitmap: Bitmap? = null

    @Volatile private var phase: Phase = Phase.Handshake

    @Volatile private var closing: Boolean = false

    /**
     * The last real measurement. The session timer used to overwrite the UI cache
     * with a freshly built zeroed sample every second, which is why the pills read
     * 0 ms / 0 fps / 0 kbps on a perfectly healthy session.
     */
    @Volatile private var lastSample: RemoteMetrics? = null

    private var lastFrames: Long = 0
    private var lastSampleAt: Long = 0
    private var lastBytes: Long = -1
    private var fallbackRtt: Int = 0
    private var statTicks: Int = 0
    private var fingerprint: String = ""

    /** Completed by the engine's own state callback, not by the connect call. */
    private val ready = CompletableDeferred<Unit>()

    /**
     * Completed by the first frame that came from a real EndPaint. The engine also
     * raises the dirty flag once during PostConnect, so "render returned 1" alone
     * would fire on the initial blank buffer and defeat the white-screen guard.
     */
    private val firstFrame = CompletableDeferred<Unit>()

    var trackpadMode: Boolean = false
    var onUpdate: (RemoteMetrics) -> Unit = {}
    var onError: (String) -> Unit = {}
    var onFrame: (Bitmap) -> Unit = {}

    /** The link died after the desktop was already up. The controller retries once. */
    var onDropped: (String) -> Unit = {}

    private val listener = object : RdpListener {
        override fun onState(state: Int, message: String) {
            Diagnostics.info("rdp", "engine state=$state ${message}".trim())
            when (state) {
                NativeRdp.STATE_FAILED -> {
                    if (closing) return
                    val failure = IllegalStateException(message)
                    if (!ready.isCompleted) ready.completeExceptionally(failure) else onError(message)
                }
                NativeRdp.STATE_CERT_CHANGED -> {
                    if (closing) return
                    val text = "host certificate changed: $message"
                    if (!ready.isCompleted) ready.completeExceptionally(IllegalStateException(text)) else onError(text)
                }
                NativeRdp.STATE_RECONNECTING -> {
                    // FreeRDP is retrying internally. Say so instead of freezing.
                    if (ready.isCompleted && !closing) {
                        phase = Phase.Reconnecting
                        publishMetrics()
                    }
                }
                NativeRdp.STATE_DISCONNECTED -> {
                    if (closing) return
                    phase = Phase.Closed
                    if (!ready.isCompleted) {
                        ready.completeExceptionally(
                            IllegalStateException("the session closed before a desktop arrived: $message"),
                        )
                    } else {
                        publishMetrics()
                        // rh_notify_state invokes this listener synchronously on the
                        // engine thread. The handler closes the session, and
                        // nativeDestroy waits on that very thread, so handing it over
                        // directly would deadlock for 10s and then free a live context.
                        val reason = message.ifBlank { "the remote session closed" }
                        scope.launch(Dispatchers.Main) { onDropped(reason) }
                    }
                }
                NativeRdp.STATE_CONNECTED -> {
                    fingerprint = runCatching { NativeRdp.fingerprint(handle) }.getOrDefault("")
                    if (fingerprint.isNotBlank()) {
                        store.put("$FINGERPRINT_PREFIX${server.id}", fingerprint)
                    }
                    val reactivated = ready.isCompleted
                    // Stay in Handshake until a real frame lands, otherwise the overlay
                    // lifts onto a blank desktop -- the original white-screen experience.
                    if (firstFrame.isCompleted) phase = Phase.Live
                    if (!reactivated) {
                        ready.complete(Unit)
                    } else {
                        // A host reactivation (opening an app, UAC, resolution change)
                        // lands here. Clear the overlay and repaint everything.
                        Diagnostics.info("rdp", "session reactivated")
                        runCatching { NativeRdp.allocFrame(handle, NativeRdp.width(handle), NativeRdp.height(handle)) }
                        publishMetrics()
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

    suspend fun connect(graphicsPipeline: Boolean = true) = withContext(Dispatchers.IO) {
        if (!NativeRdp.available) {
            error("native RDP engine unavailable: ${NativeRdp.failureReason}")
        }
        val connection = server.connection ?: error("no connection details")
        Diagnostics.info("rdp", "opening tunnel to ${connection.address}:${connection.port} as ${connection.username}")
        val forward = node.openRdpForward(connection.address, connection.port)
        forwardPort = forward.substringAfterLast(':').toInt()
        ensureNativePaths()
        NativeRdp.setLogLevel(2)
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
            graphicsPipeline = graphicsPipeline,
            h264 = false,
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
        try {
            kotlinx.coroutines.withTimeout(75_000) { ready.await() }
        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            error("the desktop did not appear within 75s (the session may still be logging on)")
        }
        Diagnostics.info("rdp", "desktop is live at ${NativeRdp.width(handle)}x${NativeRdp.height(handle)}")
        try {
            kotlinx.coroutines.withTimeout(15_000) { firstFrame.await() }
        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            error("no graphics frames arrived (graphicsPipeline=$graphicsPipeline)")
        }
        phase = Phase.Live
        Diagnostics.info("rdp", "first frame rendered")
        publishMetrics()
    }

    /**
     * FreeRDP refuses to create a client context without a HOME directory: it derives
     * its config path (and the certificate store used during TLS verification) from it.
     */
    private fun ensureNativePaths() {
        val base = java.io.File(context.filesDir, "rdp")
        runCatching {
            if (System.getenv("HOME").isNullOrBlank()) {
                val home = java.io.File(base, "home").apply { mkdirs() }
                android.system.Os.setenv("HOME", home.absolutePath, true)
            }
            if (System.getenv("XDG_CONFIG_HOME").isNullOrBlank()) {
                val config = java.io.File(base, "config").apply { mkdirs() }
                android.system.Os.setenv("XDG_CONFIG_HOME", config.absolutePath, true)
            }
            if (System.getenv("TMPDIR").isNullOrBlank()) {
                val tmp = java.io.File(base, "tmp").apply { mkdirs() }
                android.system.Os.setenv("TMPDIR", tmp.absolutePath, true)
            }
        }.onFailure { Diagnostics.warn("rdp", "could not prepare native directories", it) }
    }

    /**
     * The old bitmap is deliberately not recycled: Compose may still hold it for the
     * frame currently on screen, and recycling it produced
     * "Canvas: trying to use a recycled bitmap". Android reclaims the pixels through
     * the native allocation registry once the last reference drops.
     */
    private fun ensureBitmap(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val current = bitmap
        if (current != null && !current.isRecycled && current.width == width && current.height == height) return
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xFF07080A.toInt())
        }
    }

    private fun startPump() {
        pumpJob?.cancel()
        pumpJob = scope.launch {
            var failures = 0
            var knownWidth = 0
            var knownHeight = 0
            while (isActive) {
                val session = handle
                if (session == 0L) {
                    delay(16)
                    continue
                }
                val hostWidth = NativeRdp.width(session)
                val hostHeight = NativeRdp.height(session)
                // Only hop to the main thread when the geometry actually changed.
                // This used to run 60 times a second even on an idle desktop.
                if (hostWidth > 0 && hostHeight > 0 && (hostWidth != knownWidth || hostHeight != knownHeight)) {
                    withContext(Dispatchers.Main) { ensureBitmap(hostWidth, hostHeight) }
                    knownWidth = hostWidth
                    knownHeight = hostHeight
                }
                val target = bitmap
                if (target != null && !target.isRecycled) {
                    val result = runCatching { NativeRdp.render(session, target) }
                        .onFailure { failure ->
                            failures += 1
                            if (failures == 1 || failures % 60 == 0) {
                                Diagnostics.warn("rdp", "frame render failed ($failures)", failure)
                            }
                        }
                        .getOrDefault(0)
                    if (result == 1) {
                        failures = 0
                        if (!firstFrame.isCompleted && NativeRdp.frames(session) > 0) {
                            firstFrame.complete(Unit)
                        }
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
                drainEngineLog()
                if (handle == 0L || closing) continue
                val sample = runCatching { collectMetrics(host, port) }
                    .onFailure { Diagnostics.warn("rdp", "metrics sample failed", it) }
                    .getOrNull() ?: continue
                lastSample = sample
                withContext(Dispatchers.Main) { onUpdate(sample) }
            }
        }
    }

    private fun drainEngineLog() {
        runCatching { NativeRdp.drainEngineLog() }.getOrDefault("")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                when {
                    line.startsWith("E ") -> Diagnostics.error("rdp-engine", line.drop(2))
                    line.startsWith("W ") -> Diagnostics.warn("rdp-engine", line.drop(2))
                    else -> Diagnostics.info("rdp-engine", line.drop(2))
                }
            }
    }

    private fun publishMetrics() {
        val sample = metrics()
        lastSample = sample
        scope.launch(Dispatchers.Main) { onUpdate(sample) }
    }

    private suspend fun collectMetrics(host: String, port: Int): RemoteMetrics {
        val session = handle
        val frames = NativeRdp.frames(session)
        val now = SystemClock.elapsedRealtime()
        val elapsed = if (lastSampleAt == 0L) 0L else (now - lastSampleAt).coerceAtLeast(1L)
        val fps = if (elapsed > 0) ((frames - lastFrames) * 1000 / elapsed).toInt().coerceAtLeast(0) else 0
        lastFrames = frames
        lastSampleAt = now

        val net = runCatching { NativeRdp.netStats(session) }.getOrDefault(LongArray(3))
        var rtt = net.getOrElse(0) { 0L }.toInt()
        val totalBytes = net.getOrElse(1) { 0L } + net.getOrElse(2) { 0L }
        // bytes * 8 / millis is exactly kilobits per second.
        val kbps = if (elapsed > 0 && lastBytes >= 0 && totalBytes >= lastBytes) {
            ((totalBytes - lastBytes) * 8 / elapsed).toInt().coerceAtLeast(0)
        } else {
            0
        }
        lastBytes = totalBytes

        statTicks += 1
        if (rtt <= 0) {
            // The host did not negotiate autodetect. Fall back to a tunnel probe,
            // but only every fifth sample: dialling 3389 once a second makes the
            // RDP listener accept and drop a client every second.
            if (statTicks % 5 == 1) {
                val probed = runCatching { node.latency(host, port, 2500) }.getOrDefault(-1)
                if (probed >= 0) fallbackRtt = probed
            }
            rtt = fallbackRtt
        }

        val width = NativeRdp.width(session)
        val height = NativeRdp.height(session)
        return RemoteMetrics(
            latencyMillis = rtt.coerceAtLeast(0),
            framesPerSecond = fps,
            kilobitsPerSecond = kbps,
            resolutionLabel = if (width > 0 && height > 0) "${width}x$height" else "—",
            trackpadMode = trackpadMode,
            connecting = phase == Phase.Handshake || phase == Phase.Reconnecting,
        )
    }

    /** Never zeroes a real measurement; only the phase and geometry are refreshed. */
    fun metrics(): RemoteMetrics {
        val base = lastSample
        val width = NativeRdp.width(handle)
        val height = NativeRdp.height(handle)
        return RemoteMetrics(
            latencyMillis = base?.latencyMillis ?: 0,
            framesPerSecond = base?.framesPerSecond ?: 0,
            kilobitsPerSecond = base?.kilobitsPerSecond ?: 0,
            resolutionLabel = if (width > 0 && height > 0) "${width}x$height" else (base?.resolutionLabel ?: "—"),
            trackpadMode = trackpadMode,
            connecting = phase == Phase.Handshake || phase == Phase.Reconnecting,
        )
    }

    // ------------------------------------------------------------------- input

    fun sendPointer(flags: Int, x: Int, y: Int) {
        runCatching { NativeRdp.mouse(handle, flags, x, y) }
    }

    fun sendMove(x: Int, y: Int) = sendPointer(NativeRdp.PTR_MOVE, x, y)

    fun sendButtonDown(button: Int, x: Int, y: Int) = sendPointer(buttonDownFlag(button), x, y)

    fun sendButtonUp(button: Int, x: Int, y: Int) = sendPointer(buttonUpFlag(button), x, y)

    /** A plain left click. Windows derives double-clicks from the interval between two of these. */
    fun sendClick(x: Int, y: Int) {
        sendPointer(NativeRdp.PTR_MOVE, x, y)
        sendPointer(NativeRdp.PTR_DOWN_LEFT, x, y)
        sendPointer(NativeRdp.PTR_UP_LEFT, x, y)
    }

    fun sendRightClick(x: Int, y: Int) {
        sendPointer(NativeRdp.PTR_MOVE, x, y)
        sendPointer(NativeRdp.PTR_DOWN_RIGHT, x, y)
        sendPointer(NativeRdp.PTR_UP_RIGHT, x, y)
    }

    fun sendMiddleClick(x: Int, y: Int) {
        sendPointer(NativeRdp.PTR_MOVE, x, y)
        sendPointer(NativeRdp.PTR_DOWN_MIDDLE, x, y)
        sendPointer(NativeRdp.PTR_UP_MIDDLE, x, y)
    }

    /**
     * Wheel rotation is a signed nine-bit field; bit 8 is PTR_FLAGS_WHEEL_NEGATIVE.
     * OR-ing a raw negative Int into the flags happened to work for -120 but broke
     * for every other magnitude, so encode it explicitly.
     */
    fun sendWheel(delta: Int, x: Int, y: Int, horizontal: Boolean = false) {
        if (delta == 0) return
        val rotation = delta.coerceIn(-255, 255) and 0x01FF
        val base = if (horizontal) NativeRdp.PTR_HWHEEL else NativeRdp.PTR_WHEEL
        runCatching { NativeRdp.wheel(handle, base or rotation, x, y) }
    }

    /** Kept for hosts with RDPEI. Not used for taps: it synthesises its own mouse click. */
    fun sendTouch(flags: Int, finger: Int, x: Int, y: Int) {
        runCatching { NativeRdp.touch(handle, flags, finger, 0, x, y) }
    }

    fun sendKey(scancode: Int, down: Boolean) = runCatching { NativeRdp.key(handle, scancode, down) }.isSuccess

    fun sendSync(flags: Int) = runCatching { NativeRdp.sync(handle, flags) }.isSuccess

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

    /** Releases every modifier so a lost gesture cannot leave Ctrl or Shift stuck down. */
    fun releaseModifiers() = runCatching {
        listOf(0x1D, 0x2A, 0x36, 0x38, 0x15B, 0x15C, 0x138, 0x12A).forEach {
            NativeRdp.key(handle, it, false)
        }
        NativeRdp.mouse(handle, NativeRdp.PTR_UP_LEFT, 0, 0)
        NativeRdp.mouse(handle, NativeRdp.PTR_UP_RIGHT, 0, 0)
        NativeRdp.mouse(handle, NativeRdp.PTR_UP_MIDDLE, 0, 0)
    }

    fun sendClipboard(text: String): Boolean = runCatching { NativeRdp.sendClipboard(handle, text) }.getOrDefault(false)

    fun resize(width: Int, height: Int): Boolean {
        NativeRdp.allocFrame(handle, width, height)
        ensureBitmap(width, height)
        return NativeRdp.resize(handle, width, height)
    }

    fun currentBitmap(): Bitmap? = bitmap?.takeIf { !it.isRecycled }

    fun handleForInput(): Long = handle

    fun close() {
        closing = true
        phase = Phase.Closed
        pumpJob?.cancel()
        statsJob?.cancel()
        if (handle != 0L) {
            Diagnostics.info("rdp", "closing session handle=$handle")
            runCatching { NativeRdp.disconnect(handle) }
            runCatching { NativeRdp.destroy(handle) }
            handle = 0
        }
        // Not recycled: the last drawn frame may still be referenced by Compose.
        bitmap = null
        scope.cancel()
    }

    private fun buttonDownFlag(button: Int): Int = when (button) {
        2 -> NativeRdp.PTR_DOWN_RIGHT
        3 -> NativeRdp.PTR_DOWN_MIDDLE
        else -> NativeRdp.PTR_DOWN_LEFT
    }

    private fun buttonUpFlag(button: Int): Int = when (button) {
        2 -> NativeRdp.PTR_UP_RIGHT
        3 -> NativeRdp.PTR_UP_MIDDLE
        else -> NativeRdp.PTR_UP_LEFT
    }

    companion object {
        const val FINGERPRINT_PREFIX = "rdp.fingerprint."
    }
}
