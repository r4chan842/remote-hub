package app.remotehub.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.remotehub.app.AppState
import app.remotehub.app.SetupItem
import app.remotehub.design.DetailRow
import app.remotehub.design.ExpandableHelp
import app.remotehub.design.IconSize
import app.remotehub.design.InlineMessage
import app.remotehub.design.MessageTone
import app.remotehub.design.label
import app.remotehub.design.NavigationRow
import app.remotehub.design.PrimaryAction
import app.remotehub.design.RowDivider
import app.remotehub.design.ScreenContent
import app.remotehub.design.Section
import app.remotehub.design.Spacing
import app.remotehub.design.Symbol
import app.remotehub.design.Symbols
import app.remotehub.design.TextAction
import app.remotehub.design.TonalAction
import app.remotehub.design.ltrStyle
import app.remotehub.domain.NetworkState
import app.remotehub.domain.ServerState
import app.remotehub.i18n.LocalStrings
import app.remotehub.resources.Res
import app.remotehub.resources.brand_mark
import org.jetbrains.compose.resources.painterResource

/** GitHub's token page with the two required scopes already ticked. */
const val CREATE_TOKEN_URL =
    "https://github.com/settings/tokens/new?scopes=repo,workflow&description=RemoteHub"

/** Tailscale's key panel. */
const val TAILSCALE_KEYS_URL = "https://login.tailscale.com/admin/settings/keys"

/**
 * Setup: connect the two services the app needs, then create a server.
 *
 * The previous screen put a status row, an explanation paragraph, a link button, three
 * numbered instructions, a masked field and a submit button inside one rounded card, and
 * repeated the whole block for the second service. Both cards were taller than a phone
 * screen, so the primary action was never visible while reading the instructions.
 *
 * Now: a progress header, one flat section per service, and the instructions collapsed
 * behind a disclosure. The create action is the only filled button on the screen.
 */
@Composable
fun SetupContent(
    state: AppState,
    onTokenChange: (String) -> Unit,
    onConnectGitHub: () -> Unit,
    onAuthKeyChange: (String) -> Unit,
    onConnectNetwork: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onCreate: () -> Unit,
    onOpenServer: (String) -> Unit,
    onDisconnectGitHub: () -> Unit,
    onDisconnectNetwork: () -> Unit,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme

    // Which service is being connected, so only that button shows a spinner. A single
    // global `busy` flag used to spin both submit buttons at once.
    var pending by remember { mutableStateOf<SetupItem?>(null) }
    var confirmDisconnect by remember { mutableStateOf<SetupItem?>(null) }
    var helpOpen by remember { mutableStateOf<SetupItem?>(null) }
    LaunchedEffect(state.busy, state.githubReady, state.networkReady) {
        if (!state.busy) pending = null
    }

    val connectedCount = (if (state.githubReady) 1 else 0) + (if (state.networkReady) 1 else 0)
    val live = state.servers.firstOrNull {
        it.state == ServerState.Ready || it.state == ServerState.Connected ||
            it.state == ServerState.Expiring || it.state == ServerState.Provisioning
    }

    ScreenContent(modifier) {
        // Product identity, once, with no container behind the mark.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Image(
                painter = painterResource(Res.drawable.brand_mark),
                contentDescription = null,
                colorFilter = ColorFilter.tint(scheme.onSurface),
                modifier = Modifier.size(IconSize.large),
            )
            Column(Modifier.weight(1f)) {
                Text(s.appName, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                Text(s.brandTagline, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }

        // A live machine is the most useful thing on this screen; one row, not a card.
        if (live != null) {
            NavigationRow(
                title = live.name,
                subtitle = live.remainingLabel.ifBlank { s.stateProvisioning },
                glyph = Symbols.desktopWindows,
                trailingText = live.state.label(s),
                onClick = { onOpenServer(live.id) },
            )
            RowDivider()
        }

        // Connection progress. Two requirements, stated once.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.requiredConnections,
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$connectedCount ${s.ofLabel} 2",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { connectedCount / 2f },
                modifier = Modifier.fillMaxWidth().height(Spacing.xs),
                color = if (state.setupComplete) app.remotehub.design.LocalStatusColors.current.success else scheme.primary,
                trackColor = scheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
            )
            Text(
                if (state.setupComplete) s.setupDone
                else when (state.missingItem) {
                    SetupItem.GitHub -> s.setupMissingGithub
                    SetupItem.Network -> s.setupMissingNetwork
                    null -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }

        // ---- service 1: GitHub ----
        ServiceSection(
            glyph = Symbols.accountCircle,
            title = s.githubAccount,
            ready = state.githubReady,
            statusText = if (state.githubReady) "@${state.account?.login}" else s.setupGithubHint,
            explanation = s.setupGithubWhy,
            helpLabel = s.setupHelpToken,
            helpOpen = helpOpen == SetupItem.GitHub,
            onToggleHelp = { helpOpen = if (helpOpen == SetupItem.GitHub) null else SetupItem.GitHub },
            steps = listOf(s.setupTokenStep1, s.setupTokenStep2, s.setupTokenStep3),
            openLabel = s.setupMakeToken,
            onOpen = { onOpenUrl(CREATE_TOKEN_URL) },
            inputValue = state.tokenInput,
            onInputChange = onTokenChange,
            inputLabel = s.setupPasteToken,
            inputPlaceholder = "github_pat_…",
            connectLabel = s.connect,
            connecting = pending == SetupItem.GitHub,
            onConnect = { pending = SetupItem.GitHub; onConnectGitHub() },
            onDisconnect = { confirmDisconnect = SetupItem.GitHub },
        )
        if (state.account != null && state.account.missingScopes.isNotEmpty()) {
            InlineMessage(s.scopeMissing, tone = MessageTone.Warning)
        }

        RowDivider()

        // ---- service 2: private network ----
        ServiceSection(
            glyph = Symbols.lan,
            title = s.privateNetwork,
            ready = state.networkReady,
            statusText = when {
                state.networkReady -> s.setupNetworkDone
                state.tailnet.state == NetworkState.Connecting ||
                    state.tailnet.state == NetworkState.Authenticating -> s.working
                state.tailnet.lastError != null -> state.tailnet.lastError!!
                else -> s.setupNetworkHint
            },
            explanation = s.setupNetworkWhy,
            helpLabel = s.setupHelpKey,
            helpOpen = helpOpen == SetupItem.Network,
            onToggleHelp = { helpOpen = if (helpOpen == SetupItem.Network) null else SetupItem.Network },
            steps = listOf(s.setupKeyStep1, s.setupKeyStep2, s.setupKeyStep3),
            openLabel = s.setupOpenKeys,
            onOpen = { onOpenUrl(TAILSCALE_KEYS_URL) },
            inputValue = state.authKeyInput,
            onInputChange = onAuthKeyChange,
            inputLabel = s.setupPasteKey,
            inputPlaceholder = "tskey-auth-…",
            connectLabel = s.setupSaveConnect,
            connecting = pending == SetupItem.Network,
            onConnect = { pending = SetupItem.Network; onConnectNetwork() },
            onDisconnect = { confirmDisconnect = SetupItem.Network },
            connectedDetail = if (state.networkReady) s.nodeAddress to state.tailnet.nodeAddress else null,
        )

        if (state.error != null) {
            InlineMessage(state.error!!, tone = MessageTone.Error, onClick = onOpenLogs)
            TextAction(s.viewLogs, onOpenLogs, glyph = Symbols.terminal)
        }

        // The one primary action. Supporting text explains why it is disabled.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PrimaryAction(
                text = s.createServer,
                onClick = onCreate,
                glyph = Symbols.add,
                enabled = state.setupComplete && !state.operation.isActive,
                modifier = Modifier.fillMaxWidth(),
            )
            InlineMessage(s.setupRiskNote, tone = MessageTone.Info)
        }
    }

    val target = confirmDisconnect
    if (target != null) {
        app.remotehub.design.ConfirmDialog(
            title = s.disconnectConfirmTitle,
            body = s.disconnectConfirmBody,
            confirmLabel = s.disconnect,
            dismissLabel = s.cancel,
            destructive = true,
            glyph = Symbols.linkOff,
            onConfirm = {
                confirmDisconnect = null
                if (target == SetupItem.GitHub) onDisconnectGitHub() else onDisconnectNetwork()
            },
            onDismiss = { confirmDisconnect = null },
        )
    }
}

