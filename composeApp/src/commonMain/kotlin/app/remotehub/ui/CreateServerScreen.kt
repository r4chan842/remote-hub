@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.remotehub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.remotehub.design.InfoRow
import app.remotehub.design.KeyValueGrid
import app.remotehub.design.LocalStatusColors
import app.remotehub.design.NoticeBanner
import app.remotehub.design.Panel
import app.remotehub.design.SectionTitle
import app.remotehub.design.StepRow
import app.remotehub.design.Symbol
import app.remotehub.design.Symbols
import app.remotehub.design.TagChip
import app.remotehub.design.WarningBanner
import app.remotehub.domain.ProvisionStep
import app.remotehub.domain.StepStatus
import app.remotehub.i18n.LocalStrings

data class CreateForm(
    val name: String,
    val provider: String,
    val region: String,
    val osImage: String,
    val machineSize: String,
    val warningMinutes: Int,
    val autoBackup: Boolean,
    val encryptedBackup: Boolean,
)

@Composable
fun CreateServerContent(
    form: CreateForm,
    wizardStep: Int,
    provisioning: Boolean,
    steps: List<ProvisionStep>,
    repositoryFullName: String,
    onNameChange: (String) -> Unit,
    onWarningChange: (Int) -> Unit,
    onAutoBackupChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onCreate: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (provisioning) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.provisioning, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TagChip(s.liveProgress, tone = scheme.tertiaryContainer, content = scheme.onTertiaryContainer)
                }
                Text(repositoryFullName, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Column(Modifier.padding(top = 12.dp)) {
                    steps.forEach { step ->
                        StepRow(
                            title = stepTitle(step.id, s),
                            status = step.status,
                            detail = step.detail,
                            elapsed = step.elapsedMillis?.let { formatMillis(it) },
                        )
                    }
                }
                OutlinedButton(
                    onClick = onCancel,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                ) {
                    Text(s.cancel, style = MaterialTheme.typography.labelLarge)
                }
            }
            NoticeBanner(s.complianceNotice)
            return@Column
        }

        WizardHeader(current = wizardStep, labels = listOf(s.stepTarget, s.stepConfig, s.stepReview))

        when (wizardStep) {
            0 -> {
                Panel {
                    SectionTitle(s.provider)
                    Text(s.providerHint, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    Column(Modifier.padding(top = 12.dp)) {
                        InfoRow(s.provider, form.provider)
                        InfoRow(s.region, form.region)
                        InfoRow(s.osImage, form.osImage)
                        InfoRow(s.machineSize, form.machineSize)
                    }
                }
                Panel {
                    SectionTitle(s.serverName)
                    OutlinedTextField(
                        value = form.name,
                        onValueChange = onNameChange,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = MaterialTheme.shapes.medium,
                    )
                }
            }
            1 -> {
                Panel {
                    SectionTitle(s.expiryWarning)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text(s.alertThreshold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TagChip("${form.warningMinutes} ${s.minutes}")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(10, 15, 30, 60).forEach { value ->
                            val selected = value == form.warningMinutes
                            Surface(
                                onClick = { onWarningChange(value) },
                                shape = MaterialTheme.shapes.medium,
                                color = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
                                contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                            ) {
                                Text(
                                    "$value",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }
                }
                Panel {
                    SectionTitle(s.autoBackup)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(s.backupDestination, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                            Text(s.sameRepo, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                        Switch(checked = form.autoBackup, onCheckedChange = onAutoBackupChange)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                        Symbol(Symbols.lock, size = 18.dp, tint = scheme.primary)
                        Text("  " + s.clientSideEncrypted, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                    }
                }
                WarningBanner(s.publicRepoWarning)
            }
            else -> {
                Panel {
                    SectionTitle(s.stepReview)
                    KeyValueGrid(
                        items = listOf(
                            s.serverName to form.name,
                            s.provider to form.provider,
                            s.region to form.region,
                            s.osImage to form.osImage,
                            s.machineSize to form.machineSize,
                            s.repository to repositoryFullName,
                        ),
                        columns = 2,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                NoticeBanner(s.complianceNotice)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (wizardStep > 0) {
                OutlinedButton(onClick = onBack, shape = MaterialTheme.shapes.large, modifier = Modifier.weight(1f)) {
                    Text(s.back, style = MaterialTheme.typography.labelLarge)
                }
            }
            Button(
                onClick = { if (wizardStep == 2) onCreate() else onNext() },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.weight(1f),
            ) {
                if (wizardStep == 2) Symbol(Symbols.rocketLaunch, size = 18.dp)
                Text("  " + if (wizardStep == 2) s.createServer else s.next, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun WizardHeader(current: Int, labels: List<String>) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    val colors = LocalStatusColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val done = index < current
            val active = index == current
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    color = when {
                        done -> colors.successContainer
                        active -> scheme.primaryContainer
                        else -> scheme.surfaceVariant
                    },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier.size(26.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (done) {
                            Symbol(Symbols.check, size = 16.dp, tint = colors.onSuccessContainer)
                        } else {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active || done) scheme.onSurface else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

fun formatMillis(millis: Long): String {
    val totalSeconds = millis / 1000
    return if (totalSeconds < 60) "${totalSeconds}s" else "${totalSeconds / 60}m ${totalSeconds % 60}s"
}
