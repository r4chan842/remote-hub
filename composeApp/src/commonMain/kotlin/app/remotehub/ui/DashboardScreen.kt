package app.remotehub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.remotehub.design.InfoRow
import app.remotehub.design.MetricTile
import app.remotehub.design.NoticeBanner
import app.remotehub.design.Panel
import app.remotehub.design.SectionTitle
import app.remotehub.design.SetupRow
import app.remotehub.design.StatePill
import app.remotehub.design.Symbol
import app.remotehub.design.Symbols
import app.remotehub.design.TimeMeter
import app.remotehub.domain.GitHubAccount
import app.remotehub.domain.NetworkState
import app.remotehub.domain.QuotaSnapshot
import app.remotehub.domain.ServerSession
import app.remotehub.domain.ServerState
import app.remotehub.domain.TailnetStatus
import app.remotehub.i18n.LocalStrings

@Composable
fun DashboardContent(
    account: GitHubAccount?,
    tailnet: TailnetStatus,
    providerReady: Boolean,
    servers: List<ServerSession>,
    quota: QuotaSnapshot,
    expanded: Boolean,
    onOpenSetup: () -> Unit,
    onOpenServer: (String) -> Unit,
    onConnect: (String) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    val setupComplete = account != null && tailnet.state == NetworkState.Online && providerReady
    val active = servers.count { it.state == ServerState.Ready || it.state == ServerState.Connected || it.state == ServerState.Expiring }
    val soonest = servers.filter { it.state != ServerState.Stopped }.minByOrNull { it.remainingFraction }

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!setupComplete) {
            Panel {
                SectionTitle(s.setupTitle)
                Text(s.setupSubtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                Column(Modifier.padding(top = 8.dp)) {
                    SetupRow(
                        glyph = Symbols.accountCircle,
                        title = s.githubAccount,
                        statusText = account?.login ?: s.notConnected,
                        ok = account != null,
                    )
                    SetupRow(
                        glyph = Symbols.hub,
                        title = s.privateNetwork,
                        statusText = if (tailnet.state == NetworkState.Online) s.online else s.offline,
                        ok = tailnet.state == NetworkState.Online,
                    )
                    SetupRow(
                        glyph = Symbols.dns,
                        title = s.remoteProvider,
                        statusText = if (providerReady) s.connected else s.notConnected,
                        ok = providerReady,
                    )
                }
                Button(
                    onClick = onOpenSetup,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(s.continueSetup, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(active.toString(), s.activeServers, Symbols.dns, Modifier.weight(1f))
            MetricTile(soonest?.remainingLabel ?: "—", s.timeRemaining, Symbols.timer, Modifier.weight(1f))
            MetricTile(quota.usedMinutes.toString(), s.minutesUsed, Symbols.analytics, Modifier.weight(1f))
        }

        SectionTitle(s.yourServers) {
            TextButton(onClick = onCreate) {
                Symbol(Symbols.add, size = 18.dp, tint = scheme.primary)
                Text("  " + s.newServer, style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            }
        }

        if (servers.isEmpty()) {
            app.remotehub.design.EmptyState(Symbols.dns, s.emptyTitle, s.emptyBody)
        } else {
            servers.forEach { server ->
                ServerCard(
                    server = server,
                    expanded = expanded,
                    onOpen = { onOpenServer(server.id) },
                    onConnect = { onConnect(server.id) },
                )
            }
        }

        Panel {
            SectionTitle(s.quotaTitle)
            InfoRow(s.minutesUsed, "${quota.usedMinutes} / ${quota.includedMinutes}")
            InfoRow(s.concurrentJobs, "${quota.concurrentJobs} / ${quota.concurrentJobLimit}")
            InfoRow(s.jobMaxDuration, quota.jobMaxDurationLabel)
        }

        NoticeBanner(s.complianceNotice)
    }
}

@Composable
private fun ServerCard(
    server: ServerSession,
    expanded: Boolean,
    onOpen: () -> Unit,
    onConnect: () -> Unit,
) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(server.name, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                Text(
                    "${server.spec.providerLabel} · ${server.spec.regionLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            StatePill(server.state)
        }

        if (server.state == ServerState.Provisioning) {
            val running = server.steps.firstOrNull { it.status == app.remotehub.domain.StepStatus.Running }
            val doneCount = server.steps.count { it.status == app.remotehub.domain.StepStatus.Done }
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    running?.let { stepTitle(it.id, s) } ?: s.provisioning,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                )
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { doneCount.toFloat() / server.steps.size },
                    modifier = Modifier.fillMaxWidth(),
                    color = scheme.primary,
                    trackColor = scheme.surfaceVariant,
                )
                Text("$doneCount / ${server.steps.size}", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        } else if (server.connection != null) {
            Column(Modifier.padding(top = 12.dp)) {
                TimeMeter(
                    fraction = server.remainingFraction,
                    remaining = server.remainingLabel,
                    caption = s.timeRemaining,
                    warning = server.state == ServerState.Expiring,
                )
                InfoRow(s.address, "${server.connection.address}:${server.connection.port}", monospace = true)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (server.connection != null && server.state != ServerState.Stopped) {
                Button(
                    onClick = onConnect,
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
                ) {
                    Symbol(Symbols.desktopWindows, size = 18.dp)
                    Text("  " + s.connect, style = MaterialTheme.typography.labelLarge)
                }
            }
            OutlinedButton(onClick = onOpen, shape = MaterialTheme.shapes.large) {
                Text(s.details, style = MaterialTheme.typography.labelLarge)
            }
            if (expanded) {
                Text(
                    server.repositoryFullName,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

fun stepTitle(id: String, s: app.remotehub.i18n.AppStrings): String = when (id) {
    "account" -> s.stepAccountCheck
    "repo" -> s.stepRepoEnsure
    "secrets" -> s.stepSecrets
    "dispatch" -> s.stepDispatch
    "runner" -> s.stepRunner
    "join" -> s.stepJoin
    "handshake" -> s.stepHandshake
    else -> s.stepReady
}
