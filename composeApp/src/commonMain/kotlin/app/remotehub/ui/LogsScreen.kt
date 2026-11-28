@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.remotehub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
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
import app.remotehub.design.InlineMessage
import app.remotehub.design.MessageTone
import app.remotehub.design.Section
import app.remotehub.design.Spacing
import app.remotehub.design.TextAction
import app.remotehub.design.Symbol
import app.remotehub.design.Symbols
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

    Column(modifier.fillMaxSize().padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.md)) {
        if (crashNotice != null) {
            InlineMessage(crashNotice, Modifier.padding(bottom = Spacing.md), tone = MessageTone.Error, onClick = onDismissCrash)
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${entries.size} ${s.logsCountLabel}",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextAction(s.refresh, onRefresh, glyph = Symbols.refresh)
            TextAction(s.logsCopy, onCopy, glyph = Symbols.contentCopy)
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
            singleLine = true,
            label = { Text(s.logsSearchHint) },
            leadingIcon = { Symbol(Symbols.search, size = 20.dp) },
        )

        Row(
            Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // Two short rows keep every filter reachable on a phone without a
            // horizontal scroll that hides half of them.
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    filters.take(4).forEach { (id, label) ->
                        FilterChip(selected = filter == id, onClick = { onFilter(id) }, label = { Text(label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    filters.drop(4).forEach { (id, label) ->
                        FilterChip(selected = filter == id, onClick = { onFilter(id) }, label = { Text(label) })
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            EmptyState(Symbols.terminal, s.logsEmpty, s.logsEmptyHint, Modifier.padding(top = Spacing.xl))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(entries, key = { it.seq }) { entry ->
                    LogRow(
                        entry = entry,
                        expanded = expandedSeq == entry.seq,
                        onClick = { onToggleDetail(entry.seq) },
                    )
                }
                item {
                    Column(Modifier.padding(top = Spacing.md)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            TextAction(s.logsShare, onShare, glyph = Symbols.send)
                            TextAction(s.logsClear, onClear, glyph = Symbols.delete)
                        }
                        if (environment.isNotBlank()) {
                            Section(s.logsEnvironment, Modifier.padding(top = Spacing.sm)) {
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
                        InlineMessage(s.diagnosticsHint)
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

    LogEntrySurface(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                time,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    textDirection = TextDirection.Ltr,
                ),
                color = scheme.onSurfaceVariant,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.width(Spacing.sm))
            androidx.compose.material3.Badge(containerColor = tone.copy(alpha = 0.16f), contentColor = tone) {
                Text(entry.level.name, style = MaterialTheme.typography.labelSmall)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.width(Spacing.sm))
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
                modifier = Modifier.padding(top = Spacing.sm),
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
                    modifier = Modifier.padding(top = Spacing.sm),
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

/** One log record. A single flat container with the standard small shape. */
@Composable
private fun LogEntrySurface(onClick: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        androidx.compose.foundation.layout.Column(
            Modifier.padding(app.remotehub.design.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(app.remotehub.design.Spacing.xs),
        ) { content() }
    }
}
