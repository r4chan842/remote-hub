@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.remotehub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import app.remotehub.app.LogEntry
import app.remotehub.app.LogLevel
import app.remotehub.design.EmptyState
import app.remotehub.design.NoticeBanner
import app.remotehub.design.Panel
import app.remotehub.design.SectionTitle
import app.remotehub.design.Symbol
import app.remotehub.design.Symbols
import app.remotehub.design.TagChip
import app.remotehub.design.WarningBanner
import app.remotehub.i18n.LocalStrings
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The log screen: one line per event, newest first, with the technical payload one
 * tap away. Everything is selectable and copyable, because the point of this page
 * is to be able to send the exact text of a failure.
 */
@Composable
fun LogsContent(
    entries: List<LogEntry>,
    filter: String,
    query: String,
    expandedSeq: Long?,
    crashNotice: String?,
    environment: String,
    onFilter: (String) -> Unit,
    onQuery: (String) -> Unit,
    onToggleDetail: (Long) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
    onRefresh: () -> Unit,
    onDismissCrash: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme

    val filters = listOf(
        "all" to s.logsAll,
        "error" to s.logsErrors,
        "warn" to s.logsWarnings,
        "network" to s.logsNetwork,
        "github" to s.logsGithub,
        "rdp" to s.logsRdp,
        "system" to s.logsSystem,
    )

    Column(modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
        if (crashNotice != null) {
            WarningBanner(crashNotice, Modifier.padding(bottom = 10.dp).clickable(onClick = onDismissCrash))
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${entries.size} ${s.logsCountLabel}",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRefresh) {
                Symbol(Symbols.refresh, size = 18.dp)
                Text("  " + s.retry, style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = onCopy) {
                Symbol(Symbols.contentCopy, size = 18.dp)
                Text("  " + s.logsCopy, style = MaterialTheme.typography.labelLarge)
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            singleLine = true,
            label = { Text(s.logsSearchHint) },
            leadingIcon = { Symbol(Symbols.search, size = 20.dp) },
        )

        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Two short rows keep every filter reachable on a phone without a
            // horizontal scroll that hides half of them.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    filters.take(4).forEach { (id, label) ->
                        FilterChip(selected = filter == id, onClick = { onFilter(id) }, label = { Text(label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    filters.drop(4).forEach { (id, label) ->
                        FilterChip(selected = filter == id, onClick = { onFilter(id) }, label = { Text(label) })
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            EmptyState(Symbols.terminal, s.logsEmpty, s.logsEmptyHint, Modifier.padding(top = 24.dp))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(entries, key = { it.seq }) { entry ->
                    LogRow(
                        entry = entry,
                        expanded = expandedSeq == entry.seq,
                        onClick = { onToggleDetail(entry.seq) },
                    )
                }
                item {
                    Column(Modifier.padding(top = 12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onShare) {
                                Symbol(Symbols.send, size = 18.dp)
                                Text("  " + s.logsShare, style = MaterialTheme.typography.labelLarge)
                            }
                            TextButton(onClick = onClear) {
                                Symbol(Symbols.delete, size = 18.dp)
                                Text("  " + s.logsClear, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        if (environment.isNotBlank()) {
                            Panel(Modifier.padding(top = 8.dp)) {
                                SectionTitle(s.logsEnvironment)
                                SelectionContainer {
                                    Text(
                                        environment,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            textDirection = TextDirection.Ltr,
                                        ),
                                        color = scheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        NoticeBanner(s.diagnosticsHint)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry, expanded: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tone: Color = when (entry.level) {
        LogLevel.Error -> scheme.error
        LogLevel.Warn -> scheme.tertiary
        LogLevel.Info -> scheme.primary
        LogLevel.Debug -> scheme.onSurfaceVariant
    }
    val time = formatLogTime(entry.timeMillis)

    Panel(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                time,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    textDirection = TextDirection.Ltr,
                ),
                color = scheme.onSurfaceVariant,
            )
            Text("  ", style = MaterialTheme.typography.labelSmall)
            TagChip(entry.level.name, tone = tone.copy(alpha = 0.14f), content = tone, ltr = true)
            Text("  ", style = MaterialTheme.typography.labelSmall)
            Text(
                entry.source,
                style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (entry.detail.isNotBlank()) {
                Symbol(if (expanded) Symbols.expandMore else Symbols.chevronRight, size = 18.dp, tint = scheme.onSurfaceVariant)
            }
        }
        SelectionContainer {
            Text(
                entry.message,
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                color = if (entry.level == LogLevel.Error) scheme.error else scheme.onSurface,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (expanded && entry.detail.isNotBlank()) {
            SelectionContainer {
                Text(
                    entry.detail,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        textDirection = TextDirection.Ltr,
                    ),
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** Local wall-clock time with milliseconds, so two events can be ordered by eye. */
private fun formatLogTime(millis: Long): String {
    val local = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${pad2(local.hour.toLong())}:${pad2(local.minute.toLong())}:${pad2(local.second.toLong())}.${pad3(local.nanosecond / 1_000_000L)}"
}

private fun pad2(value: Long): String = if (value < 10) "0$value" else value.toString()

private fun pad3(value: Long): String = when {
    value < 10 -> "00$value"
    value < 100 -> "0$value"
    else -> value.toString()
}
