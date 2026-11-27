package app.remotehub.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.remotehub.app.AppState
import app.remotehub.app.SetupItem
import app.remotehub.design.ExpressiveShapes
import app.remotehub.design.InfoRow
import app.remotehub.design.NoticeBanner
import app.remotehub.design.Panel
import app.remotehub.design.SectionTitle
import app.remotehub.design.SetupRow
import app.remotehub.design.StatePill
import app.remotehub.design.Symbol
import app.remotehub.design.Symbols
import app.remotehub.design.TimeMeter
import app.remotehub.design.WarningBanner
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
    val scroll = rememberScrollState()
    var githubTop by remember { mutableIntStateOf(0) }
    var networkTop by remember { mutableIntStateOf(0) }

    // An incomplete checklist sends the user straight to the item that is missing.
    LaunchedEffect(state.highlightItem) {
        when (state.highlightItem) {
            SetupItem.GitHub -> scroll.animateScrollTo(githubTop)
            SetupItem.Network -> scroll.animateScrollTo(networkTop)
            null -> Unit
        }
    }

    val highlightShape = MaterialTheme.shapes.large
    val highlightColor = scheme.primary

    fun highlight(item: SetupItem): Modifier =
        if (state.highlightItem == item) {
            Modifier.border(2.dp, highlightColor, highlightShape)
        } else {
            Modifier
        }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val active = state.servers.firstOrNull {
            it.state == ServerState.Ready || it.state == ServerState.Connected ||
                it.state == ServerState.Expiring || it.state == ServerState.Provisioning
        }
        if (active != null) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(active.name, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                        Text(
                            if (active.remainingLabel.isBlank()) s.stateProvisioning else active.remainingLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    StatePill(active.state)
                }
                if (active.remainingFraction > 0f) {
                    TimeMeter(active.remainingFraction, active.remainingLabel, s.timeRemaining, Modifier.padding(top = 10.dp))
                }
                Button(
                    onClick = { onOpenServer(active.id) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Text(s.openServer, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Brand header: the app's own mark, one line of intent, no decoration.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // The mark is stored as a white alpha mask, so it is tinted with the
            // theme's ink: visible on a light surface and on a dark one alike.
            Image(
                painter = painterResource(Res.drawable.brand_mark),
                contentDescription = null,
                colorFilter = ColorFilter.tint(scheme.onSurface),
                modifier = Modifier.size(44.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(s.setupTitle, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
                Text(s.brandTagline, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
        Text(s.setupSubtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)

        // ---- item 1: GitHub ----
        Panel(
            highlight(SetupItem.GitHub).onGloballyPositioned { githubTop = it.positionInParent().y.toInt() },
        ) {
            SetupRow(
                glyph = Symbols.accountCircle,
                title = s.setupGithubTitle,
                statusText = if (state.githubReady) "@${state.account?.login}" else s.setupGithubHint,
                ok = state.githubReady,
                shape = ExpressiveShapes.clover4,
            )
            if (state.githubReady) {
                OutlinedButton(
                    onClick = onDisconnectGitHub,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text(s.disconnect, style = MaterialTheme.typography.labelLarge) }
            } else {
                Text(s.setupGithubWhy, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Button(
                    onClick = { onOpenUrl(CREATE_TOKEN_URL) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Symbol(Symbols.openInNew, size = 18.dp)
                    Text("  " + s.setupMakeToken, style = MaterialTheme.typography.labelLarge)
                }
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberedStep(1, s.setupTokenStep1)
                    NumberedStep(2, s.setupTokenStep2)
                    NumberedStep(3, s.setupTokenStep3)
                }
                OutlinedTextField(
                    value = state.tokenInput,
                    onValueChange = onTokenChange,
                    label = { Text(s.setupPasteToken) },
                    placeholder = { Text("github_pat_…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    shape = MaterialTheme.shapes.medium,
                )
                Button(
                    onClick = onConnectGitHub,
                    enabled = state.tokenInput.isNotBlank() && !state.busy,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("  " + s.working, style = MaterialTheme.typography.labelLarge)
                    } else {
                        Text(s.connect, style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (state.account != null && state.account.missingScopes.isNotEmpty()) {
                    WarningBanner(s.scopeMissing, Modifier.padding(top = 10.dp))
                }
            }
        }

        // ---- item 2: Tailscale ----
        Panel(
            highlight(SetupItem.Network).onGloballyPositioned { networkTop = it.positionInParent().y.toInt() },
        ) {
            SetupRow(
                glyph = Symbols.lan,
                title = s.setupNetworkTitle,
                statusText = when {
                    state.networkReady -> s.setupNetworkDone
                    state.tailnet.state == NetworkState.Connecting ||
                        state.tailnet.state == NetworkState.Authenticating -> s.working
                    state.tailnet.lastError != null -> state.tailnet.lastError!!
                    else -> s.setupNetworkHint
                },
                ok = state.networkReady,
                shape = ExpressiveShapes.gem,
            )
            if (state.networkReady) {
                InfoRow(s.address, state.tailnet.nodeAddress, monospace = true)
                OutlinedButton(
                    onClick = onDisconnectNetwork,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text(s.disconnect, style = MaterialTheme.typography.labelLarge) }
            } else {
                Text(s.setupNetworkWhy, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Button(
                    onClick = { onOpenUrl(TAILSCALE_KEYS_URL) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Symbol(Symbols.openInNew, size = 18.dp)
                    Text("  " + s.setupOpenKeys, style = MaterialTheme.typography.labelLarge)
                }
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberedStep(1, s.setupKeyStep1)
                    NumberedStep(2, s.setupKeyStep2)
                    NumberedStep(3, s.setupKeyStep3)
                }
                OutlinedTextField(
                    value = state.authKeyInput,
                    onValueChange = onAuthKeyChange,
                    label = { Text(s.setupPasteKey) },
                    placeholder = { Text("tskey-auth-…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    shape = MaterialTheme.shapes.medium,
                )
                Button(
                    onClick = onConnectNetwork,
                    enabled = state.authKeyInput.isNotBlank() && !state.busy,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) { Text(s.setupSaveConnect, style = MaterialTheme.typography.labelLarge) }
            }
        }

        if (state.error != null) {
            WarningBanner(state.error!!, Modifier.clickable(onClick = onOpenLogs))
            TextButton(onClick = onOpenLogs, modifier = Modifier.fillMaxWidth()) {
                Symbol(Symbols.terminal, size = 18.dp)
                Text("  " + s.viewLogs, style = MaterialTheme.typography.labelLarge)
            }
        }

        NoticeBanner(s.setupRiskNote)

        Button(
            onClick = onCreate,
            enabled = state.setupComplete && !state.provisioning,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Symbol(Symbols.add, size = 20.dp)
            Text("  " + s.setupCreateServer, style = MaterialTheme.typography.labelLarge)
        }
        if (!state.setupComplete) {
            Text(
                when (state.missingItem) {
                    SetupItem.GitHub -> s.setupMissingGithub
                    SetupItem.Network -> s.setupMissingNetwork
                    null -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun NumberedStep(index: Int, text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Surface(
            color = scheme.secondaryContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.size(22.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    index.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSecondaryContainer,
                )
            }
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}
