@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.remotehub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.remotehub.design.Symbol
import app.remotehub.design.Symbols
import app.remotehub.i18n.AppStrings
import app.remotehub.i18n.LocalStrings

enum class Destination {
    Dashboard,
    Servers,
    Network,
    Settings,
}

fun Destination.glyph(): String = when (this) {
    Destination.Dashboard -> Symbols.dashboard
    Destination.Servers -> Symbols.dns
    Destination.Network -> Symbols.hub
    Destination.Settings -> Symbols.settings
}

fun Destination.label(s: AppStrings): String = when (this) {
    Destination.Dashboard -> s.navDashboard
    Destination.Servers -> s.navServers
    Destination.Network -> s.navNetwork
    Destination.Settings -> s.navSettings
}

@Composable
fun AppShell(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    expanded: Boolean,
    title: String,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    floatingAction: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme

    val bar: @Composable () -> Unit = {
        TopAppBar(
            title = {
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
            },
            navigationIcon = { if (navigationIcon != null) navigationIcon() },
            actions = { if (actions != null) actions() },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = scheme.surface,
                titleContentColor = scheme.onSurface,
            ),
        )
    }

    if (expanded) {
        Row(Modifier.fillMaxSize().background(scheme.surface)) {
            NavigationRail(
                containerColor = scheme.surfaceContainerLow,
                contentColor = scheme.onSurfaceVariant,
                header = {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = scheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.width(44.dp).height(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Symbol(Symbols.rocketLaunch, size = 22.dp, tint = scheme.onPrimaryContainer)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                },
            ) {
                Destination.entries.forEach { destination ->
                    NavigationRailItem(
                        selected = destination == selected,
                        onClick = { onSelect(destination) },
                        icon = { Symbol(destination.glyph(), size = 24.dp) },
                        label = { Text(destination.label(s), style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                containerColor = scheme.surface,
                topBar = bar,
                floatingActionButton = { if (floatingAction != null) floatingAction() },
                content = { padding ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Box(Modifier.widthIn(max = 1040.dp).fillMaxHeight()) {
                            content(PaddingValues(0.dp))
                        }
                    }
                },
            )
        }
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = scheme.surface,
            topBar = bar,
            bottomBar = {
                NavigationBar(containerColor = scheme.surfaceContainer, contentColor = scheme.onSurfaceVariant) {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = destination == selected,
                            onClick = { onSelect(destination) },
                            icon = { Symbol(destination.glyph(), size = 24.dp) },
                            label = { Text(destination.label(s), style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            },
            floatingActionButton = { if (floatingAction != null) floatingAction() },
            content = content,
        )
    }
}

@Composable
fun NewServerFab(onClick: () -> Unit, extended: Boolean = false) {
    val s = LocalStrings.current
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = if (extended) 20.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Symbol(Symbols.add, size = 24.dp)
            if (extended) Text(s.newServer, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun BackIcon(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Symbol(Symbols.arrowBack, size = 22.dp)
    }
}
