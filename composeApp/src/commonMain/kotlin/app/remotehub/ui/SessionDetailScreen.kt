@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.remotehub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.remotehub.design.ConfirmDialog
import app.remotehub.design.DetailRow
import app.remotehub.design.DestructiveAction
import app.remotehub.design.IconSize
import app.remotehub.design.InlineMessage
import app.remotehub.design.LoadingState
import app.remotehub.design.MessageTone
import app.remotehub.design.OutlinedAction
import app.remotehub.design.PrimaryAction
import app.remotehub.design.RowDivider
import app.remotehub.design.ScreenContent
import app.remotehub.design.Section
import app.remotehub.design.Spacing
import app.remotehub.design.StatusBadge
import app.remotehub.design.Symbol
import app.remotehub.design.Symbols
import app.remotehub.design.TextAction
import app.remotehub.design.TimeMeter
import app.remotehub.design.TonalAction
import app.remotehub.design.ltrStyle
import app.remotehub.domain.ServerSession
import app.remotehub.domain.ServerState
import app.remotehub.i18n.LocalStrings

/**
 * Everything needed to reach one machine, and the actions that manage its life.
 *
 * Changes: the name is no longer repeated under a top bar that already shows it; the
 * connection facts are list rows instead of a fixed-width label column inside a card;
 * the password is masked with an explicit reveal control; and the four actions are
 * ranked instead of being four equal pills. Stop is destructive, so it is outlined in
 * the error role and sits behind a confirmation.
 */
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
    val provisioning = server.state == ServerState.Provisioning
    var confirmStop by rememberSaveable { mutableStateOf(false) }
    var confirmRerun by rememberSaveable { mutableStateOf(false) }

    ScreenContent(modifier) {
        // ---- state of the machine ----
        Section(title = s.sessionSummary) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                StatusBadge(server.state)
                Text(
                    server.spec.operatingSystem,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            if (!stopped && server.remainingLabel.isNotBlank()) {
                TimeMeter(
                    fraction = server.remainingFraction,
                    remaining = server.remainingLabel,
                    caption = s.timeRemaining,
                    warning = expiring,
                )
            }
            if (expiring) InlineMessage(s.expiringBanner, tone = MessageTone.Warning)
            if (stopped) InlineMessage(s.stoppedBanner, tone = MessageTone.Info)
            if (server.lastError != null) InlineMessage(server.lastError!!, tone = MessageTone.Error)
        }

        RowDivider()

        // ---- how to reach it ----
        Section(title = s.connectionInfo) {
            if (connection == null || connection.address.isBlank()) {
                LoadingState(s.findingAddress)
            } else {
                DetailRow(s.address, connection.address, mono = true, onCopy = { onCopy(s.address, connection.address) })
                DetailRow(s.port, connection.port.toString(), mono = true, onCopy = { onCopy(s.port, connection.port.toString()) })
            }
            if (connection != null) {
                DetailRow(s.username, connection.username, mono = true, onCopy = { onCopy(s.username, connection.username) })
                SecretRow(
                    label = s.password,
                    value = connection.password,
                    revealed = passwordVisible,
                    onToggle = onTogglePassword,
                    onCopy = { onCopy(s.password, connection.password) },
                )
            }
        }

        RowDivider()

        // ---- what the user can do, ranked ----
        Section(title = s.serverActions, subtitle = s.restartHint) {
            if (error != null) {
                InlineMessage(error, tone = MessageTone.Error)
                androidx.compose.foundation.layout.Spacer(Modifier.height(Spacing.sm))
            }
            PrimaryAction(
                text = s.connectRemote,
                onClick = onConnect,
                glyph = Symbols.desktopWindows,
                enabled = !stopped && connection?.address?.isNotBlank() == true && !busy,
                loading = busy && !stopped,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                TonalAction(
                    text = if (stopped) s.rerun else s.restartServer,
                    onClick = { confirmRerun = true },
                    glyph = Symbols.restartAlt,
                    enabled = !busy && !provisioning,
                    modifier = Modifier.weight(1f),
                )
                OutlinedAction(
                    text = s.backup,
                    onClick = onBackup,
                    glyph = Symbols.backup,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
            }
            DestructiveAction(
                text = s.stopServer,
                onClick = { confirmStop = true },
                glyph = Symbols.stop,
                enabled = !stopped && !busy,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            )
        }

        if (server.backups.isNotEmpty()) {
            RowDivider()
            Section(title = s.backups) {
                server.backups.takeLast(5).forEach { record ->
                    DetailRow(
                        label = record.createdAtLabel.ifBlank { record.id.substringAfterLast('/') },
                        value = record.commitRef.ifBlank { record.sizeLabel },
                        mono = true,
                    )
                }
            }
        }

        TextAction(s.openWorkflowRun, onOpenRun, glyph = Symbols.openInNew)
    }

    if (confirmStop) {
        ConfirmDialog(
            title = s.stopConfirmTitle,
            body = s.stopConfirmBody,
            confirmLabel = s.stopServer,
            dismissLabel = s.cancel,
            destructive = true,
            glyph = Symbols.warning,
            onConfirm = { confirmStop = false; onStop() },
            onDismiss = { confirmStop = false },
        )
    }
    if (confirmRerun) {
        ConfirmDialog(
            title = s.restartServer,
            body = s.restartHint,
            confirmLabel = s.confirm,
            dismissLabel = s.cancel,
            glyph = Symbols.restartAlt,
            onConfirm = { confirmRerun = false; onRerun() },
            onDismiss = { confirmRerun = false },
        )
    }
}

/**
 * A credential. Masked by default, revealed only on an explicit action, and the reveal
 * control states what it will do — the old screen left a "Show password" text button
 * detached from the value it affected.
 */
@Composable
private fun SecretRow(
    label: String,
    value: String,
    revealed: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        overlineContent = { Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant) },
        headlineContent = {
            Text(
                if (revealed) value else "••••••••••••",
                style = ltrStyle(),
                color = scheme.onSurface,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggle) {
                    Symbol(
                        if (revealed) Symbols.visibilityOff else Symbols.visibility,
                        size = IconSize.medium,
                        tint = scheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onCopy) {
                    Symbol(Symbols.contentCopy, size = IconSize.medium, tint = scheme.onSurfaceVariant)
                }
            }
        },
    )
}
