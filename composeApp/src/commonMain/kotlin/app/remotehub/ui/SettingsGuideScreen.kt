@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.remotehub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.remotehub.design.NoticeBanner
import app.remotehub.design.Panel
import app.remotehub.design.SectionTitle
import app.remotehub.design.Symbol
import app.remotehub.design.Symbols
import app.remotehub.design.WarningBanner
import app.remotehub.i18n.LocalStrings
import app.remotehub.i18n.availableLocales

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
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Panel {
            SectionTitle(s.appearance)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(s.themeSystem, s.themeLight, s.themeDark).forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = themeMode == index,
                        onClick = { onTheme(index) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                    ) { Text(label, style = MaterialTheme.typography.labelMedium) }
                }
            }
        }

        Panel {
            SectionTitle(s.language)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                availableLocales.forEachIndexed { index, locale ->
                    SegmentedButton(
                        selected = localeTag == locale.first,
                        onClick = { onLocale(locale.first) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = availableLocales.size),
                    ) { Text(locale.second, style = MaterialTheme.typography.labelMedium) }
                }
            }
        }

        Panel {
            SectionTitle(s.notificationsTitle)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.expiryAlert, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
                    Text(s.expiryAlertHint, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                Switch(checked = expiryAlertEnabled, onCheckedChange = onExpiryAlert)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                listOf(10, 15, 30).forEach { minutes ->
                    TextButton(onClick = { onWarningMinutes(minutes) }) {
                        Text(
                            if (warningMinutes == minutes) "● $minutes" else "$minutes",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (warningMinutes == minutes) scheme.primary else scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Panel {
            SectionTitle(s.guides)
            listOf("github" to s.guideGithub, "tailscale" to s.guideTailscale, "rdp" to s.guideRdp, "security" to s.guideSecurity)
                .forEach { (id, title) ->
                    TextButton(onClick = { onGuide(id) }, modifier = Modifier.fillMaxWidth()) {
                        Symbol(Symbols.info, size = 18.dp)
                        Text("  $title", style = MaterialTheme.typography.bodyMedium)
                    }
                }
        }

        NoticeBanner(s.privacyNote)
    }
}

@Composable
fun GuideContent(guideId: String, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    val (title, steps) = when (guideId) {
        "github" -> s.guideGithub to listOf(s.githubStep1, s.githubStep2, s.githubStep3, s.scopesNote)
        "tailscale" -> s.guideTailscale to listOf(s.tailscaleStep1, s.tailscaleStep2, s.tailscaleStep3, s.networkNote)
        "rdp" -> s.guideRdp to listOf(s.rdpGuide1, s.rdpGuide2, s.rdpGuide3)
        else -> s.guideSecurity to listOf(s.security1, s.security2, s.security3, s.security4)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
        steps.forEachIndexed { index, text ->
            Panel { NumberedStep(index + 1, text) }
        }
        if (guideId == "security") WarningBanner(s.riskNote)
    }
}
