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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.remotehub.design.CornerRadius
import app.remotehub.design.EmptyState
import app.remotehub.design.ExpressiveShapes
import app.remotehub.design.IconBadge
import app.remotehub.design.InfoRow
import app.remotehub.design.NoticeBanner
import app.remotehub.design.Panel
import app.remotehub.design.SectionTitle
import app.remotehub.design.Symbol
import app.remotehub.design.Symbols
import app.remotehub.design.WarningBanner
import app.remotehub.domain.NetworkState
import app.remotehub.domain.TailnetStatus
import app.remotehub.i18n.LocalStrings

data class TailnetDevice(val hostname: String, val address: String, val online: Boolean)

/**
 * The network page: what the tunnel is doing, and the one switch that decides whether
 * the tailnet belongs to this app alone or to the whole phone.
 */
@Composable
fun NetworkContent(
    tailnet: TailnetStatus,
    devices: List<TailnetDevice>,
    deviceVpn: Boolean,
    vpnBusy: Boolean,
    vpnInterface: String,
    padding: PaddingValues,
    onToggleVpn: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onCopy: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    val online = tailnet.state == NetworkState.Online

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 28.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ---------------------------------------------------------- tunnel state
        Surface(
            shape = CornerRadius.extraLarge,
            color = if (online) scheme.primaryContainer else scheme.surfaceContainerHigh,
            contentColor = if (online) scheme.onPrimaryContainer else scheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IconBadge(
                    glyph = if (online) Symbols.verifiedUser else Symbols.vpnLock,
                    shape = if (online) ExpressiveShapes.cookie9 else ExpressiveShapes.gem,
                    container = if (online) scheme.primary else scheme.surfaceContainerHighest,
                    content = if (online) scheme.onPrimary else scheme.onSurfaceVariant,
                    size = 52.dp,
                    filled = online,
                )
                Column(Modifier.weight(1f)) {
                    Text(s.networkScreenTitle, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (online) "${tailnet.tailnetName} · ${tailnet.nodeAddress}" else s.networkScreenSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (tailnet.state == NetworkState.Connecting) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
        }

        // ------------------------------------------------- whole-device access
        Panel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(
                        glyph = Symbols.phoneAndroid,
                        shape = ExpressiveShapes.clover4,
                        container = scheme.tertiaryContainer,
                        content = scheme.onTertiaryContainer,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(s.deviceAccess, style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (deviceVpn) s.vpnOn else s.vpnOff,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = deviceVpn,
                        onCheckedChange = { onToggleVpn() },
                        enabled = online && !vpnBusy,
                    )
                }
                Text(s.deviceAccessHint, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                NoticeBanner(s.vpnScope)
                WarningBanner(s.vpnConflict)
                if (vpnBusy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(s.vpnWorking, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (deviceVpn && vpnInterface.isNotBlank()) {
                    InfoRow(s.vpnInterface, vpnInterface, monospace = true)
                }
                Button(
                    onClick = onToggleVpn,
                    enabled = online && !vpnBusy,
                    shape = CornerRadius.full,
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (deviceVpn) {
                        ButtonDefaults.buttonColors(containerColor = scheme.errorContainer, contentColor = scheme.onErrorContainer)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) {
                    Symbol(if (deviceVpn) Symbols.powerSettingsNew else Symbols.vpnLock, size = 18.dp)
                    Box(Modifier.size(8.dp))
                    Text(if (deviceVpn) s.vpnDisable else s.vpnEnable)
                }
            }
        }

        // ------------------------------------------------------------- node facts
        SectionTitle(s.networkTitle)
        Panel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                InfoRow(s.tailnet, tailnet.tailnetName.ifBlank { "—" })
                InfoRow(s.nodeName, tailnet.nodeName.ifBlank { "—" })
                InfoRow(s.nodeAddress, tailnet.nodeAddress.ifBlank { "—" }, monospace = true, onCopy = { onCopy(s.nodeAddress, tailnet.nodeAddress) })
                InfoRow(s.relay, tailnet.relayLabel.ifBlank { "—" })
                InfoRow(s.transport, if (deviceVpn) "tun" else tailnet.transportLabel.ifBlank { "userspace" }, monospace = true)
            }
        }
        tailnet.lastError?.let { WarningBanner(it) }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            if (online) {
                Button(
                    onClick = onDisconnect,
                    shape = CornerRadius.full,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.surfaceContainerHighest, contentColor = scheme.onSurface),
                ) { Text(s.disconnectNetwork, maxLines = 1) }
            } else {
                Button(onClick = onConnect, shape = CornerRadius.full, modifier = Modifier.weight(1f)) {
                    Symbol(Symbols.vpnKey, size = 18.dp)
                    Box(Modifier.size(8.dp))
                    Text(s.connectNetwork, maxLines = 1)
                }
            }
        }

        // ---------------------------------------------------------------- peers
        SectionTitle(s.peersTitle)
        if (devices.isEmpty()) {
            EmptyState(Symbols.devices, s.peersEmpty, s.networkSubtitle)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                devices.forEachIndexed { index, device ->
                    Surface(
                        shape = CornerRadius.largeIncreased,
                        color = scheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            IconBadge(
                                glyph = Symbols.computer,
                                shape = ExpressiveShapes.forIndex(index + 2),
                                size = 38.dp,
                                container = scheme.surfaceContainerHighest,
                                content = scheme.onSurfaceVariant,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(device.hostname, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                Text(device.address, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                            }
                            Symbol(
                                if (device.online) Symbols.checkCircle else Symbols.hourglassEmpty,
                                size = 20.dp,
                                tint = if (device.online) scheme.primary else scheme.onSurfaceVariant,
                                filled = device.online,
                            )
                        }
                    }
                }
            }
        }
    }
}
