@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.remotehub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.remotehub.design.InlineMessage
import app.remotehub.design.MessageTone
import app.remotehub.design.NavigationRow
import app.remotehub.design.RowDivider
import app.remotehub.design.ScreenContent
import app.remotehub.design.Section
import app.remotehub.design.Spacing
import app.remotehub.design.SwitchRow
import app.remotehub.design.Symbols
import app.remotehub.i18n.LocalStrings
import app.remotehub.i18n.availableLocales

/**
 * Settings, grouped by what the setting affects.
 *
 * Every group used to be a Card, and the alert threshold was three text buttons whose
 * selected state was a literal "●" character. Groups are now subheader + rows, and the
 * threshold is a real single-select chip row, so selection is conveyed by the component
 * rather than by punctuation.
 */
@Composable
fun SettingsContent(
    themeMode: Int,
    localeTag: String,
    expiryAlertEnabled: Boolean,
    warningMinutes: Int,
    onTheme: (Int) -> Unit,
    onLocale: (String) -> Unit,
    onExpiryAlert: (Boolean) -> Unit,
    onWarningMinutes: (Int) -> Unit,
    onGuide: (String) -> Unit,
    onLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme

    ScreenContent(modifier) {
        Section(title = s.appearance) {
            val options = listOf(s.themeSystem, s.themeLight, s.themeDark)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = themeMode == index,
                        onClick = { onTheme(index) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        icon = {},
                        label = { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                    )
                }
            }
        }

        Section(title = s.language) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                availableLocales.forEachIndexed { index, locale ->
                    SegmentedButton(
                        selected = localeTag == locale.first,
                        onClick = { onLocale(locale.first) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = availableLocales.size),
                        icon = {},
                        label = { Text(locale.second, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                    )
                }
            }
        }

        RowDivider()

        Section(title = s.notificationsTitle) {
            SwitchRow(
                title = s.expiryAlert,
                subtitle = s.expiryAlertHint,
                checked = expiryAlertEnabled,
                onCheckedChange = onExpiryAlert,
                control = { Switch(checked = expiryAlertEnabled, onCheckedChange = onExpiryAlert) },
            )
            Column(Modifier.padding(top = Spacing.sm)) {
                Text(
                    s.alertThreshold,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf(10, 15, 30).forEach { minutes ->
                        FilterChip(
                            selected = warningMinutes == minutes,
                            onClick = { onWarningMinutes(minutes) },
                            enabled = expiryAlertEnabled,
                            label = { Text("$minutes ${s.minutes}", style = MaterialTheme.typography.labelLarge) },
                            leadingIcon = if (warningMinutes == minutes) {
                                { app.remotehub.design.Symbol(Symbols.check, size = app.remotehub.design.IconSize.compact) }
                            } else null,
                        )
                    }
                }
            }
        }

        RowDivider()

        Section(title = s.diagnosticsSection, subtitle = s.diagnosticsHint) {
            NavigationRow(title = s.logsTitle, glyph = Symbols.terminal, onClick = onLogs)
        }

        Section(title = s.guides) {
            NavigationRow(s.guideGithub, { onGuide("github") }, glyph = Symbols.info)
            NavigationRow(s.guideTailscale, { onGuide("tailscale") }, glyph = Symbols.info)
            NavigationRow(s.guideRdp, { onGuide("rdp") }, glyph = Symbols.info)
            NavigationRow(s.guideSecurity, { onGuide("security") }, glyph = Symbols.shield)
        }

        InlineMessage(s.privacyNote, tone = MessageTone.Info)
    }
}

/** A guide is prose. One section, numbered lines, no card per paragraph. */
@Composable
fun GuideContent(guideId: String, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val (title, steps) = when (guideId) {
        "github" -> s.guideGithub to listOf(s.githubStep1, s.githubStep2, s.githubStep3, s.scopesNote)
        "tailscale" -> s.guideTailscale to listOf(s.tailscaleStep1, s.tailscaleStep2, s.tailscaleStep3, s.networkNote)
        "rdp" -> s.guideRdp to listOf(s.rdpGuide1, s.rdpGuide2, s.rdpGuide3)
        else -> s.guideSecurity to listOf(s.security1, s.security2, s.security3, s.security4)
    }
    ScreenContent(modifier) {
        Section(title = title) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                steps.forEachIndexed { index, text -> NumberedStep(index + 1, text) }
            }
        }
        if (guideId == "security") InlineMessage(s.riskNote, tone = MessageTone.Warning)
    }
}
