package app.remotehub.rdp

import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import app.remotehub.i18n.LocalStrings
import kotlin.math.roundToInt

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
    if (bitmap == null || bitmap.isRecycled) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(s.nativeEngine, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val image = remember(bitmap, frameTick) { bitmap.asImageBitmap() }
    val hostWidth = session.let { NativeRdp.width(it.handleForInput()) }.coerceAtLeast(1)
    val hostHeight = session.let { NativeRdp.height(it.handleForInput()) }.coerceAtLeast(1)

    fun mapX(x: Float): Int =
        if (viewSize.width == 0) 0 else (x / viewSize.width * hostWidth).roundToInt().coerceIn(0, hostWidth - 1)

    fun mapY(y: Float): Int =
        if (viewSize.height == 0) 0 else (y / viewSize.height * hostHeight).roundToInt().coerceIn(0, hostHeight - 1)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                viewSize = size
                cursorX = size.width / 2f
                cursorY = size.height / 2f
            }
            .pointerInput(trackpadMode, viewSize) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var lastX = down.position.x
                    var lastY = down.position.y
                    var moved = false
                    var startX = lastX
                    var startY = lastY
                    if (trackpadMode) {
                        session.sendPointer(NativeRdp.PTR_MOVE, mapX(cursorX), mapY(cursorY))
                    } else {
                        session.sendTouch(NativeRdp.TOUCH_DOWN, 0, mapX(lastX), mapY(lastY))
                    }
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        val dx = change.position.x - lastX
                        val dy = change.position.y - lastY
                        if (kotlin.math.abs(change.position.x - startX) > 12 ||
                            kotlin.math.abs(change.position.y - startY) > 12
                        ) {
                            moved = true
                        }
                        if (trackpadMode) {
                            cursorX = (cursorX + dx).coerceIn(0f, viewSize.width.toFloat())
                            cursorY = (cursorY + dy).coerceIn(0f, viewSize.height.toFloat())
                            session.sendPointer(NativeRdp.PTR_MOVE, mapX(cursorX), mapY(cursorY))
                        } else if (moved) {
                            session.sendTouch(NativeRdp.TOUCH_MOTION, 0, mapX(change.position.x), mapY(change.position.y))
                        }
                        lastX = change.position.x
                        lastY = change.position.y
                        change.consume()
                    } while (event.changes.any { it.pressed })

                    if (trackpadMode) {
                        if (!moved) {
                            session.sendPointer(NativeRdp.PTR_DOWN_LEFT, mapX(cursorX), mapY(cursorY))
                            session.sendPointer(NativeRdp.PTR_UP_LEFT, mapX(cursorX), mapY(cursorY))
                        }
                    } else {
                        session.sendTouch(NativeRdp.TOUCH_UP, 0, mapX(lastX), mapY(lastY))
                        if (!moved) {
                            session.sendPointer(NativeRdp.PTR_DOWN_LEFT, mapX(lastX), mapY(lastY))
                            session.sendPointer(NativeRdp.PTR_UP_LEFT, mapX(lastX), mapY(lastY))
                        }
                    }
                }
            },
    ) {
        drawImage(
            image = image,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        )
        if (trackpadMode) {
            drawCircle(
                color = androidx.compose.ui.graphics.Color(0xFFADC6FF),
                radius = 6f,
                center = androidx.compose.ui.geometry.Offset(cursorX, cursorY),
            )
        }
    }
}
