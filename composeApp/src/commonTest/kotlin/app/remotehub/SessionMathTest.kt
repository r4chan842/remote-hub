package app.remotehub

import app.remotehub.app.SessionMath
import app.remotehub.domain.ServerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionMathTest {
    private val start = 1_000_000L

    @Test
    fun remainingNeverGoesNegative() {
        assertEquals(0L, SessionMath.remainingMillis(start, 1, start + 120_000))
        assertEquals(60_000L, SessionMath.remainingMillis(start, 2, start + 60_000))
    }

    @Test
    fun fractionIsBounded() {
        assertEquals(1f, SessionMath.remainingFraction(start, 10, start))
        assertEquals(0.5f, SessionMath.remainingFraction(start, 10, start + 300_000))
        assertEquals(0f, SessionMath.remainingFraction(start, 10, start + 900_000))
        assertEquals(0f, SessionMath.remainingFraction(start, 0, start))
    }

    @Test
    fun warningThresholdFlipsStateToExpiring() {
        val state = SessionMath.stateFor(ServerState.Ready, start, 60, 15, start + 46 * 60_000)
        assertEquals(ServerState.Expiring, state)
    }

    @Test
    fun readyStaysReadyOutsideWarningWindow() {
        val state = SessionMath.stateFor(ServerState.Ready, start, 60, 15, start + 10 * 60_000)
        assertEquals(ServerState.Ready, state)
    }

    @Test
    fun elapsedWindowBecomesStopped() {
        assertEquals(ServerState.Stopped, SessionMath.stateFor(ServerState.Connected, start, 30, 5, start + 31 * 60_000))
    }

    @Test
    fun terminalAndTransientStatesArePreserved() {
        listOf(ServerState.Provisioning, ServerState.Stopping, ServerState.Stopped, ServerState.Failed).forEach {
            assertEquals(it, SessionMath.stateFor(it, start, 1, 5, start + 600_000))
        }
    }

    @Test
    fun durationFormatting() {
        assertEquals("0m", SessionMath.formatDuration(0))
        assertEquals("45s", SessionMath.formatDuration(45_000))
        assertEquals("2m 5s", SessionMath.formatDuration(125_000))
        assertEquals("1h 1m", SessionMath.formatDuration(3_660_000))
    }

    @Test
    fun byteFormatting() {
        assertEquals("512 B", SessionMath.formatBytes(512))
        assertEquals("1.0 KB", SessionMath.formatBytes(1024))
        assertEquals("1.5 MB", SessionMath.formatBytes(1_572_864))
    }

    @Test
    fun connectedSessionAlsoExpires() {
        val state = SessionMath.stateFor(ServerState.Connected, start, 20, 5, start + 16 * 60_000)
        assertEquals(ServerState.Expiring, state)
        assertTrue(SessionMath.remainingMillis(start, 20, start + 16 * 60_000) == 240_000L)
    }
}
