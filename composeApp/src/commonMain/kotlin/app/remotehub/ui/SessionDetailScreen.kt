package app.remotehub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.remotehub.app.SessionMath
import app.remotehub.design.InfoRow
import app.remotehub.design.NoticeBanner
import app.remotehub.design.Panel
import app.remotehub.design.SectionTitle
import app.remotehub.design.StatePill
import app.remotehub.design.Symbol
import app.remotehub.design.Symbols
import app.remotehub.design.TimeMeter
import app.remotehub.design.WarningBanner
import app.remotehub.domain.ServerSession
import app.remotehub.domain.ServerState
import app.remotehub.i18n.LocalStrings

/** Step 4: everything needed to reach the machine, and four clear actions. */
@Composable
fun SessionDetailContent(
    server: ServerSession,
    passwordVisible: Boolean,
    busy: Boolean,
    error: String?,
    onTogglePassword: () -> Unit,
    onCopy: (String, String) -> Unit,
    onConnect: () -> Unit,
    onRerun: () -> Unit,
    onBackup: () -> Unit,
    onStop: () -> Unit,
    onOpenRun: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    val connection = server.connection
    val expiring = server.state == ServerState.Expiring
    val stopped = server.state == ServerState.Stopped || server.state == ServerState.Failed

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(server.name, style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
                Text(server.spec.operatingSystem, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            StatePill(server.state)
        }

        if (!stopped) {
            Panel {
                TimeMeter(
                    fraction = server.remainingFraction,
                    remaining = server.remainingLabel,
                    caption = s.timeRemaining,
                    warning = expiring,
                )
            }
        }
        if (expiring) {
            WarningBanner(s.expiringBanner)
        }
        if (stopped) {
            NoticeBanner(s.stoppedBanner)
        }

        Panel {
            SectionTitle(s.connectionInfo)
            if (connection == null || connection.address.isBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(s.findingAddress, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            } else {
                InfoRow(s.address, connection.address, monospace = true, onCopy = { onCopy(s.address, connection.address) })
                InfoRow(s.port, connection.port.toString(), monospace = true, onCopy = { onCopy(s.port, connection.port.toString()) })
            }
            if (connection != null) {
                InfoRow(s.username, connection.username, monospace = true, onCopy = { onCopy(s.username, connection.username) })
                InfoRow(
                    label = s.password,
                    value = if (passwordVisible) connection.password else "••••••••••••",
                    monospace = true,
                    onCopy = { onCopy(s.password, connection.password) },
                )
                TextButton(onClick = onTogglePassword) {
                    Text(if (passwordVisible) s.hidePassword else s.showPassword, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (error != null) WarningBanner(error)

        Button(
            onClick = onConnect,
            enabled = !stopped && connection?.address?.isNotBlank() == true && !busy,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Symbol(Symbols.desktopWindows, size = 20.dp)
            Text("  " + s.connectRemote, style = MaterialTheme.typography.labelLarge)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onBackup, enabled = !busy, shape = MaterialTheme.shapes.large, modifier = Modifier.weight(1f)) {
                Symbol(Symbols.backup, size = 18.dp)
                Text("  " + s.backup, style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(onClick = onRerun, enabled = !busy, shape = MaterialTheme.shapes.large, modifier = Modifier.weight(1f)) {
                Symbol(Symbols.refresh, size = 18.dp)
                Text("  " + s.rerun, style = MaterialTheme.typography.labelMedium)
            }
        }
        OutlinedButton(
            onClick = onStop,
            enabled = !stopped && !busy,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Symbol(Symbols.stop, size = 18.dp)
            Text("  " + s.stopServer, style = MaterialTheme.typography.labelMedium)
        }

        NoticeBanner(s.rerunNote)

        if (server.backups.isNotEmpty()) {
            Panel {
                SectionTitle(s.backups)
                server.backups.takeLast(5).forEach { record ->
                    InfoRow(record.commitRef.ifBlank { record.id.substringAfterLast('/') }, record.sizeLabel)
                }
            }
        }

        TextButton(onClick = onOpenRun, modifier = Modifier.fillMaxWidth()) {
            Symbol(Symbols.openInNew, size = 16.dp)
            Text("  " + s.openWorkflowRun, style = MaterialTheme.typography.labelMedium)
        }
    }
}
