@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.remotehub.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.remotehub.app.Tab
import app.remotehub.design.IconSize
import app.remotehub.design.LocalWindowClass
import app.remotehub.design.Spacing
import app.remotehub.design.Symbol
import app.remotehub.design.Symbols
import app.remotehub.design.WindowClass
import app.remotehub.i18n.AppStrings
import app.remotehub.i18n.LocalStrings

/**
 * The four persistent destinations. Kept as a UI-level alias of [Tab] so the navigation
 * model owns the truth and the shell only renders it.
 */
typealias Destination = Tab

fun Destination.glyph(): String = when (this) {
    Tab.Home -> Symbols.dashboard
    Tab.Servers -> Symbols.dns
    Tab.Network -> Symbols.hub
    Tab.Settings -> Symbols.settings
}

fun Destination.label(s: AppStrings): String = when (this) {
    Tab.Home -> s.navDashboard
    Tab.Servers -> s.navServers
    Tab.Network -> s.navNetwork
    Tab.Settings -> s.navSettings
}

/**
 * The application chrome.
 *
 * Changes from the previous version:
 *  - The window class is measured, not assumed. `expanded` used to be hardcoded false at
 *    the call site, so the navigation rail existed but could never appear on a tablet.
 *  - The navigation bar is flat. It was clipped to 20 dp top corners, which is not part of
 *    the component and produced a floating slab above the system bar.
 *  - The top bar carries a single title line. A second subtitle line inside a small top
 *    app bar is not the component's layout; supporting copy belongs to the screen.
 *  - The new-server action uses the real FAB / ExtendedFAB instead of a Row inside a FAB.
 */
@Composable
fun AppShell(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    floatingAction: (@Composable () -> Unit)? = null,
    snackbarHost: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme

    BoxWithConstraints(modifier.fillMaxSize()) {
        val window = WindowClass.forWidth(maxWidth.value.toInt())
        CompositionLocalProvider(LocalWindowClass provides window) {
            val bar: @Composable () -> Unit = {
                TopAppBar(
                    title = {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = { if (navigationIcon != null) navigationIcon() },
                    actions = { if (actions != null) actions() },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = scheme.surface,
                        titleContentColor = scheme.onSurface,
                        navigationIconContentColor = scheme.onSurface,
                        actionIconContentColor = scheme.onSurfaceVariant,
                    ),
                )
            }

            if (window.useRail) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = scheme.surface,
                        contentColor = scheme.onSurfaceVariant,
                        header = { Spacer(Modifier.height(Spacing.lg)) },
                    ) {
                        Destination.entries.forEach { destination ->
                            val active = destination == selected
                            NavigationRailItem(
                                selected = active,
                                onClick = { onSelect(destination) },
                                icon = { Symbol(destination.glyph(), size = IconSize.standard, filled = active) },
                                label = { Text(destination.label(s), style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                            )
                        }
                    }
                    Scaffold(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        containerColor = scheme.surface,
                        topBar = bar,
                        floatingActionButton = { if (floatingAction != null) floatingAction() },
                        snackbarHost = { if (snackbarHost != null) snackbarHost() },
                        content = { padding -> content(padding) },
                    )
                }
            } else {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = scheme.surface,
                    topBar = bar,
                    bottomBar = {
                        NavigationBar(
                            containerColor = scheme.surfaceContainer,
                            contentColor = scheme.onSurfaceVariant,
                            tonalElevation = 0.dp,
                        ) {
                            Destination.entries.forEach { destination ->
                                val active = destination == selected
                                NavigationBarItem(
                                    selected = active,
                                    onClick = { onSelect(destination) },
                                    icon = { Symbol(destination.glyph(), size = IconSize.standard, filled = active) },
                                    label = {
                                        Text(
                                            destination.label(s),
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                )
                            }
                        }
                    },
                    floatingActionButton = { if (floatingAction != null) floatingAction() },
                    snackbarHost = { if (snackbarHost != null) snackbarHost() },
                    content = { padding -> content(padding) },
                )
            }
        }
    }
}

/**
 * The create action. Compact windows get the standard square-ish FAB; wider windows get
 * the extended form, which is what the Material 3 guidance recommends when there is room
 * for the label.
 */
@Composable
fun NewServerFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val window = LocalWindowClass.current
    if (window == WindowClass.Compact) {
        FloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Symbol(Symbols.add, size = IconSize.standard)
        }
    } else {
        ExtendedFloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            icon = { Symbol(Symbols.add, size = IconSize.standard) },
            text = { Text(s.newServer, style = MaterialTheme.typography.labelLarge) },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
fun BackIcon(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Symbol(Symbols.arrowBack, size = IconSize.standard)
    }
}

/** A top-bar action with the standard icon size and a label for assistive tech. */
@Composable
fun BarAction(glyph: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Symbol(glyph, size = IconSize.standard, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
