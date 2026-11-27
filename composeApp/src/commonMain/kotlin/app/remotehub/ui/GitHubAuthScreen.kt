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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.remotehub.design.InfoRow
import app.remotehub.design.LocalStatusColors
import app.remotehub.design.NoticeBanner
import app.remotehub.design.Panel
import app.remotehub.design.SectionTitle
import app.remotehub.design.Symbol
import app.remotehub.design.Symbols
import app.remotehub.design.TagChip
import app.remotehub.domain.AuthMethod
import app.remotehub.domain.DeviceFlowPhase
import app.remotehub.domain.DeviceFlowState
import app.remotehub.domain.GitHubAccount
import app.remotehub.i18n.LocalStrings

@Composable
fun GitHubAuthContent(
    account: GitHubAccount?,
    method: AuthMethod,
    deviceFlow: DeviceFlowState,
    patInput: String,
    clientIdInput: String,
    onClientIdChange: (String) -> Unit,
    requiredScopes: List<String>,
    onMethodChange: (AuthMethod) -> Unit,
    onPatChange: (String) -> Unit,
    onCopyCode: () -> Unit,
    onOpenGitHub: () -> Unit,
    onValidatePat: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    val status = LocalStatusColors.current

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (account != null) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(color = status.successContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.size(44.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Symbol(Symbols.verifiedUser, size = 24.dp, tint = status.onSuccessContainer)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(s.connectedAs, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        Text(account.login, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                    }
                    OutlinedButton(onClick = onDisconnect, shape = MaterialTheme.shapes.large) {
                        Text(s.disconnect, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Column(Modifier.padding(top = 10.dp)) {
                    InfoRow(s.grantedPermissions, account.grantedScopes.joinToString(" · "))
                    if (account.missingScopes.isNotEmpty()) {
                        InfoRow(s.missingPermissions, account.missingScopes.joinToString(" · "))
                    }
                    InfoRow(s.version, account.tokenExpiresLabel)
                }
            }
        }

        TabRow(
            selectedTabIndex = if (method == AuthMethod.DeviceFlow) 0 else 1,
            containerColor = scheme.surface,
            contentColor = scheme.primary,
        ) {
            Tab(
                selected = method == AuthMethod.DeviceFlow,
                onClick = { onMethodChange(AuthMethod.DeviceFlow) },
                text = { Text(s.tabDevice, style = MaterialTheme.typography.labelLarge) },
            )
            Tab(
                selected = method == AuthMethod.PersonalAccessToken,
                onClick = { onMethodChange(AuthMethod.PersonalAccessToken) },
                text = { Text(s.tabPat, style = MaterialTheme.typography.labelLarge) },
            )
        }

        if (method == AuthMethod.DeviceFlow) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.tabDevice, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TagChip(s.recommended, tone = scheme.primaryContainer, content = scheme.onPrimaryContainer)
                }
                Text(s.deviceInstruction, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)

                OutlinedTextField(
                    value = clientIdInput,
                    onValueChange = onClientIdChange,
                    label = { Text(s.oauthClientId) },
                    placeholder = { Text("Iv1.xxxxxxxxxxxxxxxx") },
                    singleLine = true,
                    supportingText = { Text(s.oauthClientIdHint, style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    shape = MaterialTheme.shapes.medium,
                )

                Surface(
                    color = scheme.surfaceContainerHighest,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    Column(
                        Modifier.padding(vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(s.yourCode, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        Text(
                            deviceFlow.userCode,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp,
                            color = scheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            "${s.expiresInSeconds} ${deviceFlow.secondsRemaining} ${s.seconds}",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilledTonalButton(onClick = onCopyCode, shape = MaterialTheme.shapes.large, modifier = Modifier.weight(1f)) {
                        Symbol(Symbols.contentCopy, size = 18.dp)
                        Text("  " + s.copyCode, style = MaterialTheme.typography.labelLarge)
                    }
                    Button(onClick = onOpenGitHub, shape = MaterialTheme.shapes.large, modifier = Modifier.weight(1f)) {
                        Symbol(Symbols.openInNew, size = 18.dp)
                        Text("  " + s.openGithub, style = MaterialTheme.typography.labelLarge)
                    }
                }

                if (deviceFlow.phase == DeviceFlowPhase.WaitingForApproval || deviceFlow.phase == DeviceFlowPhase.CodeIssued) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(s.waitingApproval, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                }
                Text(
                    s.deviceFlowHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        } else {
            Panel {
                Text(s.tabPat, style = MaterialTheme.typography.titleMedium)
                Text(s.patHint, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                OutlinedTextField(
                    value = patInput,
                    onValueChange = onPatChange,
                    label = { Text(s.patLabel) },
                    placeholder = { Text(s.patPlaceholder) },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    shape = MaterialTheme.shapes.medium,
                )
                SectionTitle(s.requiredPermissions, Modifier.padding(top = 16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    requiredScopes.forEach { TagChip(it) }
                }
                Button(
                    onClick = onValidatePat,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                ) {
                    Symbol(Symbols.shield, size = 18.dp)
                    Text("  " + s.validateAndSave, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        NoticeBanner(s.storedSecurely)
    }
}
