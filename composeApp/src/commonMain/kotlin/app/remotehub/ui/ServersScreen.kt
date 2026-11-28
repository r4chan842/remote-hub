package app.remotehub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.remotehub.design.EmptyState
import app.remotehub.design.IconSize
import app.remotehub.design.flushListRow
import app.remotehub.design.Layout
import app.remotehub.design.RowDivider
import app.remotehub.design.Spacing
import app.remotehub.design.StatusBadge
import app.remotehub.design.Symbol
import app.remotehub.design.Symbols
import app.remotehub.design.label
import app.remotehub.design.ltrStyle
import app.remotehub.design.screenMargin
import app.remotehub.domain.ServerSession
import app.remotehub.domain.ServerState
import app.remotehub.i18n.LocalStrings

private enum class ServerFilter { All, Live, Stopped }

/**
 * Every session the user has created, newest first.
 *
 * The previous version was a column of 28 dp-radius cards, each containing a decorative
 * badge whose outline differed per row, a full-width capsule button and a text button.
 * With one server that left most of the screen empty; with ten it was ten buttons deep.
 *
 * Now it is a list: one row per server, the whole row opens the detail screen, and the
 * single most useful action sits in the trailing slot. A filter row keeps a long list
 * navigable.
 */
@Composable
fun ServersContent(
    servers: List<ServerSession>,
    padding: PaddingValues,
    onOpen: (String) -> Unit,
    onConnect: (String) -> Unit,
    onRestart: (String) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val margin = screenMargin
    // rememberSaveable so the chosen filter survives leaving and returning to the tab.
    var filterIndex by rememberSaveable { mutableStateOf(0) }
    val filter = ServerFilter.entries[filterIndex.coerceIn(0, ServerFilter.entries.size - 1)]

    if (servers.isEmpty()) {
        Box(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = margin),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                glyph = Symbols.dns,
                title = s.serversEmpty,
                body = s.serversEmptyHint,
                actionLabel = s.createServer,
                onAction = onCreate,
            )
        }
        return
    }

    val shown = servers.filter { server ->
        when (filter) {
            ServerFilter.All -> true
            ServerFilter.Live -> server.state == ServerState.Ready || server.state == ServerState.Connected ||
                server.state == ServerState.Expiring || server.state == ServerState.Provisioning
            ServerFilter.Stopped -> server.state == ServerState.Stopped || server.state == ServerState.Failed
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = modifier.fillMaxSize().widthIn(max = Layout.maxContentWidth),
            contentPadding = PaddingValues(
                start = margin,
                end = margin,
                top = padding.calculateTopPadding() + Spacing.sm,
                bottom = padding.calculateBottomPadding() + Spacing.scrollEnd,
            ),
        ) {
            item(key = "filter") {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ServerFilter.entries.forEach { entry ->
                        FilterChip(
                            selected = filter == entry,
                            onClick = { filterIndex = entry.ordinal },
                            label = {
                                Text(
                                    when (entry) {
                                        ServerFilter.All -> s.logsAll
                                        ServerFilter.Live -> s.online
                                        ServerFilter.Stopped -> s.stateStopped
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            },
                        )
                    }
                }
            }

            if (shown.isEmpty()) {
                item(key = "filtered-empty") {
                    EmptyState(
                        glyph = Symbols.filterList,
                        title = s.serversEmpty,
                        body = s.serversEmptyHint,
                    )
                }
            }

            items(shown, key = { it.id }) { server ->
                ServerRow(
                    server = server,
                    onOpen = { onOpen(server.id) },
                    onConnect = { onConnect(server.id) },
                    onRestart = { onRestart(server.id) },
                )
                RowDivider()
            }
        }
    }
}

@Composable
private fun ServerRow(
    server: ServerSession,
    onOpen: () -> Unit,
    onConnect: () -> Unit,
    onRestart: () -> Unit,
) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    val live = server.state == ServerState.Ready || server.state == ServerState.Connected
    val busy = server.state == ServerState.Provisioning || server.state == ServerState.Stopping
    val address = server.connection?.address?.ifBlank { null } ?: server.repositoryFullName

    ListItem(
        modifier = Modifier.fillMaxWidth().flushListRow().clickable(onClick = onOpen),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Surface(
                color = if (live) scheme.primaryContainer else scheme.surfaceContainerHighest,
                contentColor = if (live) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                shape = CircleShape,
                modifier = Modifier.size(IconSize.large),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Symbol(
                        if (server.state == ServerState.Connected) Symbols.desktopWindows else Symbols.dns,
                        size = IconSize.standard,
                    )
                }
            }
        },
        headlineContent = {
            Text(
                server.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(address, style = ltrStyle(), color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    StatusBadge(server.state)
                    if (server.remainingLabel.isNotBlank() && server.durationMinutes > 0) {
                        Text(
                            server.remainingLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                textDirection = androidx.compose.ui.text.style.TextDirection.Ltr,
                            ),
                            color = if (server.remainingFraction < 0.15f) {
                                app.remotehub.design.LocalStatusColors.current.warning
                            } else {
                                scheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        },
        trailingContent = {
            // One action: reach the machine when it is up, bring it back when it is not.
            IconButton(onClick = if (live) onConnect else onRestart, enabled = !busy) {
                Symbol(
                    if (live) Symbols.desktopWindows else Symbols.restartAlt,
                    size = IconSize.standard,
                    tint = if (busy) scheme.outlineVariant else scheme.primary,
                )
            }
        },
    )
}
