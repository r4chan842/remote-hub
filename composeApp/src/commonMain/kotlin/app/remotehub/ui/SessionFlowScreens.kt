@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.remotehub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextDirection
import app.remotehub.app.AppState
import app.remotehub.app.ProvisionPhase
import app.remotehub.design.ConfirmDialog
import app.remotehub.design.EmptyState
import app.remotehub.design.ErrorState
import app.remotehub.design.ExpandableHelp
import app.remotehub.design.IconSize
import app.remotehub.design.InlineMessage
import app.remotehub.design.LoadingState
import app.remotehub.design.LocalStatusColors
import app.remotehub.design.MessageTone
import app.remotehub.design.OutlinedAction
import app.remotehub.design.PrimaryAction
import app.remotehub.design.RowDivider
import app.remotehub.design.ScreenContent
import app.remotehub.design.Section
import app.remotehub.design.Spacing
import app.remotehub.design.StepRow
import app.remotehub.design.Symbol
import app.remotehub.design.Symbols
import app.remotehub.design.TextAction
import app.remotehub.i18n.AppStrings
import app.remotehub.i18n.LocalStrings

/** Two inputs and one decision. The explanation of what happens is a disclosure. */
@Composable
fun CreateContent(
    state: AppState,
    onNameChange: (String) -> Unit,
    onDurationChange: (Int) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    var detailsOpen by rememberSaveable { mutableStateOf(false) }

    ScreenContent(modifier) {
        Section(title = s.createServer, subtitle = s.createSubtitle) {
            OutlinedTextField(
                value = state.form.name,
                onValueChange = onNameChange,
                label = { Text(s.createNameLabel) },
                placeholder = { Text("windows-1") },
                singleLine = true,
                supportingText = { Text(s.createNameHint, style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraSmall,
            )
        }

        Section(title = s.createDuration, subtitle = s.createDurationHint) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                listOf(60, 120, 240, 360).forEach { minutes ->
                    FilterChip(
                        selected = state.form.durationMinutes == minutes,
                        onClick = { onDurationChange(minutes) },
                        label = { Text(if (minutes < 60) "$minutes m" else "${minutes / 60} h") },
                    )
                }
            }
            Text(
                s.createDurationMax,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }

        ExpandableHelp(s.createWhatHappens, detailsOpen, { detailsOpen = !detailsOpen }) {
            listOf(s.createBulletRepo, s.createBulletWindows, s.createBulletTunnel, s.createBulletPassword)
                .forEach { line ->
                    Text("• $line", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
        }

        if (state.publicRepository) {
            InlineMessage(s.createPublicNote, tone = MessageTone.Warning)
        }
        InlineMessage(s.createEphemeralNote, tone = MessageTone.Info)
        if (state.error != null) InlineMessage(state.error!!, tone = MessageTone.Error)

        PrimaryAction(
            text = s.createStart,
            onClick = onStart,
            glyph = Symbols.playArrow,
            enabled = state.form.name.isNotBlank() && !state.operation.isActive,
            loading = state.operation.isActive,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun ProvisionPhase.label(s: AppStrings): String = when (this) {
    ProvisionPhase.Idle -> s.progressStarting
    ProvisionPhase.Validating -> s.phaseValidating
    ProvisionPhase.Initializing -> s.phaseInitializing
    ProvisionPhase.CreatingRepository -> s.phaseCreatingRepo
    ProvisionPhase.RunningWorkflow -> s.phaseRunningWorkflow
    ProvisionPhase.Connecting -> s.phaseConnecting
    ProvisionPhase.Completed -> s.phaseCompleted
    ProvisionPhase.Failed -> s.phaseFailed
    ProvisionPhase.Cancelled -> s.phaseCancelled
}

/**
 * The creation lifecycle, rendered from [AppState.operation] rather than from a boolean.
 *
 * Because the operation lives above the navigation state, this screen shows the same
 * thing whether the user stayed here or went to Settings and came back. Every terminal
 * state has its own affordance: retry on failure, open on success, restart on cancel.
 */
@Composable
fun ProgressContent(
    state: AppState,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onOpenServer: () -> Unit,
    modifier: Modifier = Modifier,
    onAcknowledge: () -> Unit = {},
    onOpenLogs: () -> Unit = {},
) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    val status = LocalStatusColors.current
    val op = state.operation
    var logOpen by rememberSaveable { mutableStateOf(false) }
    var confirmCancel by rememberSaveable { mutableStateOf(false) }

    ScreenContent(modifier) {
        if (!op.hasRun) {
            EmptyState(
                glyph = Symbols.rocketLaunch,
                title = s.progressTitle,
                body = s.createSubtitle,
                actionLabel = s.createServer,
                onAction = onAcknowledge,
            )
            return@ScreenContent
        }

        // ---- headline: which phase, how far ----
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Symbol(
                    when {
                        op.phase == ProvisionPhase.Completed -> Symbols.checkCircle
                        op.phase == ProvisionPhase.Failed -> Symbols.error
                        op.phase == ProvisionPhase.Cancelled -> Symbols.cancel
                        else -> Symbols.rocketLaunch
                    },
                    size = IconSize.standard,
                    tint = when {
                        op.phase == ProvisionPhase.Completed -> status.success
                        op.phase == ProvisionPhase.Failed -> scheme.error
                        else -> scheme.primary
                    },
                    filled = op.phase == ProvisionPhase.Completed,
                )
                Text(
                    op.phase.label(s),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f).padding(start = Spacing.sm),
                )
                Text(
                    "${(op.progressFraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr),
                    color = scheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { op.progressFraction },
                modifier = Modifier.fillMaxWidth().height(Spacing.xs),
                color = when {
                    op.phase == ProvisionPhase.Failed -> scheme.error
                    op.phase == ProvisionPhase.Completed -> status.success
                    else -> scheme.primary
                },
                trackColor = scheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
            )
            Text(
                op.serverName,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }

        // ---- terminal outcomes ----
        when (op.phase) {
            ProvisionPhase.Failed -> ErrorState(
                message = op.errorMessage ?: s.progressFailed,
                retryLabel = s.retryAction,
                onRetry = onRetry,
                detailsLabel = s.viewLogs,
                onDetails = onOpenLogs,
            )
            ProvisionPhase.Completed -> InlineMessage(s.progressDone, tone = MessageTone.Info)
            ProvisionPhase.Cancelled -> InlineMessage(s.phaseCancelled, tone = MessageTone.Warning)
            else -> Unit
        }

        if (op.steps.isNotEmpty()) {
            RowDivider()
            Section(title = s.liveProgress) {
                op.steps.forEach { step ->
                    StepRow(
                        title = stepTitle(step.id, s),
                        status = step.status,
                        detail = step.detail,
                        elapsed = step.elapsedMillis?.let { "${it / 1000}s" },
                    )
                }
            }
        }

        if (op.log.isNotEmpty()) {
            ExpandableHelp(s.progressLog, logOpen, { logOpen = !logOpen }) {
                op.log.takeLast(12).forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ---- the action that matches the state ----
        when {
            op.isActive -> OutlinedAction(
                text = s.cancel,
                onClick = { confirmCancel = true },
                glyph = Symbols.close,
                modifier = Modifier.fillMaxWidth(),
            )
            op.phase == ProvisionPhase.Completed -> PrimaryAction(
                text = s.progressOpenServer,
                onClick = onOpenServer,
                glyph = Symbols.arrowForward,
                modifier = Modifier.fillMaxWidth(),
            )
            else -> PrimaryAction(
                text = s.retryAction,
                onClick = onRetry,
                glyph = Symbols.refresh,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirmCancel) {
        ConfirmDialog(
            title = s.cancelConfirmTitle,
            body = s.cancelConfirmBody,
            confirmLabel = s.cancel,
            dismissLabel = s.back,
            destructive = true,
            glyph = Symbols.warning,
            onConfirm = { confirmCancel = false; onCancel() },
            onDismiss = { confirmCancel = false },
        )
    }
}

/** Backend step ids are stable; anything new falls back to the raw id. */
internal fun stepTitle(id: String, s: AppStrings): String = when (id) {
    "account" -> s.stepAccountCheck
    "repo" -> s.stepRepoEnsure
    "secrets" -> s.stepSecrets
    "dispatch" -> s.stepDispatch
    "machine" -> s.stepRunner
    "network" -> s.stepJoin
    "ready" -> s.stepReady
    else -> id
}

/** Shown inside the remote surface while the tailnet address is still being resolved. */
@Composable
fun FindingAddressContent(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingState(LocalStrings.current.findingAddress)
    }
}
