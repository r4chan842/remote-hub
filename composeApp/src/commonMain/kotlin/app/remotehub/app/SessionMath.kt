package app.remotehub.app

import app.remotehub.domain.ServerState
import kotlin.math.max
import kotlin.math.min

object SessionMath {
    fun remainingMillis(startedAtEpochMs: Long, durationMinutes: Int, nowEpochMs: Long): Long =
        max(0L, startedAtEpochMs + durationMinutes * 60_000L - nowEpochMs)

    fun remainingFraction(startedAtEpochMs: Long, durationMinutes: Int, nowEpochMs: Long): Float {
        val total = durationMinutes * 60_000L
        if (total <= 0) return 0f
        val left = remainingMillis(startedAtEpochMs, durationMinutes, nowEpochMs)
        return min(1f, max(0f, left.toFloat() / total.toFloat()))
    }

    fun stateFor(
        current: ServerState,
        startedAtEpochMs: Long,
        durationMinutes: Int,
        warningMinutes: Int,
        nowEpochMs: Long,
    ): ServerState {
        if (current == ServerState.Provisioning || current == ServerState.Stopping ||
            current == ServerState.Stopped || current == ServerState.Failed
        ) {
            return current
        }
        val left = remainingMillis(startedAtEpochMs, durationMinutes, nowEpochMs)
        if (left <= 0L) return ServerState.Stopped
        if (left <= warningMinutes * 60_000L) return ServerState.Expiring
        return current
    }

    fun formatDuration(millis: Long): String {
        if (millis <= 0) return "0m"
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble() / 1024
        var index = 0
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index++
        }
        val rounded = (value * 10).toLong() / 10.0
        return "$rounded ${units[index]}"
    }
}
