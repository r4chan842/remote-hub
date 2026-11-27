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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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

/**
 * The remote desktop and its pointer translation.
 *
 * Input used to go through `freerdp_client_handle_touch`, which synthesises a mouse
 * click of its own when RDPEI is not connected, *and* an explicit left click was sent
 * afterwards. Every tap therefore arrived as two clicks, so a single tap opened files
 * and a double click was four clicks. All gestures are now explicit mouse events.
 *
 * Touch mode (direct):      tap = click · double tap = open · hold = right click ·
 *                           drag = left drag · two fingers = scroll · two-finger tap = right click
 * Trackpad mode (relative): move = pointer · tap = click · double tap + hold = drag ·
 *                           two-finger tap = right click · two fingers = scroll
 */
@Composable
fun RdpSurface(session: RdpSession?, trackpadMode: Boolean, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    var frameTick by remember { mutableIntStateOf(0) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var cursorX by remember { mutableStateOf(0f) }
    var cursorY by remember { mutableStateOf(0f) }

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
            // Keyed on the input mode only. Including viewSize restarted the gesture
            // detector on every layout pass, which dropped in-flight drags.
            .pointerInput(trackpadMode) {
                val slop = viewConfiguration.touchSlop
                var lastTapAt = 0L
                var lastTapX = 0f
                var lastTapY = 0f

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    // Resolution is read once per gesture: it cannot change meaningfully
                    // mid-stroke, and this keeps JNI off the per-event path.
                    val hostWidth = NativeRdp.width(session.handleForInput()).coerceAtLeast(1)
                    val hostHeight = NativeRdp.height(session.handleForInput()).coerceAtLeast(1)

                    fun hx(x: Float): Int {
                        val w = viewSize.width
                        return if (w <= 0) 0 else (x / w * hostWidth).roundToInt().coerceIn(0, hostWidth - 1)
                    }

                    fun hy(y: Float): Int {
                        val h = viewSize.height
                        return if (h <= 0) 0 else (y / h * hostHeight).roundToInt().coerceIn(0, hostHeight - 1)
                    }

                    val startX = down.position.x
                    val startY = down.position.y
                    var lastX = startX
                    var lastY = startY
                    var moved = false
                    var dragging = false
                    var handled = false
                    var scrollMode = false
                    var centroidX = startX
                    var centroidY = startY
                    var scrollAccX = 0f
                    var scrollAccY = 0f
                    var scrollTravel = 0f
                    val startedAt = SystemClock.uptimeMillis()
                    val longPressAt = startedAt + LONG_PRESS_MS
                    val nearLastTap = startedAt - lastTapAt < DOUBLE_TAP_MS &&
                        hypot(startX - lastTapX, startY - lastTapY) < slop * 2.5f
                    // Second tap of a double tap that is held becomes a drag, which is
                    // how a touchpad moves a window or extends a selection.
                    val tapDragArmed = trackpadMode && nearLastTap

                    if (trackpadMode) session.sendMove(hx(cursorX), hy(cursorY))

                    gesture@ while (true) {
                        val remaining = longPressAt - SystemClock.uptimeMillis()
                        val armTimeout = !moved && !scrollMode && !dragging && !handled &&
                            !tapDragArmed && remaining > 0
                        val event = if (armTimeout) {
                            withTimeoutOrNull(remaining) { awaitPointerEvent() }
                        } else {
                            awaitPointerEvent()
                        }

                        if (event == null) {
                            // Held without moving: right click, then swallow the rest of
                            // the touch so releasing does not also emit a left click.
                            val tx = if (trackpadMode) hx(cursorX) else hx(startX)
                            val ty = if (trackpadMode) hy(cursorY) else hy(startY)
                            session.sendRightClick(tx, ty)
                            handled = true
                            while (true) {
                                val tail = awaitPointerEvent()
                                tail.changes.forEach { it.consume() }
                                if (tail.changes.none { it.pressed }) break
                            }
                            break@gesture
                        }

                        val pressed = event.changes.filter { it.pressed }

                        if (pressed.size >= 2) {
                            if (!scrollMode) {
                                scrollMode = true
                                if (dragging) {
                                    session.sendButtonUp(1, hx(lastX), hy(lastY))
                                    dragging = false
                                }
                                centroidX = pressed.map { it.position.x }.average().toFloat()
                                centroidY = pressed.map { it.position.y }.average().toFloat()
                            }
                            val cx = pressed.map { it.position.x }.average().toFloat()
                            val cy = pressed.map { it.position.y }.average().toFloat()
                            val dx = cx - centroidX
                            val dy = cy - centroidY
                            centroidX = cx
                            centroidY = cy
                            scrollTravel += abs(dx) + abs(dy)
                            scrollAccX += dx
                            scrollAccY += dy
                            val stepsX = (scrollAccX / SCROLL_STEP_PX).toInt()
                            val stepsY = (scrollAccY / SCROLL_STEP_PX).toInt()
                            if (stepsX != 0 || stepsY != 0) {
                                scrollAccX -= stepsX * SCROLL_STEP_PX
                                scrollAccY -= stepsY * SCROLL_STEP_PX
                                val tx = if (trackpadMode) hx(cursorX) else hx(centroidX)
                                val ty = if (trackpadMode) hy(cursorY) else hy(centroidY)
                                // Touch: content follows the fingers. Trackpad: the
                                // Windows convention, fingers down scrolls down.
                                val sign = if (trackpadMode) -1 else 1
                                if (stepsY != 0) session.sendWheel(sign * stepsY * WHEEL_NOTCH, tx, ty)
                                if (stepsX != 0) {
                                    session.sendWheel(sign * stepsX * WHEEL_NOTCH, tx, ty, horizontal = true)
                                }
                                handled = true
                            }
                            event.changes.forEach { it.consume() }
                            continue@gesture
                        }

                        if (pressed.isEmpty()) {
                            val now = SystemClock.uptimeMillis()
                            when {
                                dragging -> {
                                    val tx = if (trackpadMode) hx(cursorX) else hx(lastX)
                                    val ty = if (trackpadMode) hy(cursorY) else hy(lastY)
                                    session.sendButtonUp(1, tx, ty)
                                    handled = true
                                }
                                scrollMode -> {
                                    if (!handled && scrollTravel < slop && now - startedAt < TAP_MS) {
                                        val tx = if (trackpadMode) hx(cursorX) else hx(centroidX)
                                        val ty = if (trackpadMode) hy(cursorY) else hy(centroidY)
                                        session.sendRightClick(tx, ty)
                                    }
                                    handled = true
                                }
                                !handled -> {
                                    // Snap a second tap onto the first tap's pixel so the
                                    // host sees two clicks at identical coordinates and
                                    // resolves them as a double click.
                                    val repeatTap = now - lastTapAt < DOUBLE_TAP_MS &&
                                        hypot(startX - lastTapX, startY - lastTapY) < slop * 2.5f
                                    val anchorX = if (repeatTap) lastTapX else if (trackpadMode) cursorX else lastX
                                    val anchorY = if (repeatTap) lastTapY else if (trackpadMode) cursorY else lastY
                                    session.sendClick(hx(anchorX), hy(anchorY))
                                    lastTapAt = now
                                    lastTapX = if (trackpadMode) cursorX else startX
                                    lastTapY = if (trackpadMode) cursorY else startY
                                }
                            }
                            event.changes.forEach { it.consume() }
                            break@gesture
                        }

                        val change = pressed.first()
                        val px = change.position.x
                        val py = change.position.y
                        val dx = px - lastX
                        val dy = py - lastY
                        lastX = px
                        lastY = py

                        if (!moved && (abs(px - startX) > slop || abs(py - startY) > slop)) {
                            moved = true
                            if (!trackpadMode || tapDragArmed) {
                                val ax = if (trackpadMode) hx(cursorX) else hx(startX)
                                val ay = if (trackpadMode) hy(cursorY) else hy(startY)
                                session.sendButtonDown(1, ax, ay)
                                dragging = true
                            }
                        }

                        if (trackpadMode) {
                            cursorX = (cursorX + dx * TRACKPAD_GAIN).coerceIn(0f, viewSize.width.toFloat())
                            cursorY = (cursorY + dy * TRACKPAD_GAIN).coerceIn(0f, viewSize.height.toFloat())
                            session.sendMove(hx(cursorX), hy(cursorY))
                        } else if (dragging) {
                            session.sendMove(hx(px), hy(py))
                        }
                        change.consume()
                    }
                }
            },
    ) {
        drawImage(
            image = image,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        )
        if (trackpadMode) {
            val centre = Offset(cursorX, cursorY)
            drawCircle(color = Color(0x66000000), radius = 11f, center = centre)
            drawCircle(color = Color(0xFFADC6FF), radius = 9f, center = centre, style = Stroke(width = 2f))
            drawCircle(color = Color(0xFFADC6FF), radius = 2.5f, center = centre)
        }
    }
}
