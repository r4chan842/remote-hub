package app.remotehub.rdp

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.remotehub.i18n.LocalStrings
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.withTimeoutOrNull

private const val LONG_PRESS_MS = 450L
private const val DOUBLE_TAP_MS = 320L
private const val TAP_MS = 260L
private const val SCROLL_STEP_PX = 26f
private const val TRACKPAD_GAIN = 1.6f
private const val WHEEL_NOTCH = 120
private const val MAX_ZOOM = 5f

/**
 * The remote desktop, its viewport and the full mobile pointer vocabulary.
 *
 * Gestures follow Microsoft's published RD Client / Windows App tables:
 *
 * Direct touch — tap: left click · hold then release: right click · drag: left
 * drag · hold then drag: right drag · two fingers: wheel · pinch: zoom.
 *
 * Mouse pointer — one finger drag: move · tap: left click at the pointer ·
 * two-finger tap or hold: right click · double-tap and hold: left drag ·
 * two-finger double-tap and hold: right drag · two fingers: wheel · pinch: zoom.
 *
 * Every event is an explicit slow-path mouse PDU. `freerdp_client_handle_touch`
 * is deliberately not used: without RDPEI it synthesises a click of its own, so
 * combining it with an explicit click sent two clicks per tap.
 */
@Composable
fun RdpSurface(session: RdpSession?, trackpadMode: Boolean, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val haptics = LocalHapticFeedback.current
    var frameTick by remember { mutableIntStateOf(0) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var cursorX by remember { mutableFloatStateOf(0f) }
    var cursorY by remember { mutableFloatStateOf(0f) }
    var zoom by remember(session) { mutableFloatStateOf(1f) }
    var panX by remember(session) { mutableFloatStateOf(0f) }
    var panY by remember(session) { mutableFloatStateOf(0f) }

    if (session == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    remember(session) {
        session.onFrame = { frameTick++ }
        true
    }

    val bitmap: Bitmap? = session.currentBitmap()
    if (bitmap == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(s.nativeEngine, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val image = remember(bitmap, frameTick) { bitmap.asImageBitmap() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                val first = viewSize == IntSize.Zero
                viewSize = size
                if (first) {
                    cursorX = size.width / 2f
                    cursorY = size.height / 2f
                } else {
                    cursorX = cursorX.coerceIn(0f, size.width.toFloat())
                    cursorY = cursorY.coerceIn(0f, size.height.toFloat())
                }
            }
            // Keyed on the input mode only: keying on viewSize restarted the
            // detector on every layout pass and dropped in-flight drags.
            .pointerInput(trackpadMode) {
                val slop = viewConfiguration.touchSlop
                var lastTapAt = 0L
                var lastTapX = 0f
                var lastTapY = 0f
                var lastMultiTapAt = 0L

                fun clampPan() {
                    val minX = viewSize.width * (1f - zoom)
                    val minY = viewSize.height * (1f - zoom)
                    panX = panX.coerceIn(minX, 0f)
                    panY = panY.coerceIn(minY, 0f)
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    val hostWidth = NativeRdp.width(session.handleForInput()).coerceAtLeast(1)
                    val hostHeight = NativeRdp.height(session.handleForInput()).coerceAtLeast(1)

                    // View -> host, undoing the local zoom and pan first.
                    fun hx(x: Float): Int {
                        val w = viewSize.width * zoom
                        return if (w <= 0f) 0
                        else ((x - panX) / w * hostWidth).roundToInt().coerceIn(0, hostWidth - 1)
                    }

                    fun hy(y: Float): Int {
                        val h = viewSize.height * zoom
                        return if (h <= 0f) 0
                        else ((y - panY) / h * hostHeight).roundToInt().coerceIn(0, hostHeight - 1)
                    }

                    val startX = down.position.x
                    val startY = down.position.y
                    var lastX = startX
                    var lastY = startY
                    var moved = false
                    var longPressed = false
                    var dragButton = 0
                    var multi = false
                    var pinched = false
                    var handled = false
                    var threeFinger = false
                    var centroidX = startX
                    var centroidY = startY
                    var spread = 0f
                    var scrollAccX = 0f
                    var scrollAccY = 0f
                    var travel = 0f
                    val startedAt = SystemClock.uptimeMillis()
                    val longPressAt = startedAt + LONG_PRESS_MS
                    val nearLastTap = startedAt - lastTapAt < DOUBLE_TAP_MS &&
                        hypot(startX - lastTapX, startY - lastTapY) < slop * 2.5f
                    // Mouse pointer: the second tap of a double tap, held, drags.
                    val leftDragArmed = trackpadMode && nearLastTap

                    fun targetX() = if (trackpadMode) hx(cursorX) else hx(lastX)
                    fun targetY() = if (trackpadMode) hy(cursorY) else hy(lastY)

                    if (trackpadMode) session.sendMove(hx(cursorX), hy(cursorY))

                    gesture@ while (true) {
                        val remaining = longPressAt - SystemClock.uptimeMillis()
                        val armTimeout = !moved && !multi && !longPressed && dragButton == 0 &&
                            !leftDragArmed && remaining > 0
                        val event = if (armTimeout) {
                            withTimeoutOrNull(remaining) { awaitPointerEvent() }
                        } else {
                            awaitPointerEvent()
                        }

                        if (event == null) {
                            // Held still. Do not click yet: releasing means right
                            // click, dragging means right drag. That is exactly the
                            // RD Client behaviour for both pointer modes.
                            longPressed = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            continue@gesture
                        }

                        val pressed = event.changes.filter { it.pressed }

                        if (pressed.isEmpty()) {
                            val now = SystemClock.uptimeMillis()
                            when {
                                dragButton != 0 -> {
                                    session.sendButtonUp(dragButton, targetX(), targetY())
                                    dragButton = 0
                                }
                                threeFinger -> {
                                    if (travel < slop * 2f) {
                                        zoom = 1f
                                        panX = 0f
                                        panY = 0f
                                    }
                                }
                                multi -> {
                                    if (!handled && !pinched && travel < slop &&
                                        now - startedAt < TAP_MS
                                    ) {
                                        session.sendRightClick(targetX(), targetY())
                                        lastMultiTapAt = now
                                    }
                                }
                                longPressed -> session.sendRightClick(targetX(), targetY())
                                else -> {
                                    // Snap a repeat tap onto the previous pixel so the
                                    // host resolves two clicks as a double click.
                                    val repeatTap = now - lastTapAt < DOUBLE_TAP_MS &&
                                        hypot(startX - lastTapX, startY - lastTapY) < slop * 2.5f
                                    val ax = when {
                                        repeatTap -> lastTapX
                                        trackpadMode -> cursorX
                                        else -> lastX
                                    }
                                    val ay = when {
                                        repeatTap -> lastTapY
                                        trackpadMode -> cursorY
                                        else -> lastY
                                    }
                                    session.sendClick(hx(ax), hy(ay))
                                    lastTapAt = now
                                    lastTapX = if (trackpadMode) cursorX else startX
                                    lastTapY = if (trackpadMode) cursorY else startY
                                }
                            }
                            event.changes.forEach { it.consume() }
                            break@gesture
                        }

                        if (pressed.size >= 3) threeFinger = true

                        if (pressed.size >= 2 || multi) {
                            val cx = pressed.map { it.position.x }.average().toFloat()
                            val cy = pressed.map { it.position.y }.average().toFloat()
                            val dist = pressed.map {
                                hypot(it.position.x - cx, it.position.y - cy)
                            }.average().toFloat()

                            if (!multi) {
                                multi = true
                                if (dragButton != 0) {
                                    session.sendButtonUp(dragButton, targetX(), targetY())
                                    dragButton = 0
                                }
                                centroidX = cx
                                centroidY = cy
                                spread = dist
                                // Mouse pointer: two fingers right after a two-finger
                                // tap, held, become a right-button drag.
                                if (trackpadMode && startedAt - lastMultiTapAt < DOUBLE_TAP_MS) {
                                    dragButton = 2
                                    session.sendButtonDown(2, hx(cursorX), hy(cursorY))
                                }
                                event.changes.forEach { it.consume() }
                                continue@gesture
                            }

                            val dx = cx - centroidX
                            val dy = cy - centroidY
                            centroidX = cx
                            centroidY = cy
                            travel += abs(dx) + abs(dy)

                            if (dragButton == 2) {
                                if (trackpadMode) {
                                    cursorX = (cursorX + dx * TRACKPAD_GAIN)
                                        .coerceIn(0f, viewSize.width.toFloat())
                                    cursorY = (cursorY + dy * TRACKPAD_GAIN)
                                        .coerceIn(0f, viewSize.height.toFloat())
                                }
                                session.sendMove(targetX(), targetY())
                                event.changes.forEach { it.consume() }
                                continue@gesture
                            }

                            val ratio = if (spread > 1f && dist > 1f) dist / spread else 1f
                            spread = dist

                            if (abs(ratio - 1f) > 0.015f) {
                                // Pinch: zoom the local view around the centroid.
                                pinched = true
                                val next = (zoom * ratio).coerceIn(1f, MAX_ZOOM)
                                val effective = next / zoom
                                panX = cx - (cx - panX) * effective
                                panY = cy - (cy - panY) * effective
                                zoom = next
                                clampPan()
                                handled = true
                            } else if (zoom > 1.01f) {
                                // Zoomed in: two fingers pan the viewport.
                                panX += dx
                                panY += dy
                                clampPan()
                                handled = true
                            } else {
                                scrollAccX += dx
                                scrollAccY += dy
                                val stepsX = (scrollAccX / SCROLL_STEP_PX).toInt()
                                val stepsY = (scrollAccY / SCROLL_STEP_PX).toInt()
                                if (stepsX != 0 || stepsY != 0) {
                                    scrollAccX -= stepsX * SCROLL_STEP_PX
                                    scrollAccY -= stepsY * SCROLL_STEP_PX
                                    val tx = if (trackpadMode) hx(cursorX) else hx(cx)
                                    val ty = if (trackpadMode) hy(cursorY) else hy(cy)
                                    // Touch: content follows the fingers.
                                    // Mouse pointer: the Windows touchpad convention.
                                    val sign = if (trackpadMode) -1 else 1
                                    if (stepsY != 0) session.sendWheel(sign * stepsY * WHEEL_NOTCH, tx, ty)
                                    if (stepsX != 0) {
                                        session.sendWheel(sign * stepsX * WHEEL_NOTCH, tx, ty, horizontal = true)
                                    }
                                    handled = true
                                }
                            }
                            event.changes.forEach { it.consume() }
                            continue@gesture
                        }

                        val change = pressed.first()
                        val px = change.position.x
                        val py = change.position.y
                        val dx = px - lastX
                        val dy = py - lastY
                        lastX = px
                        lastY = py
                        travel += abs(dx) + abs(dy)

                        if (!moved && (abs(px - startX) > slop || abs(py - startY) > slop)) {
                            moved = true
                            dragButton = when {
                                longPressed -> 2
                                !trackpadMode -> 1
                                leftDragArmed -> 1
                                else -> 0
                            }
                            if (dragButton != 0) {
                                val ax = if (trackpadMode) hx(cursorX) else hx(startX)
                                val ay = if (trackpadMode) hy(cursorY) else hy(startY)
                                session.sendButtonDown(dragButton, ax, ay)
                            }
                        }

                        if (trackpadMode) {
                            cursorX = (cursorX + dx * TRACKPAD_GAIN).coerceIn(0f, viewSize.width.toFloat())
                            cursorY = (cursorY + dy * TRACKPAD_GAIN).coerceIn(0f, viewSize.height.toFloat())
                            session.sendMove(hx(cursorX), hy(cursorY))
                        } else if (dragButton != 0) {
                            session.sendMove(hx(px), hy(py))
                        }
                        change.consume()
                    }
                }
            },
    ) {
        val dw = (size.width * zoom).roundToInt().coerceAtLeast(1)
        val dh = (size.height * zoom).roundToInt().coerceAtLeast(1)
        drawImage(
            image = image,
            dstOffset = IntOffset(panX.roundToInt(), panY.roundToInt()),
            dstSize = IntSize(dw, dh),
        )
        if (trackpadMode) {
            val centre = Offset(cursorX, cursorY)
            drawCircle(color = Color(0x66000000), radius = 11f, center = centre)
            drawCircle(color = Color(0xFFADC6FF), radius = 9f, center = centre, style = Stroke(width = 2f))
            drawCircle(color = Color(0xFFADC6FF), radius = 2.5f, center = centre)
        }
    }
}
