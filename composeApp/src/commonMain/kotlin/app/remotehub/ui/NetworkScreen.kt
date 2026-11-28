@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.remotehub.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.remotehub.design.DetailRow
import app.remotehub.design.DestructiveAction
import app.remotehub.design.EmptyState
import app.remotehub.design.IconSize
import app.remotehub.design.flushListRow
import app.remotehub.design.InlineMessage
import app.remotehub.design.LocalStatusColors
import app.remotehub.design.MessageTone
import app.remotehub.design.OutlinedAction
import app.remotehub.design.PrimaryAction
import app.remotehub.design.RowDivider
import app.remotehub.design.ScreenContent
import app.remotehub.design.Section
import app.remotehub.design.Spacing
import app.remotehub.design.SwitchRow
import app.remotehub.design.Symbol
import app.remotehub.design.Symbols
import app.remotehub.design.ltrStyle
import app.remotehub.domain.NetworkState
import app.remotehub.domain.TailnetStatus
import app.remotehub.i18n.LocalStrings

data class TailnetDevice(val hostname: String, val address: String, val online: Boolean)

/**
 * The tunnel's state and the one switch that decides whether the tailnet belongs to this
 * app alone or to the whole phone.
 *
 * The old page opened with a saturated blue hero card containing a scalloped badge, then
 * a card containing two more coloured banners and a capsule button, then a card per peer
 * with a different decorative outline each. It is now four flat sections; the switch is
 * the control for the switch row, and the button below it is the explicit consent action.
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
    val status = LocalStatusColors.current
    val online = tailnet.state == NetworkState.Online
    val connecting = tailnet.state == NetworkState.Connecting || tailnet.state == NetworkState.Authenticating

    Box(Modifier.fillMaxSize().padding(padding)) {
        ScreenContent(modifier) {
            // ---- tunnel ----
            Section(title = s.networkTitle) {
                ListItem(
                    modifier = Modifier.fillMaxWidth().flushListRow(),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        Surface(
                            color = if (online) scheme.primaryContainer else scheme.surfaceContainerHighest,
                            contentColor = if (online) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                            shape = CircleShape,
                            modifier = Modifier.size(IconSize.large),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Symbol(if (online) Symbols.verifiedUser else Symbols.vpnLock, size = IconSize.standard, filled = online)
                            }
                        }
                    },
                    headlineContent = {
                        Text(
                            if (online) s.online else if (connecting) s.working else s.offline,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (online) status.success else scheme.onSurface,
                        )
                    },
                    supportingContent = {
                        Text(
                            if (online) "${tailnet.tailnetName} · ${tailnet.nodeAddress}" else s.networkScreenSubtitle,
                            style = ltrStyle(),
                            color = scheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = if (connecting) {
                        { CircularProgressIndicator(Modifier.size(IconSize.medium), strokeWidth = 2.dp) }
                    } else null,
                )
                if (tailnet.lastError != null) {
                    InlineMessage(tailnet.lastError!!, tone = MessageTone.Error)
                }
                if (online) {
                    OutlinedAction(s.disconnectNetwork, onDisconnect, glyph = Symbols.linkOff, modifier = Modifier.fillMaxWidth())
                } else {
                    PrimaryAction(s.connectNetwork, onConnect, glyph = Symbols.vpnKey, enabled = !connecting, modifier = Modifier.fillMaxWidth())
                }
            }

            RowDivider()

            // ---- whole-device access ----
            Section(title = s.deviceAccess, subtitle = s.deviceAccessHint) {
                SwitchRow(
                    title = s.deviceAccess,
                    subtitle = if (deviceVpn) s.vpnOn else s.vpnOff,
                    checked = deviceVpn,
                    onCheckedChange = { onToggleVpn() },
                    enabled = online && !vpnBusy,
                    control = {
                        Switch(
                            checked = deviceVpn,
                            onCheckedChange = { onToggleVpn() },
                            enabled = online && !vpnBusy,
                        )
                    },
                )
                if (deviceVpn && vpnInterface.isNotBlank()) {
                    DetailRow(s.vpnInterface, vpnInterface, mono = true)
                }
                InlineMessage(s.vpnScope, tone = MessageTone.Info)
                InlineMessage(s.vpnConflict, tone = MessageTone.Warning)
                if (deviceVpn) {
                    DestructiveAction(
                        text = s.vpnDisable,
                        onClick = onToggleVpn,
                        glyph = Symbols.powerSettingsNew,
                        enabled = online && !vpnBusy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    PrimaryAction(
                        text = if (vpnBusy) s.vpnWorking else s.vpnEnable,
                        onClick = onToggleVpn,
                        glyph = Symbols.vpnLock,
                        enabled = online && !vpnBusy,
                        loading = vpnBusy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            RowDivider()

            // ---- node facts ----
            Section(title = s.embeddedNode) {
                DetailRow(s.tailnet, tailnet.tailnetName.ifBlank { "—" })
                DetailRow(s.nodeName, tailnet.nodeName.ifBlank { "—" })
                DetailRow(
                    s.nodeAddress,
                    tailnet.nodeAddress.ifBlank { "—" },
                    mono = true,
                    onCopy = if (tailnet.nodeAddress.isNotBlank()) {
                        { onCopy(s.nodeAddress, tailnet.nodeAddress) }
                    } else null,
                )
                DetailRow(s.relay, tailnet.relayLabel.ifBlank { "—" })
                DetailRow(s.transport, if (deviceVpn) "tun" else tailnet.transportLabel.ifBlank { "userspace" }, mono = true)
                if (tailnet.latencyMillis > 0) {
                    DetailRow(s.latency, "${tailnet.latencyMillis} ms", mono = true)
                }
            }

            RowDivider()

            // ---- peers ----
            Section(title = s.peersTitle) {
                if (devices.isEmpty()) {
                    EmptyState(Symbols.devices, s.peersEmpty, s.networkSubtitle)
                } else {
                    devices.forEach { device ->
                        ListItem(
                            modifier = Modifier.fillMaxWidth().flushListRow(),
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = {
                                Symbol(Symbols.computer, size = IconSize.standard, tint = scheme.onSurfaceVariant)
                            },
                            headlineContent = {
                                Text(device.hostname, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = { Text(device.address, style = ltrStyle(), color = scheme.onSurfaceVariant) },
                            trailingContent = {
                                Symbol(
                                    if (device.online) Symbols.checkCircle else Symbols.hourglassEmpty,
                                    size = IconSize.medium,
                                    tint = if (device.online) status.success else scheme.outline,
                                    filled = device.online,
                                )
                            },
                        )
                        RowDivider()
                    }
                }
            }
        }
    }
}
