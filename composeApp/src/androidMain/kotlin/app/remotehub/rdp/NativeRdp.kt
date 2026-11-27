package app.remotehub.rdp

import android.graphics.Bitmap

interface RdpListener {
    fun onState(state: Int, message: String)
    fun onResize(width: Int, height: Int)
    fun onClipboard(utf8: ByteArray)
}

object NativeRdp {
    const val STATE_CONNECTING = 1
    const val STATE_CONNECTED = 2
    const val STATE_RECONNECTING = 3
    const val STATE_DISCONNECTED = 4
    const val STATE_FAILED = 5
    const val STATE_CERT_CHANGED = 6

    // Slow-path TS_POINTER_EVENT field (MS-RDPBCGR 2.2.8.1.1.3.1.1.3).
    // These used to hold the *extended* button bits (0x0001 / 0x0002), which are
    // reserved here. Windows answered ERRINFO_INVALID_INPUT_PDU_MOUSE (0x10D0)
    // and disconnected on the first click or drag.
    const val PTR_MOVE = 0x0800
    const val PTR_DOWN = 0x8000
    const val PTR_BUTTON1 = 0x1000
    const val PTR_BUTTON2 = 0x2000
    const val PTR_BUTTON3 = 0x4000
    const val PTR_DOWN_LEFT = PTR_DOWN or PTR_BUTTON1
    const val PTR_UP_LEFT = PTR_BUTTON1
    const val PTR_DOWN_RIGHT = PTR_DOWN or PTR_BUTTON2
    const val PTR_UP_RIGHT = PTR_BUTTON2
    const val PTR_DOWN_MIDDLE = PTR_DOWN or PTR_BUTTON3
    const val PTR_UP_MIDDLE = PTR_BUTTON3
    const val PTR_WHEEL = 0x0200
    const val PTR_HWHEEL = 0x0400
    const val PTR_WHEEL_NEGATIVE = 0x0100

    // Extended field, a different PDU: only back / forward buttons live here.
    const val PTR_XDOWN = 0x8000
    const val PTR_XBUTTON1 = 0x0001
    const val PTR_XBUTTON2 = 0x0002

    const val TOUCH_DOWN = 0x01
    const val TOUCH_UP = 0x02
    const val TOUCH_MOTION = 0x04

    private var loaded = false
    private var loadError: String? = null

    val available: Boolean
        get() {
            ensureLoaded()
            return loaded
        }

    val failureReason: String?
        get() {
            ensureLoaded()
            return loadError
        }

    @Synchronized
    private fun ensureLoaded() {
        if (loaded || loadError != null) return
        try {
            System.loadLibrary("remotehub-rdp")
            loaded = true
        } catch (error: UnsatisfiedLinkError) {
            loadError = error.message ?: "native library missing"
        }
    }

    fun create(listener: RdpListener): Long {
        if (!available) return 0L
        return nativeCreate(listener)
    }

    fun connect(
        handle: Long,
        host: String,
        port: Int,
        username: String,
        password: String,
        domain: String,
        width: Int,
        height: Int,
        colorDepth: Int,
        nla: Boolean,
        graphicsPipeline: Boolean,
        h264: Boolean,
        clipboard: Boolean,
        audio: Boolean,
        pinnedFingerprint: String,
        quality: Int,
    ): Boolean {
        if (!available || handle == 0L) return false
        return nativeConnect(
            handle, host, port, username, password, domain, width, height, colorDepth,
            nla, graphicsPipeline, h264, clipboard, audio, pinnedFingerprint, quality,
        )
    }

    fun allocFrame(handle: Long, width: Int, height: Int): Boolean =
        available && handle != 0L && nativeAllocFrame(handle, width, height)

    fun render(handle: Long, bitmap: Bitmap): Int =
        if (available && handle != 0L) nativeRender(handle, bitmap) else -1

    fun mouse(handle: Long, flags: Int, x: Int, y: Int) {
        if (available && handle != 0L) nativeMouse(handle, flags, x, y)
    }

    fun wheel(handle: Long, flags: Int, x: Int, y: Int) {
        if (available && handle != 0L) nativeWheel(handle, flags, x, y)
    }

    /** Back / forward buttons. These use the extended PDU, unlike the wheel. */
    fun xButton(handle: Long, flags: Int, x: Int, y: Int) {
        if (available && handle != 0L) nativeXButton(handle, flags, x, y)
    }

    fun touch(handle: Long, flags: Int, finger: Int, pressure: Int, x: Int, y: Int) {
        if (available && handle != 0L) nativeTouch(handle, flags, finger, pressure, x, y)
    }

    fun key(handle: Long, scancode: Int, down: Boolean) {
        if (available && handle != 0L) nativeKey(handle, scancode, down)
    }

