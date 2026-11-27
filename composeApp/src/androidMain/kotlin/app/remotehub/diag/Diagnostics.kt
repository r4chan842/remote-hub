package app.remotehub.diag

import android.app.ActivityManager
import android.app.Application
import android.annotation.TargetApi
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import app.remotehub.app.LogEntry
import app.remotehub.app.LogLevel
import app.remotehub.tsbridge.Tsbridge
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The app's black box on the Java side.
 *
 * It keeps a ring buffer for the log screen, appends every line to a file inside
 * app storage so the lines before a hard crash survive the process, imports the
 * system's record of why the previous process died (ApplicationExitInfo, API 30+),
 * and pulls the Go bridge's records — including the verbose tsnet log — into the
 * same timeline.
 *
 * Nothing here throws: a diagnostic path that can crash would defeat its purpose.
 */
object Diagnostics {
    private const val MAX_ENTRIES = 900
    private const val FILE_MAX_BYTES = 512L * 1024L
    private const val PREVIOUS_RUN_LINES = 80

    private val lock = Any()
    private val entries = ArrayDeque<LogEntry>()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private var context: Context? = null
    private var folder: File? = null
    private var file: File? = null
    private var seq = 0L
    private var nativeSeq = 0L
    private var installed = false
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    /** Set when the system says the previous process died abnormally. */
    var crashNotice: LogEntry? = null
        private set

    @Serializable
    private data class NativeRecord(
        val seq: Long = 0,
        val timeMillis: Long = 0,
        val level: String = "INFO",
        val source: String = "native",
        val message: String = "",
    )

    // ------------------------------------------------------------------ setup

    fun install(app: Application) {
        if (installed) return
        installed = true
        context = app
        val dir = File(app.filesDir, "diagnostics")
        runCatching { dir.mkdirs() }
        folder = dir
        file = File(dir, "app.log")

        // Point the Go side at the same folder before any node exists, so a
        // first-start failure is already on disk when the app is reopened.
        runCatching {
            Tsbridge.setLogDir(dir.absolutePath)
            Tsbridge.setAndroidVersion(androidMajor().toLong())
        }.onFailure { log(LogLevel.Error, "native", "native library unavailable", throwable = it) }

        log(
            LogLevel.Info, "app",
            "RemoteHub started · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} " +
                "(API ${Build.VERSION.SDK_INT}) · ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}",
        )
        importPreviousNativeRun(dir)
        importExitReasons(app)
        installCrashHandler()
        pullNative()
    }

    private fun androidMajor(): Int =
        Build.VERSION.RELEASE.takeWhile { it.isDigit() }.toIntOrNull()
            ?: if (Build.VERSION.SDK_INT >= 30) 11 else 10