/**
 * One service: status, then either the connected summary or the credential form. The
 * long instructions live behind a disclosure so the submit button stays reachable.
 */
@Composable
private fun ServiceSection(
    glyph: String,
    title: String,
    ready: Boolean,
    statusText: String,
    explanation: String,
    helpLabel: String,
    helpOpen: Boolean,
    onToggleHelp: () -> Unit,
    steps: List<String>,
    openLabel: String,
    onOpen: () -> Unit,
    inputValue: String,
    onInputChange: (String) -> Unit,
    inputLabel: String,
    inputPlaceholder: String,
    connectLabel: String,
    connecting: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    connectedDetail: Pair<String, String>? = null,
) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    val status = app.remotehub.design.LocalStatusColors.current

    Section(title = title) {
        ListItem(
            modifier = Modifier.fillMaxWidth(),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Symbol(
                    if (ready) Symbols.checkCircle else glyph,
                    size = IconSize.standard,
                    tint = if (ready) status.success else scheme.onSurfaceVariant,
                    filled = ready,
                )
            },
            headlineContent = {
                Text(
                    statusText,
                    style = if (ready && statusText.startsWith("@")) ltrStyle() else MaterialTheme.typography.bodyMedium,
                    color = if (ready) scheme.onSurface else scheme.onSurfaceVariant,
                )
            },
            trailingContent = if (ready) {
                { TextAction(s.disconnect, onDisconnect) }
            } else null,
        )

        if (ready) {
            if (connectedDetail != null) {
                DetailRow(connectedDetail.first, connectedDetail.second, mono = true)
            }
        } else {
            Text(
                explanation,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
            OutlinedTextField(
                value = inputValue,
                onValueChange = onInputChange,
                label = { Text(inputLabel) },
                placeholder = { Text(inputPlaceholder) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraSmall,
            )
            Spacer(Modifier.height(Spacing.sm))
            PrimaryAction(
                text = if (connecting) s.working else connectLabel,
                onClick = onConnect,
                enabled = inputValue.isNotBlank(),
                loading = connecting,
                modifier = Modifier.fillMaxWidth(),
            )
            ExpandableHelp(helpLabel, helpOpen, onToggleHelp) {
                steps.forEachIndexed { index, text -> NumberedStep(index + 1, text) }
                TonalAction(openLabel, onOpen, glyph = Symbols.openInNew)
            }
        }
    }
}

@Composable
internal fun NumberedStep(index: Int, text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Top) {
        Text(
            "$index.",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.primary,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}
