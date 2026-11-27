@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.remotehub.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.remotehub.design.CornerRadius
import app.remotehub.design.ExpressiveShapes
import app.remotehub.design.Symbol
import app.remotehub.design.Symbols
import app.remotehub.domain.ServerSession
import app.remotehub.i18n.LocalStrings

data class RemoteMetrics(
    val latencyMillis: Int,
    val framesPerSecond: Int,
    val kilobitsPerSecond: Int,
    val resolutionLabel: String,
    val trackpadMode: Boolean,
    val connecting: Boolean,
)

/** The desktop is drawn on this colour, so an empty frame is never a white flash. */
private val DesktopBlack = Color(0xFF07080A)
private val Chrome = Color(0xFF14161B)
private val ChromeHigh = Color(0xFF1E2128)
private val ChromeText = Color(0xFFE6E8EE)
private val ChromeMuted = Color(0xFF9AA1AE)

/**
 * The remote desktop screen.
 *
 * It is deliberately chrome-free: the session owns the display, the bars sit on top
 * of it and disappear in fullscreen. The desktop keeps the host's aspect ratio
 * instead of being stretched into a card, which is what made the old screen look
 * like a web page rather than a remote session.
 */
@Composable
fun RemoteContent(
    server: ServerSession,
    metrics: RemoteMetrics,
    onDisconnect: () -> Unit,
    onKeyboard: () -> Unit,
    onClipboard: () -> Unit,
    onCtrlAltDel: () -> Unit,
    onRightClick: () -> Unit,
    onToggleInputMode: () -> Unit,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    fullscreen: Boolean = false,
    connecting: Boolean = false,
    rightClickArmed: Boolean = false,
    hostAspectRatio: Float = 16f / 9f,
    desktopSurface: (@Composable () -> Unit)? = null,
) {
    val s = LocalStrings.current
    val busy = connecting || metrics.connecting

    Box(modifier = modifier.fillMaxSize().background(DesktopBlack)) {
        // ------------------------------------------------------------- desktop
        // The host resolution decides the shape. The desktop is fitted inside the
        // available space and centred, so pixels stay square instead of being
        // stretched into whatever rectangle the phone happens to have.
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val ratio = hostAspectRatio.coerceIn(0.5f, 3.2f)
            val available = maxWidth.value to maxHeight.value
            val byWidth = available.second * ratio
            val fittedWidth = if (byWidth <= available.first) byWidth else available.first
            val fittedHeight = fittedWidth / ratio
            Box(
                Modifier.size(fittedWidth.dp, fittedHeight.dp).background(DesktopBlack),
                contentAlignment = Alignment.Center,
            ) {
                if (desktopSurface != null) desktopSurface()
            }
        }

        // --------------------------------------------------------- login overlay
        if (busy) {
            Box(
                Modifier.fillMaxSize().background(DesktopBlack.copy(alpha = 0.86f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.widthIn(max = 320.dp).padding(24.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(76.dp)) {
                        CircularProgressIndicator(
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = ChromeHigh,
                        )
                        Symbol(Symbols.desktopWindows, size = 30.dp, tint = ChromeText)
                    }
                    Text(
                        s.remoteConnecting,
                        style = MaterialTheme.typography.titleSmall,
                        color = ChromeText,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        s.remoteHandshake,
                        style = MaterialTheme.typography.bodySmall,
                        color = ChromeMuted,
                        textAlign = TextAlign.Center,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SoftTag(metrics.resolutionLabel)
                        SoftTag("TLS · NLA")
                    }
                }
            }
        }

        // ------------------------------------------------------------ top chrome
        AnimatedVisibility(visible = !fullscreen, enter = fadeIn(), exit = fadeOut()) {
            Surface(color = Chrome.copy(alpha = 0.94f), contentColor = ChromeText) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        onClick = onDisconnect,
                        shape = ExpressiveShapes.circle,
                        color = ChromeHigh,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Symbol(Symbols.close, size = 18.dp, tint = ChromeText)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(server.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(
                            if (busy) s.remoteConnecting else "${s.connectedTo} ${server.connection?.address ?: "—"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ChromeMuted,
                            maxLines = 1,
                        )
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MetricPill("${metrics.latencyMillis}", "ms", metrics.latencyMillis in 1..220)
                            MetricPill("${metrics.framesPerSecond}", "fps", metrics.framesPerSecond > 0)
                            MetricPill("${metrics.kilobitsPerSecond}", "kbps", metrics.kilobitsPerSecond > 0)
                        }
                    }
                }
            }
        }

        // --------------------------------------------------------- bottom toolbar
        AnimatedVisibility(
            visible = !fullscreen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(color = Chrome.copy(alpha = 0.96f), shape = CornerRadius.barTop, contentColor = ChromeText) {
                // Two rows of three. Six tools in one row squeezed every label to
                // ~100px on a phone and truncated "Ctrl+Alt+Del".
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RemoteTool(Symbols.keyboard, s.keyboard, onKeyboard, Modifier.weight(1f), ExpressiveShapes.cookie6)
                        RemoteTool(
                            Symbols.rightClick,
                            s.rightClick,
                            onRightClick,
                            Modifier.weight(1f),
                            ExpressiveShapes.diamond,
                            active = rightClickArmed,
                        )
                        RemoteTool(Symbols.contentCopy, s.clipboard, onClipboard, Modifier.weight(1f), ExpressiveShapes.clover4)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RemoteTool(Symbols.cable, s.ctrlAltDel, onCtrlAltDel, Modifier.weight(1f), ExpressiveShapes.gem)
                        RemoteTool(
                            if (metrics.trackpadMode) Symbols.mouse else Symbols.touchApp,
                            if (metrics.trackpadMode) s.trackpadMode else s.touchMode,
                            onToggleInputMode,
                            Modifier.weight(1f),
                            ExpressiveShapes.sunny,
                            active = metrics.trackpadMode,
                        )
                        RemoteTool(Symbols.fullscreen, s.fullscreen, onFullscreen, Modifier.weight(1f), ExpressiveShapes.pentagon)
                    }
                }
            }
        }

        // In fullscreen only a small escape hatch stays on screen.
        if (fullscreen) {
            Surface(
                onClick = onFullscreen,
                shape = CornerRadius.full,
                color = Chrome.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Symbol(Symbols.fullscreenExit, size = 20.dp, tint = ChromeText)
                }
            }
        }
    }
}

@Composable
private fun MetricPill(value: String, unit: String, healthy: Boolean) {
    Surface(
        shape = CornerRadius.small,
        color = if (healthy) Color(0xFF16321F) else ChromeHigh,
        contentColor = if (healthy) Color(0xFF8FE0A9) else ChromeMuted,
    ) {
        Text(
            "$value $unit",
            style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun SoftTag(text: String) {
    Surface(shape = CornerRadius.small, color = ChromeHigh, contentColor = ChromeMuted) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun RemoteTool(
    glyph: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: androidx.compose.ui.graphics.Shape = ExpressiveShapes.cookie6,
    active: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = CornerRadius.largeIncreased,
        color = if (active) scheme.primaryContainer else ChromeHigh,
        contentColor = if (active) scheme.onPrimaryContainer else ChromeText,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            Surface(shape = badge, color = Color.Transparent) {
                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                    Symbol(glyph, size = 20.dp, filled = active)
                }
            }
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}