    private fun installCrashHandler() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                log(
                    LogLevel.Error, "crash",
                    "uncaught ${error.javaClass.simpleName} on thread ${thread.name}: ${error.message ?: "no message"}",
                    throwable = error,
                )
            }
            // Hand over to the platform handler so the process still restarts cleanly.
            runCatching { previousHandler?.uncaughtException(thread, error) }
        }
    }

    /**
     * Android keeps the reason a process died. For a native abort the description
     * carries the signal and the abort message, which is the only trace left when
     * libgojni.so takes the process down.
     */
    private fun importExitReasons(app: Application) {
        if (Build.VERSION.SDK_INT < 30) return
        val manager = app.getSystemService(ActivityManager::class.java) ?: return
        val history = runCatching {
            manager.getHistoricalProcessExitReasons(app.packageName, 0, 8)
        }.getOrNull() ?: return
        val marker = File(folder ?: return, "exit-marker")
        val seen = runCatching { marker.readText().trim().toLong() }.getOrDefault(0L)
        var newest = seen
        history.sortedBy { it.timestamp }.forEach { info ->
            if (info.timestamp <= seen) return@forEach
            newest = maxOf(newest, info.timestamp)
            val reason = reasonName(info.reason)
            val fatal = when (info.reason) {
                ApplicationExitInfo.REASON_CRASH,
                ApplicationExitInfo.REASON_CRASH_NATIVE,
                ApplicationExitInfo.REASON_ANR,
                ApplicationExitInfo.REASON_LOW_MEMORY,
                ApplicationExitInfo.REASON_SIGNALED,
                -> true
                else -> false
            }
            val detail = buildString {
                append("reason=").append(reason)
                append(" · time=").append(stamp.format(Date(info.timestamp)))
                append(" · status=").append(info.status)
                append(" · importance=").append(info.importance)
                append(" · pss=").append(info.pss).append("kB")
                append(" · rss=").append(info.rss).append("kB")
                info.description?.let { append('\n').append(it) }
                if (Build.VERSION.SDK_INT >= 33) {
                    runCatching {
                        info.traceInputStream?.use { stream ->
                            val text = stream.readBytes().decodeToString().take(6000)
                            if (text.isNotBlank()) append('\n').append(text)
                        }
                    }
                }
            }
            log(
                if (fatal) LogLevel.Error else LogLevel.Info,
                "crash",
                "previous exit: $reason at ${stamp.format(Date(info.timestamp))}",
                detail = detail,
            )
            if (fatal) {
                synchronized(lock) { crashNotice = entries.lastOrNull() }
            }
        }
        runCatching { marker.writeText(newest.toString()) }
    }

    @TargetApi(Build.VERSION_CODES.R)
    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_UNKNOWN -> "UNKNOWN"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "JAVA_CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_CRASH"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INIT_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "PACKAGE_STATE_CHANGE"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
        else -> "REASON_$reason"
    }

    /**
     * The Go log file survives the process. Import the tail of the previous run so
     * the lines that led to a native crash are visible after reopening the app.
     */
    private fun importPreviousNativeRun(dir: File) {
        val native = File(dir, "native.log")
        if (!native.exists()) return
        val marker = File(dir, "native-marker")
        val seen = runCatching { marker.readText().trim().toLong() }.getOrDefault(0L)
        val lines = runCatching { native.readLines() }.getOrDefault(emptyList())
        // Everything before the current run's first "android release=" line is history.
        val boundary = lines.indexOfLast { it.contains("android release=") }
        val previous = if (boundary > 0) lines.subList(0, boundary) else emptyList()
        var newest = seen
        previous.takeLast(PREVIOUS_RUN_LINES).forEach { line ->
            val parts = line.split("\t")
            if (parts.size < 4) return@forEach
            val millis = parts[0].toLongOrNull() ?: return@forEach
            if (millis <= seen) return@forEach
            newest = maxOf(newest, millis)
            append(
                level = levelOf(parts[1]),
                source = "prev·${parts[2]}",
                message = parts[3],
                timeMillis = millis,
            )
        }
        if (newest > seen) runCatching { marker.writeText(newest.toString()) }
    }

    // ------------------------------------------------------------------ write

    fun debug(source: String, message: String) = log(LogLevel.Debug, source, message)

    fun info(source: String, message: String) = log(LogLevel.Info, source, message)

    fun warn(source: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.Warn, source, message, throwable = throwable)

    fun error(source: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.Error, source, message, throwable = throwable)

    fun log(level: LogLevel, source: String, message: String, detail: String = "", throwable: Throwable? = null) {
        val extra = buildString {
            if (detail.isNotBlank()) append(redact(detail))
            if (throwable != null) {
                if (isNotEmpty()) append('\n')
                append(redact(traceOf(throwable)))
            }
        }
        append(level, source, redact(message), extra, System.currentTimeMillis())
    }

    private fun append(level: LogLevel, source: String, message: String, detail: String = "", timeMillis: Long) {
        synchronized(lock) {
            seq += 1
            val entry = LogEntry(seq, timeMillis, level, source, message, detail)
            entries.addLast(entry)
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
            writeLine(entry)
        }
    }

    private fun writeLine(entry: LogEntry) {
        val target = file ?: return
        runCatching {
            if (target.length() > FILE_MAX_BYTES) {
                val previous = File(target.parentFile, "app.log.1")
                previous.delete()
                target.renameTo(previous)
            }
            val line = "${entry.timeMillis}\t${entry.level.name}\t${entry.source}\t" +
                entry.message.replace("\n", " ⏎ ") +
                (if (entry.detail.isBlank()) "" else "\t" + entry.detail.replace("\n", " ⏎ ")) + "\n"
            target.appendText(line)
        }
    }

    private fun traceOf(error: Throwable): String {
        val buffer = StringWriter()
        runCatching { error.printStackTrace(PrintWriter(buffer)) }
        return buffer.toString().take(8000)
    }

    // ------------------------------------------------------------------ native

    /** Pull new Go records into the same timeline. Cheap: only unseen sequence numbers. */
    fun pullNative() {
        val raw = runCatching { Tsbridge.logsJSON(nativeSeq, 400L) }.getOrNull() ?: return
        val records = runCatching { json.decodeFromString<List<NativeRecord>>(raw) }.getOrNull() ?: return
        if (records.isEmpty()) return
        synchronized(lock) {
            records.forEach { record ->
                if (record.seq > nativeSeq) nativeSeq = record.seq
                seq += 1
                val entry = LogEntry(
                    seq = seq,
                    timeMillis = if (record.timeMillis > 0) record.timeMillis else System.currentTimeMillis(),
                    level = levelOf(record.level),
                    source = record.source,
                    message = redact(record.message),
                )
                entries.addLast(entry)
                while (entries.size > MAX_ENTRIES) entries.removeFirst()
                writeLine(entry)
            }
        }
    }

    private fun levelOf(raw: String): LogLevel = when (raw.uppercase()) {
        "DEBUG" -> LogLevel.Debug
        "WARN", "WARNING" -> LogLevel.Warn
        "ERROR", "FATAL" -> LogLevel.Error
        else -> LogLevel.Info
    }

    // ------------------------------------------------------------------ read

    /** Newest first, filtered for the log screen. */
    fun snapshot(filter: String = "all", query: String = "", limit: Int = 400): List<LogEntry> {
        val copy = synchronized(lock) { entries.toList() }
        val needle = query.trim().lowercase()
        return copy.asReversed().filter { entry ->
            matchesFilter(entry, filter) &&
                (needle.isBlank() ||
                    entry.message.lowercase().contains(needle) ||
                    entry.source.lowercase().contains(needle) ||
                    entry.detail.lowercase().contains(needle))
        }.take(limit)
    }

    fun errorCount(): Int = synchronized(lock) { entries.count { it.level == LogLevel.Error } }

    private fun matchesFilter(entry: LogEntry, filter: String): Boolean = when (filter) {
        "error" -> entry.level == LogLevel.Error
        "warn" -> entry.level == LogLevel.Warn || entry.level == LogLevel.Error
        "network" -> entry.source in NETWORK_SOURCES
        "github" -> entry.source in GITHUB_SOURCES
        "rdp" -> entry.source in RDP_SOURCES
        "system" -> entry.source !in NETWORK_SOURCES && entry.source !in GITHUB_SOURCES && entry.source !in RDP_SOURCES
        else -> true
    }

    private val NETWORK_SOURCES = setOf(
        "tailscale", "tsnet", "node", "netmon", "env", "forward", "probe", "bridge", "health", "network",
    )
    private val GITHUB_SOURCES = setOf("github", "provision", "workflow", "backup")
    private val RDP_SOURCES = setOf("rdp", "freerdp", "desktop")

    fun environmentReport(): String = buildString {
        append("app: RemoteHub\n")
        append("device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n")
        append("android: ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}\n")
        append("build: ${Build.DISPLAY}\n")
        append("abi: ${Build.SUPPORTED_ABIS.joinToString()}\n")
        append("time: ${stamp.format(Date())}\n")
        val native = runCatching { Tsbridge.diagnosticsJSON() }.getOrNull()
        append("native: ").append(native ?: "unavailable").append('\n')
    }

    fun exportText(limit: Int = 600): String = buildString {
        append(environmentReport())
        append("\n---- log (oldest first) ----\n")
        val copy = synchronized(lock) { entries.toList() }
        copy.takeLast(limit).forEach { entry ->
            append(clock.format(Date(entry.timeMillis)))
            append(' ').append(entry.level.name)
            append(' ').append(entry.source)
            append(": ").append(entry.message).append('\n')
            if (entry.detail.isNotBlank()) append(entry.detail).append('\n')
        }
    }

    /** Writes a shareable file into the cache folder exposed by the FileProvider. */
    fun exportFile(): File? {
        val ctx = context ?: return null
        return runCatching {
            val dir = File(ctx.cacheDir, "diagnostics")
            dir.mkdirs()
            val target = File(dir, "remotehub-log-${System.currentTimeMillis()}.txt")
            target.writeText(exportText(MAX_ENTRIES))
            target
        }.getOrNull()
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            crashNotice = null
            nativeSeq = 0
            runCatching { file?.writeText("") }
            runCatching { File(folder, "app.log.1").delete() }
            runCatching { File(folder, "native-marker").delete() }
        }
        runCatching { Tsbridge.clearLogs() }
        info("app", "log cleared")
    }

    // ------------------------------------------------------------------ safety

    private val secrets = listOf(
        Regex("""tskey-[A-Za-z0-9\-]+"""),
        Regex("""github_pat_[A-Za-z0-9_]+"""),
        Regex("""gh[pousr]_[A-Za-z0-9]{8,}"""),
        Regex("""(?i)\b(password|passphrase|secret|authkey|auth_key|token)(\s*[=:]\s*)(\S+)"""),
    )

    /** The log is meant to be copied and shared, so credentials never enter it. */
    fun redact(text: String): String {
        var out = text
        out = secrets[0].replace(out, "tskey-***")
        out = secrets[1].replace(out, "github_pat_***")
        out = secrets[2].replace(out, "gh*_***")
        out = secrets[3].replace(out) { match ->
            match.groupValues[1] + match.groupValues[2] + "***"
        }
        return out
    }
}
