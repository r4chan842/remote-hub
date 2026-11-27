@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.remotehub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.remotehub.design.CornerRadius
import app.remotehub.design.EmptyState
import app.remotehub.design.ExpressiveShapes
import app.remotehub.design.IconBadge
import app.remotehub.design.StatePill
import app.remotehub.design.Symbol
import app.remotehub.design.Symbols
import app.remotehub.design.TimeMeter
import app.remotehub.domain.ServerSession
import app.remotehub.domain.ServerState
import app.remotehub.i18n.LocalStrings

/**
 * Every session the user has created, newest first. One card per server, one primary
 * action per card, and the restart action re-runs the same workflow instead of
 * building a second machine.
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
    if (servers.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                EmptyState(Symbols.dns, s.serversEmpty, s.serversEmptyHint)
                Button(onClick = onCreate, shape = CornerRadius.full) {
                    Symbol(Symbols.add, size = 18.dp)
                    Box(Modifier.size(8.dp))
                    Text(s.createServer)
                }
            }
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(servers, key = { _, item -> item.id }) { index, server ->
            ServerCard(
                server = server,
                shapeIndex = index,
                onOpen = { onOpen(server.id) },
                onConnect = { onConnect(server.id) },
                onRestart = { onRestart(server.id) },
            )
        }
    }
}

@Composable
private fun ServerCard(
    server: ServerSession,
    shapeIndex: Int,
    onOpen: () -> Unit,
    onConnect: () -> Unit,
    onRestart: () -> Unit,
) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    val live = server.state == ServerState.Ready || server.state == ServerState.Connected
    Surface(
        onClick = onOpen,
        shape = CornerRadius.extraLarge,
        color = scheme.surfaceContainerLow,
        contentColor = scheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(
                    glyph = if (server.state == ServerState.Connected) Symbols.desktopWindows else Symbols.dns,
                    shape = ExpressiveShapes.forIndex(shapeIndex),
                    container = if (live) scheme.primaryContainer else scheme.surfaceContainerHighest,
                    content = if (live) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    filled = live,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        server.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        server.connection?.address?.ifBlank { server.repositoryFullName } ?: server.repositoryFullName,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatePill(server.state)
            }

            if (server.durationMinutes > 0 && server.remainingLabel.isNotBlank()) {
                TimeMeter(
                    fraction = server.remainingFraction,
                    remaining = server.remainingLabel,
                    caption = s.lifecycle,
                    warning = server.remainingFraction < 0.15f,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (live) {
                    Button(onClick = onConnect, shape = CornerRadius.full, modifier = Modifier.weight(1f)) {
                        Symbol(Symbols.desktopWindows, size = 18.dp)
                        Box(Modifier.size(8.dp))
                        Text(s.openRemote, maxLines = 1)
                    }
                } else {
                    Button(
                        onClick = onRestart,
                        shape = CornerRadius.full,
                        modifier = Modifier.weight(1f),
                        enabled = server.state != ServerState.Provisioning,
                    ) {
                        Symbol(Symbols.restartAlt, size = 18.dp)
                        Box(Modifier.size(8.dp))
                        Text(if (server.state == ServerState.Provisioning) s.restarting else s.restartServer, maxLines = 1)
                    }
                }
                TextButton(onClick = onOpen, shape = CornerRadius.large) { Text(s.sessionDetail, maxLines = 1) }
            }
        }
    }
}
