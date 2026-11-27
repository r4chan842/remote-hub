package app.remotehub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.remotehub.app.AppState
import app.remotehub.design.NoticeBanner
import app.remotehub.design.Panel
import app.remotehub.design.SectionTitle
import app.remotehub.design.StepRow
import app.remotehub.design.Symbol
import app.remotehub.design.Symbols
import app.remotehub.design.WarningBanner
import app.remotehub.domain.StepStatus
import app.remotehub.i18n.LocalStrings
import app.remotehub.server.SessionWorkflow

/** Step 2 of the linear flow: a name and a lifetime, nothing else. */
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(s.createTitle, style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
        Text(s.createSubtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)

        Panel {
            OutlinedTextField(
                value = state.form.name,
                onValueChange = onNameChange,
                label = { Text(s.createNameLabel) },
                placeholder = { Text("windows-1") },
                singleLine = true,
                supportingText = { Text(s.createNameHint, style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )
        }

        Panel {
            SectionTitle(s.createDuration)
            Text(s.createDurationHint, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
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
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Panel {
            SectionTitle(s.createWhatHappens)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("• " + s.createBulletRepo, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Text("• " + s.createBulletWindows, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Text("• " + s.createBulletTunnel, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Text("• " + s.createBulletPassword, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }

        NoticeBanner(s.createEphemeralNote)
        if (state.publicRepository) {
            WarningBanner(s.createPublicNote)
        }
        if (state.error != null) {
            WarningBanner(state.error!!)
        }

        Button(
            onClick = onStart,
            enabled = state.form.name.isNotBlank() && !state.provisioning,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Symbol(Symbols.playArrow, size = 20.dp)
            Text("  " + s.createStart, style = MaterialTheme.typography.labelLarge)
        }
        OutlinedButton(onClick = onBack, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Text(s.back, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Step 3: the seven stages with live status and elapsed time. */
@Composable
fun ProgressContent(
    state: AppState,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onOpenServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    val failed = state.steps.any { it.status == StepStatus.Failed }
    val done = state.steps.isNotEmpty() && state.steps.all { it.status == StepStatus.Done }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.progressTitle, style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
                Text(
                    if (failed) s.progressFailed else if (done) s.progressDone else s.progressRunning,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (!failed && !done) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            }
        }

        Panel {
            state.steps.forEach { step ->
                StepRow(
                    title = stepTitle(step.id, s),
                    status = step.status,
                    detail = step.detail,
                    elapsed = step.elapsedMillis?.let { formatMillis(it) },
                )
            }
            if (state.steps.isEmpty()) {
                Text(s.progressStarting, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }

        if (state.progressLog.isNotEmpty()) {
            Panel {
                SectionTitle(s.progressLog)
                state.progressLog.takeLast(6).forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }

        if (state.error != null) WarningBanner(state.error!!)

        when {
            done -> Button(
                onClick = onOpenServer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(s.progressOpenServer, style = MaterialTheme.typography.labelLarge) }

            failed -> {
                Button(onClick = onRetry, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Symbol(Symbols.refresh, size = 18.dp)
                    Text("  " + s.retry, style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(onClick = onCancel, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Text(s.back, style = MaterialTheme.typography.labelLarge)
                }
            }

            else -> OutlinedButton(
                onClick = onCancel,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(s.cancel, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** The seven stages of a session, in the order the user sees them. */
val provisionStepIds = listOf("account", "repo", "secrets", "dispatch", "machine", "network", "ready")

fun stepTitle(id: String, s: app.remotehub.i18n.AppStrings): String = when (id) {
    "account" -> s.stepAccount
    "repo" -> s.stepRepo
    "secrets" -> s.stepSecrets
    "dispatch" -> s.stepDispatch
    "machine" -> s.stepMachine
    "network" -> s.stepNetwork
    else -> s.stepReady
}

fun formatMillis(millis: Long): String {
    val totalSeconds = millis / 1000
    return if (totalSeconds < 60) "${totalSeconds}s" else "${totalSeconds / 60}m ${totalSeconds % 60}s"
}

/** Shown inside the remote screen while the tailnet address is still being discovered. */
@Composable
fun FindingAddressContent(modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    Column(
        modifier = modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp)
        Text(
            s.findingAddress,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            s.findingAddressHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
