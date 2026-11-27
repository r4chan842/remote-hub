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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.remotehub.design.InfoRow
import app.remotehub.design.KeyValueGrid
import app.remotehub.design.LocalStatusColors
import app.remotehub.design.Panel
import app.remotehub.design.SectionTitle
import app.remotehub.design.StatePill
import app.remotehub.design.Symbol
import app.remotehub.design.Symbols
import app.remotehub.design.TagChip
import app.remotehub.design.TimeMeter
import app.remotehub.design.WarningBanner
import app.remotehub.domain.OperationKind
import app.remotehub.domain.OperationResult
import app.remotehub.domain.ServerSession
import app.remotehub.domain.ServerState
import app.remotehub.i18n.AppStrings
import app.remotehub.i18n.LocalStrings

@Composable
fun ServerDetailContent(
    server: ServerSession,
    passwordVisible: Boolean,
    expanded: Boolean,
    onTogglePassword: () -> Unit,
    onConnect: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onRebuild: () -> Unit,
    onRerun: () -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreBackup: (String) -> Unit,
    onOpenRun: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    val colors = LocalStatusColors.current

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(server.name, style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
                Text(
                    "${server.spec.providerLabel} · ${server.spec.regionLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            StatePill(server.state)
        }

        if (server.state == ServerState.Expiring) {
            WarningBanner(s.warningBanner)
        }

        if (server.lastError != null) {
            WarningBanner(server.lastError)
        }

        Panel {
            TimeMeter(
                fraction = server.remainingFraction,
                remaining = server.remainingLabel,
                caption = s.timeRemaining,
                warning = server.state == ServerState.Expiring,
            )
            Column(Modifier.padding(top = 10.dp)) {
                InfoRow(s.uptime, server.uptimeLabel)
                InfoRow(s.alertThreshold, "${server.warningThresholdMinutes} ${s.minutes}")
            }
        }

        if (server.connection != null) {
            Panel {
                SectionTitle(s.connectionInfo)
                Column(Modifier.padding(top = 4.dp)) {
                    InfoRow(s.address, server.connection.address, monospace = true, onCopy = {})
                    InfoRow(s.port, server.connection.port.toString(), monospace = true, onCopy = {})
                    InfoRow(s.username, server.connection.username, monospace = true, onCopy = {})
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            s.password,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            if (passwordVisible) server.connection.password else "••••••••••••",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onTogglePassword) {
                            Symbol(if (passwordVisible) Symbols.visibilityOff else Symbols.visibility, size = 18.dp)
                            Text("  " + if (passwordVisible) s.hide else s.show, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    InfoRow(s.fingerprint, server.connection.certificateFingerprint, monospace = true, onCopy = {})
                }
                Button(
                    onClick = onConnect,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Symbol(Symbols.desktopWindows, size = 18.dp)
                    Text("  " + s.openRemote, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Panel {
            SectionTitle(s.lifecycle)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LifecycleButton(Symbols.stop, s.stop, onStop, Modifier.weight(1f), destructive = true)
                LifecycleButton(Symbols.restart, s.restart, onRestart, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LifecycleButton(Symbols.build, s.rebuild, onRebuild, Modifier.weight(1f))
                LifecycleButton(Symbols.replay, s.rerunAction, onRerun, Modifier.weight(1f))
            }
        }

        Panel {
            SectionTitle(s.specifications)
            KeyValueGrid(
                items = listOf(
                    s.cores to server.spec.cpuCores.toString(),
                    s.memory to "${server.spec.memoryGigabytes} GB",
                    s.disk to "${server.spec.diskGigabytes} GB",
                    s.operatingSystem to server.spec.operatingSystem,
                ),
                columns = if (expanded) 4 else 2,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Panel {
            SectionTitle(s.backups) {
                FilledTonalButton(onClick = onCreateBackup, shape = MaterialTheme.shapes.large) {
                    Symbol(Symbols.backup, size = 18.dp)
                    Text("  " + s.createBackup, style = MaterialTheme.typography.labelMedium)
                }
            }
            Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TagChip(s.sameRepo)
                TagChip(s.clientSideEncrypted, tone = colors.successContainer, content = colors.onSuccessContainer)
            }
            Column(Modifier.padding(top = 8.dp)) {
                server.backups.forEachIndexed { index, backup ->
                    if (index > 0) HorizontalDivider(color = scheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Symbol(Symbols.archive, size = 20.dp, tint = scheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(backup.createdAtLabel, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                            Text(
                                "${backup.sizeLabel} · ${backup.commitRef}",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = { onRestoreBackup(backup.id) }, shape = MaterialTheme.shapes.large) {
                            Text(s.restore, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        Panel {
            SectionTitle(s.history)
            Column(Modifier.padding(top = 6.dp)) {
                server.operations.forEach { record ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val tint = when (record.result) {
                            OperationResult.Success -> colors.success
                            OperationResult.Failure -> scheme.error
                            OperationResult.InProgress -> scheme.primary
                            OperationResult.Cancelled -> scheme.outline
                        }
                        Symbol(operationGlyph(record.kind), size = 20.dp, tint = tint)
                        Column(Modifier.weight(1f)) {
                            Text(operationLabel(record.kind, s), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                            Text(record.message, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                        Text(record.timestampLabel, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }

        Panel {
            SectionTitle(s.workflowRun)
            InfoRow(s.repository, server.repositoryFullName, monospace = true, onCopy = {})
            InfoRow(s.workflowRun, "#${server.workflowRunNumber}", monospace = true)
            OutlinedButton(
                onClick = onOpenRun,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Symbol(Symbols.openInNew, size = 18.dp)
                Text("  " + s.openInGithub, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun LifecycleButton(glyph: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, destructive: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = if (destructive) {
            ButtonDefaults.outlinedButtonColors(contentColor = scheme.error)
        } else {
            ButtonDefaults.outlinedButtonColors()
        },
    ) {
        Symbol(glyph, size = 18.dp)
        Text("  $label", style = MaterialTheme.typography.labelLarge)
    }
}

fun operationGlyph(kind: OperationKind): String = when (kind) {
    OperationKind.Provision -> Symbols.rocketLaunch
    OperationKind.Stop -> Symbols.stop
    OperationKind.Restart -> Symbols.restart
    OperationKind.Rebuild -> Symbols.build
    OperationKind.Backup -> Symbols.backup
    OperationKind.Restore -> Symbols.cloudDownload
    OperationKind.RerunWorkflow -> Symbols.replay
    OperationKind.Connect -> Symbols.desktopWindows
}

fun operationLabel(kind: OperationKind, s: AppStrings): String = when (kind) {
    OperationKind.Provision -> s.createServer
    OperationKind.Stop -> s.stop
    OperationKind.Restart -> s.restart
    OperationKind.Rebuild -> s.rebuild
    OperationKind.Backup -> s.createBackup
    OperationKind.Restore -> s.restore
    OperationKind.RerunWorkflow -> s.rerunAction
    OperationKind.Connect -> s.openRemote
}
