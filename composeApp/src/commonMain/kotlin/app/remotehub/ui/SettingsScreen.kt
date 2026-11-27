@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.remotehub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.remotehub.design.Panel
import app.remotehub.design.SectionTitle
import app.remotehub.design.Symbol
import app.remotehub.design.Symbols
import app.remotehub.design.TagChip
import app.remotehub.i18n.LocalStrings
import app.remotehub.i18n.availableLocales

@Composable
fun SettingsContent(
    themeMode: Int,
    localeTag: String,
    expiryAlertEnabled: Boolean,
    alertThresholdMinutes: Int,
    biometricEnabled: Boolean,
    storageEncrypted: Boolean,
    appVersion: String,
    onThemeChange: (Int) -> Unit,
    onLocaleChange: (String) -> Unit,
    onExpiryAlertChange: (Boolean) -> Unit,
    onBiometricChange: (Boolean) -> Unit,
    onClearCredentials: () -> Unit,
    onOpenGuide: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Panel {
            SectionTitle(s.appearance)
            Text(s.theme, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(s.themeSystem, s.themeLight, s.themeDark).forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = themeMode == index,
                        onClick = { onThemeChange(index) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                        icon = {
                            Symbol(
                                when (index) {
                                    0 -> Symbols.tune
                                    1 -> Symbols.lightMode
                                    else -> Symbols.darkMode
                                },
                                size = 16.dp,
                            )
                        },
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Text(s.languageLabel, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                availableLocales.forEach { (tag, name) ->
                    val selected = tag == localeTag
                    androidx.compose.material3.Surface(
                        onClick = { onLocaleChange(tag) },
                        shape = MaterialTheme.shapes.medium,
                        color = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
                        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    ) {
                        Text(name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                    }
                }
            }
        }

        Panel {
            SectionTitle(s.notifications)
            ToggleRow(s.expiryAlert, s.alertThreshold, expiryAlertEnabled, onExpiryAlertChange)
            Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TagChip("$alertThresholdMinutes ${s.minutes}")
            }
        }

        Panel {
            SectionTitle(s.security)
            ToggleRow(s.biometricLock, s.storedSecurely, biometricEnabled, onBiometricChange)
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = scheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Symbol(Symbols.lock, size = 20.dp, tint = scheme.primary)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(s.storageEncryption, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                    Text(
                        if (storageEncrypted) "AES-256-GCM · Keystore / DPAPI" else s.notConnected,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedButton(
                onClick = onClearCredentials,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            ) {
                Symbol(Symbols.delete, size = 18.dp, tint = scheme.error)
                Text("  " + s.clearCredentials, style = MaterialTheme.typography.labelLarge, color = scheme.error)
            }
        }

        Panel {
            SectionTitle(s.guides)
            GuideRow(Symbols.rocketLaunch, s.gettingStarted) { onOpenGuide("start") }
            HorizontalDivider(color = scheme.outlineVariant)
            GuideRow(Symbols.bugReport, s.troubleshooting) { onOpenGuide("troubleshoot") }
            HorizontalDivider(color = scheme.outlineVariant)
            GuideRow(Symbols.shield, s.policyAndLimits) { onOpenGuide("policy") }
        }

        Panel {
            SectionTitle(s.about)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text(s.appName, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
                TagChip("${s.version} $appVersion")
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun GuideRow(glyph: String, title: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.material3.Surface(
        onClick = onClick,
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Symbol(glyph, size = 20.dp, tint = scheme.primary)
            Text(title, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface, modifier = Modifier.weight(1f))
            Symbol(Symbols.chevronRight, size = 20.dp, tint = scheme.outline)
        }
    }
}