    fun unicode(handle: Long, code: Int) {
        if (available && handle != 0L) nativeUnicode(handle, code)
    }

    fun sync(handle: Long, flags: Int) {
        if (available && handle != 0L) nativeSync(handle, flags)
    }

    fun resize(handle: Long, width: Int, height: Int): Boolean =
        available && handle != 0L && nativeResize(handle, width, height)

    fun sendClipboard(handle: Long, text: String): Boolean =
        available && handle != 0L && nativeSendClipboard(handle, text)

    /**
     * { roundTripMillis, bytesIn, bytesOut } read from the engine itself
     * (rdpContext::autodetect and freerdp_get_stats), so the UI shows measured
     * latency and real protocol throughput instead of an estimate.
     */
    fun netStats(handle: Long): LongArray =
        if (available && handle != 0L) runCatching { nativeNetStats(handle) }.getOrDefault(LongArray(3))
        else LongArray(3)

    fun frames(handle: Long): Long = if (available && handle != 0L) nativeFrames(handle) else 0L
    fun width(handle: Long): Int = if (available && handle != 0L) nativeWidth(handle) else 0
    fun height(handle: Long): Int = if (available && handle != 0L) nativeHeight(handle) else 0
    fun connected(handle: Long): Boolean = available && handle != 0L && nativeConnected(handle)
    fun lastError(handle: Long): Int = if (available && handle != 0L) nativeLastError(handle) else 0
    fun fingerprint(handle: Long): String = if (available && handle != 0L) nativeFingerprint(handle) else ""
    fun uptime(handle: Long): Long = if (available && handle != 0L) nativeUptime(handle) else 0L

    fun disconnect(handle: Long) {
        if (available && handle != 0L) nativeDisconnect(handle)
    }

    fun destroy(handle: Long) {
        if (available && handle != 0L) nativeDestroy(handle)
    }

    fun version(): String = if (available) nativeVersion() else "unavailable"

    fun setLogLevel(level: Int) {
        if (available) nativeSetLogLevel(level)
    }

    /**
     * FreeRDP's own diagnostics. WinPR writes them to stderr, which an Android app
     * process does not have, so the bridge captures them and they are drained here
     * into the app log. Without this a codec or channel failure is invisible.
     */
    fun drainEngineLog(): String = if (available) runCatching { nativeDrainEngineLog() }.getOrDefault("") else "" 

    @JvmStatic private external fun nativeCreate(listener: RdpListener): Long
    @JvmStatic private external fun nativeConnect(
        handle: Long, host: String, port: Int, username: String, password: String, domain: String,
        width: Int, height: Int, colorDepth: Int, nla: Boolean, gfx: Boolean, h264: Boolean,
        clipboard: Boolean, audio: Boolean, pinnedFingerprint: String, quality: Int,
    ): Boolean
    @JvmStatic private external fun nativeAllocFrame(handle: Long, width: Int, height: Int): Boolean
    @JvmStatic private external fun nativeRender(handle: Long, bitmap: Bitmap): Int
    @JvmStatic private external fun nativeMouse(handle: Long, flags: Int, x: Int, y: Int)
    @JvmStatic private external fun nativeWheel(handle: Long, flags: Int, x: Int, y: Int)
    @JvmStatic private external fun nativeXButton(handle: Long, flags: Int, x: Int, y: Int)
    @JvmStatic private external fun nativeTouch(handle: Long, flags: Int, finger: Int, pressure: Int, x: Int, y: Int)
    @JvmStatic private external fun nativeKey(handle: Long, scancode: Int, down: Boolean)
    @JvmStatic private external fun nativeUnicode(handle: Long, code: Int)
    @JvmStatic private external fun nativeSync(handle: Long, flags: Int)
    @JvmStatic private external fun nativeResize(handle: Long, width: Int, height: Int): Boolean
    @JvmStatic private external fun nativeSendClipboard(handle: Long, text: String): Boolean
    @JvmStatic private external fun nativeNetStats(handle: Long): LongArray
    @JvmStatic private external fun nativeFrames(handle: Long): Long
    @JvmStatic private external fun nativeWidth(handle: Long): Int
    @JvmStatic private external fun nativeHeight(handle: Long): Int
    @JvmStatic private external fun nativeConnected(handle: Long): Boolean
    @JvmStatic private external fun nativeLastError(handle: Long): Int
    @JvmStatic private external fun nativeFingerprint(handle: Long): String
    @JvmStatic private external fun nativeUptime(handle: Long): Long
    @JvmStatic private external fun nativeDisconnect(handle: Long)
    @JvmStatic private external fun nativeDestroy(handle: Long)
    @JvmStatic private external fun nativeVersion(): String
    @JvmStatic private external fun nativeDrainEngineLog(): String
    @JvmStatic private external fun nativeSetLogLevel(level: Int)
}
