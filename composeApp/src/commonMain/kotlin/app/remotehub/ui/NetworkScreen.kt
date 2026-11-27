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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.remotehub.design.InfoRow
import app.remotehub.design.LocalStatusColors
import app.remotehub.design.NoticeBanner
import app.remotehub.design.Panel
import app.remotehub.design.SectionTitle
import app.remotehub.design.Symbol
import app.remotehub.design.Symbols
import app.remotehub.design.TagChip
import app.remotehub.design.WarningBanner
import app.remotehub.domain.NetworkState
import app.remotehub.domain.TailnetStatus
import app.remotehub.i18n.LocalStrings

@Composable
fun NetworkContent(
    status: TailnetStatus,
    authKeyInput: String,
    onAuthKeyChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOfficialSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    val colors = LocalStatusColors.current
    val online = status.state == NetworkState.Online

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(
                    color = if (online) colors.successContainer else scheme.surfaceVariant,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Symbol(
                            if (online) Symbols.link else Symbols.linkOff,
                            size = 26.dp,
                            tint = if (online) colors.onSuccessContainer else scheme.onSurfaceVariant,
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(s.embeddedNode, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                    Text(
                        when (status.state) {
                            NetworkState.Online -> s.online
                            NetworkState.Offline -> s.offline
                            NetworkState.Connecting -> s.stateProvisioning
                            NetworkState.Authenticating -> s.waitingApproval
                            NetworkState.Error -> s.stateFailed
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (online) colors.success else scheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TagChip(s.userspaceMode)
                TagChip(s.noRootRequired)
                TagChip(s.vpnCoexistence)
            }

            Column(Modifier.padding(top = 12.dp)) {
                InfoRow(s.tailnet, status.tailnetName)
                InfoRow(s.nodeName, status.nodeName, monospace = true)
                InfoRow(s.nodeAddress, status.nodeAddress, monospace = true, onCopy = {})
                InfoRow(s.relay, status.relayLabel)
                InfoRow(s.latency, "${status.latencyMillis} ms")
                InfoRow(s.transport, status.transportLabel)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (online) {
                    OutlinedButton(onClick = onDisconnect, shape = MaterialTheme.shapes.large, modifier = Modifier.weight(1f)) {
                        Symbol(Symbols.linkOff, size = 18.dp)
                        Text("  " + s.disconnectNetwork, style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Button(onClick = onConnect, shape = MaterialTheme.shapes.large, modifier = Modifier.weight(1f)) {
                        Symbol(Symbols.link, size = 18.dp)
                        Text("  " + s.connectNetwork, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Button(
                    onClick = onOfficialSignIn,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.secondaryContainer, contentColor = scheme.onSecondaryContainer),
                ) {
                    Symbol(Symbols.openInNew, size = 18.dp)
                    Text("  " + s.officialSignIn, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (status.lastError != null) {
            WarningBanner(status.lastError)
        }

        Panel {
            SectionTitle(s.authKey)
            Text(s.authKeyHint, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            OutlinedTextField(
                value = authKeyInput,
                onValueChange = onAuthKeyChange,
                label = { Text(s.authKey) },
                placeholder = { Text("tskey-auth-…") },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                shape = MaterialTheme.shapes.medium,
            )
        }

        NoticeBanner(s.networkSubtitle)
    }
}
