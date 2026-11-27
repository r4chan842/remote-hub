package app.remotehub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.remotehub.design.LocalStatusColors
import app.remotehub.design.Symbol
import app.remotehub.design.Symbols
import app.remotehub.design.TagChip
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

@Composable
fun RemoteContent(
    server: ServerSession,
    metrics: RemoteMetrics,
    onDisconnect: () -> Unit,
    onKeyboard: () -> Unit,
    onClipboard: () -> Unit,
    onCtrlAltDel: () -> Unit,
    onToggleInputMode: () -> Unit,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    val colors = LocalStatusColors.current

    Column(modifier = modifier.fillMaxSize().background(scheme.surfaceContainerLowest)) {
        Surface(color = scheme.surfaceContainer, contentColor = scheme.onSurface) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    onClick = onDisconnect,
                    shape = MaterialTheme.shapes.small,
                    color = scheme.surfaceContainerHighest,
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Symbol(Symbols.close, size = 18.dp, tint = scheme.onSurfaceVariant)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(server.name, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                    Text(
                        "${s.connectedTo} ${server.connection?.address ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                TagChip("${metrics.latencyMillis} ms", tone = colors.successContainer, content = colors.onSuccessContainer)
                TagChip("${metrics.framesPerSecond} fps")
                TagChip("${metrics.kilobitsPerSecond} kbps")
            }
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = Color(0xFF101317),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(28.dp),
                    ) {
                        if (metrics.connecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(30.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFFADC6FF),
                                trackColor = Color(0xFF2A3038),
                            )
                        } else {
                            Symbol(Symbols.desktopWindows, size = 40.dp, tint = Color(0xFF6F7A88))
                        }
                        Text(
                            s.nativeEngine,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFFD7DEE8),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            s.secureChannel,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8B95A3),
                            textAlign = TextAlign.Center,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(color = Color(0xFF1D232B), shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    metrics.resolutionLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF9FB0C4),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                            Surface(color = Color(0xFF1D232B), shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    "TLS · NLA",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF9FB0C4),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(color = scheme.surfaceContainer, contentColor = scheme.onSurfaceVariant) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RemoteTool(Symbols.keyboard, s.keyboard, onKeyboard, Modifier.weight(1f))
                RemoteTool(Symbols.contentCopy, s.clipboard, onClipboard, Modifier.weight(1f))
                RemoteTool(Symbols.cable, s.ctrlAltDel, onCtrlAltDel, Modifier.weight(1f))
                RemoteTool(
                    if (metrics.trackpadMode) Symbols.mouse else Symbols.touchApp,
                    if (metrics.trackpadMode) s.trackpadMode else s.touchMode,
                    onToggleInputMode,
                    Modifier.weight(1f),
                    active = metrics.trackpadMode,
                )
                RemoteTool(Symbols.fullscreen, s.fullscreen, onFullscreen, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RemoteTool(glyph: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (active) scheme.primaryContainer else scheme.surfaceContainerHighest,
        contentColor = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            Symbol(glyph, size = 20.dp)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}
