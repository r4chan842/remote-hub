package app.remotehub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.remotehub.app.AppState
import app.remotehub.app.Route
import app.remotehub.app.Tab
import app.remotehub.design.RemoteHubTheme
import app.remotehub.design.ScreenContent
import app.remotehub.design.Section
import app.remotehub.i18n.LocalStrings
import app.remotehub.i18n.stringsFor
import app.remotehub.ui.AppShell
import app.remotehub.ui.NetworkContent
import app.remotehub.ui.ProgressContent
import app.remotehub.ui.ServersContent
import app.remotehub.ui.SettingsContent
import app.remotehub.ui.SetupContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * The desktop target is a design bench, not a second product.
 *
 * It exists so `renderScreens` can rasterise the real Compose implementation in CI and a
 * reviewer can look at the same code the phone runs. It used to be a parallel demo with
 * its own navigation and its own fixtures, which drifted from the Android host; it now
 * drives the same [AppState] and the same screens through the same routes.
 */
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "RemoteHub · design bench") {
        Bench()
    }
}

@Composable
private fun Bench() {
    var state by remember { mutableStateOf(AppState()) }
    val strings = stringsFor(state.localeTag)

    RemoteHubTheme(darkTheme = true) {
        CompositionLocalProvider(
            LocalStrings provides strings,
            LocalLayoutDirection provides if (strings.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            val route = state.nav.route
            AppShell(
                selected = state.tab,
                onSelect = { state = state.selectTab(it) },
                title = when (route) {
                    Route.Setup -> strings.setupTitle
                    Route.Create -> strings.createTitle
                    Route.Progress -> strings.progressTitle
                    Route.Servers -> strings.serversTitle
                    Route.Network -> strings.networkScreenTitle
                    Route.Settings -> strings.settingsTitle
                    Route.Logs -> strings.logsTitle
                    is Route.Guide -> strings.guides
                    is Route.Detail -> strings.sessionDetail
                    is Route.Remote -> strings.remoteDesktop
                },
            ) { padding ->
                val m = Modifier.padding(padding)
                when (route) {
                    Route.Setup -> SetupContent(
                        state,
                        { state = state.copy(tokenInput = it) },
                        {},
                        { state = state.copy(authKeyInput = it) },
                        {},
                        {},
                        { state = state.at(Route.Create) },
                        {},
                        {},
                        {},
                        {},
                        m,
                    )
                    Route.Servers -> ServersContent(
                        state.servers, PaddingValues(0.dp),
                        { state = state.at(Route.Detail(it)) }, {}, {},
                        { state = state.at(Route.Create) }, m,
                    )
                    Route.Network -> NetworkContent(
                        state.tailnet, emptyList(), state.deviceVpn, false, "",
                        PaddingValues(0.dp), {}, {}, {}, { _, _ -> }, m,
                    )
                    Route.Settings -> SettingsContent(
                        state.themeMode, state.localeTag, state.expiryAlertEnabled, state.warningMinutes,
                        { state = state.copy(themeMode = it) },
                        { state = state.copy(localeTag = it) },
                        { state = state.copy(expiryAlertEnabled = it) },
                        { state = state.copy(warningMinutes = it) },
                        { state = state.at(Route.Guide(it)) },
                        { state = state.at(Route.Logs) },
                        m,
                    )
                    Route.Progress -> ProgressContent(state, {}, {}, {}, m)
                    else -> Box(Modifier.fillMaxSize()) {
                        ScreenContent {
                            Section(strings.appName) {
                                Text(
                                    "Design bench: use the navigation to reach Setup, Servers, Network and Settings. " +
                                        "Run ./gradlew :composeApp:renderScreens for the full rasterised set.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
