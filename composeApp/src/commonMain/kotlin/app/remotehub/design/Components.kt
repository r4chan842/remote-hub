@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.remotehub.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import app.remotehub.domain.ServerState
import app.remotehub.domain.StepStatus
import app.remotehub.i18n.AppStrings
import app.remotehub.i18n.LocalStrings

fun ServerState.label(s: AppStrings): String = when (this) {
    ServerState.Provisioning -> s.stateProvisioning
    ServerState.Ready -> s.stateReady
    ServerState.Connected -> s.stateConnected
    ServerState.Expiring -> s.stateExpiring
    ServerState.Stopping -> s.stateStopping
    ServerState.Stopped -> s.stateStopped
    ServerState.Failed -> s.stateFailed
}

@Composable
fun stateColors(state: ServerState): Pair<Color, Color> {
    val status = LocalStatusColors.current
    val scheme = MaterialTheme.colorScheme
    return when (state) {
        ServerState.Ready -> status.successContainer to status.onSuccessContainer
        ServerState.Connected -> scheme.primaryContainer to scheme.onPrimaryContainer
        ServerState.Provisioning -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        ServerState.Expiring -> status.warningContainer to status.onWarningContainer
        ServerState.Failed -> scheme.errorContainer to scheme.onErrorContainer
        ServerState.Stopping -> status.warningContainer to status.onWarningContainer
        ServerState.Stopped -> status.neutralContainer to status.onNeutralContainer
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) trailing()
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun StatePill(state: ServerState, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val (bg, fg) = stateColors(state)
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(6.dp).background(fg, CircleShape))
            Text(state.label(s), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun TagChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    ltr: Boolean = false,
) {
    Surface(color = tone, contentColor = content, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(
                textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content,
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier, monospace: Boolean = false, onCopy: (() -> Unit)? = null) {
    val s = LocalStrings.current
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(112.dp),
        )
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(
                textDirection = if (monospace) TextDirection.Ltr else TextDirection.Content,
                textAlign = if (monospace && rtl) TextAlign.Right else TextAlign.Start,
            ),
            fontWeight = if (monospace) FontWeight.Medium else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (onCopy != null) {
            IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                Symbol(Symbols.contentCopy, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun MetricTile(value: String, caption: String, glyph: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Symbol(glyph, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StepRow(title: String, status: StepStatus, detail: String?, elapsed: String?, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    val statusColors = LocalStatusColors.current
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (status) {
            StepStatus.Running -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = scheme.primary,
                trackColor = scheme.surfaceVariant,
            )
            StepStatus.Done -> Surface(color = statusColors.successContainer, shape = CircleShape, modifier = Modifier.size(22.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Symbol(Symbols.check, size = 15.dp, tint = statusColors.onSuccessContainer)
                }
            }
            StepStatus.Failed -> Surface(color = scheme.errorContainer, shape = CircleShape, modifier = Modifier.size(22.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Symbol(Symbols.close, size = 15.dp, tint = scheme.onErrorContainer)
                }
            }
            else -> Surface(color = scheme.surfaceVariant, shape = CircleShape, modifier = Modifier.size(22.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.size(6.dp).background(scheme.outline, CircleShape))
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (status == StepStatus.Pending) scheme.onSurfaceVariant else scheme.onSurface,
            )
            if (detail != null) {
                val latinOnly = detail.none { it.code in 0x0590..0x08FF }
                val detailRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall.copy(
                        textDirection = if (latinOnly) TextDirection.Ltr else TextDirection.Content,
                        textAlign = if (latinOnly && detailRtl) TextAlign.Right else TextAlign.Start,
                    ),
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        val trailing = elapsed ?: when (status) {
            StepStatus.Running -> s.running
            StepStatus.Pending -> s.pending
            StepStatus.Skipped -> "—"
            else -> null
        }
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SetupRow(
    glyph: String,
    title: String,
    statusText: String,
    ok: Boolean,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = ExpressiveShapes.cookie6,
) {
    val scheme = MaterialTheme.colorScheme
    val status = LocalStatusColors.current
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(
            glyph = glyph,
            shape = shape,
            size = 40.dp,
            container = if (ok) status.successContainer else scheme.surfaceVariant,
            content = if (ok) status.onSuccessContainer else scheme.onSurfaceVariant,
            filled = ok,
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
            Text(
                statusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) status.success else scheme.onSurfaceVariant,
            )
        }
        Symbol(
            if (ok) Symbols.checkCircle else Symbols.chevronRight,
            size = 20.dp,
            tint = if (ok) status.success else scheme.outline,
            filled = ok,
        )
    }
}

@Composable
fun WarningBanner(text: String, modifier: Modifier = Modifier) {
    val status = LocalStatusColors.current
    Surface(
        color = status.warningContainer,
        contentColor = status.onWarningContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Symbol(Symbols.warning, size = 20.dp)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun NoticeBanner(text: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Symbol(Symbols.info, size = 20.dp, tint = scheme.primary)
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun TimeMeter(fraction: Float, remaining: String, caption: String, modifier: Modifier = Modifier, warning: Boolean = false) {
    val status = LocalStatusColors.current
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(caption, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(
                remaining,
                style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr),
                color = if (warning) status.warning else scheme.onSurface,
            )
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = if (warning) status.warning else scheme.primary,
            trackColor = scheme.surfaceVariant,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

@Composable
fun EmptyState(glyph: String, title: String, body: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(color = scheme.surfaceContainer, shape = CircleShape, modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Symbol(glyph, size = 30.dp, tint = scheme.onSurfaceVariant)
            }
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
fun KeyValueGrid(items: List<Pair<String, String>>, columns: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(columns).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { (k, v) ->
                    Column(Modifier.weight(1f)) {
                        Text(k, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        Text(v, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                    }
                }
                repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
