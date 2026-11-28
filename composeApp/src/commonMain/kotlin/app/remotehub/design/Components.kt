@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.remotehub.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.remotehub.domain.ServerState
import app.remotehub.domain.StepStatus
import app.remotehub.i18n.AppStrings
import app.remotehub.i18n.LocalStrings

/**
 * Cancels ListItem's built-in 16 dp horizontal inset.
 *
 * ListItem pads its own content, and ScreenContent already applies the window margin.
 * Stacking the two indented every row 16 dp past its section header and past the text
 * fields beside it, which is visible as a broken left edge on every screen. This measures
 * the row wider and places it back by the inset, so the content lands on the screen grid
 * while the component keeps its own internal layout. placeRelative mirrors it for RTL.
 */
fun Modifier.flushListRow(): Modifier = this.layout { measurable, constraints ->
    val inset = Spacing.lg.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth + inset * 2),
    )
    layout(constraints.maxWidth, placeable.height) { placeable.placeRelative(-inset, 0) }
}

// --------------------------------------------------------------------------- state colours

fun ServerState.label(s: AppStrings): String = when (this) {
    ServerState.Provisioning -> s.stateProvisioning
    ServerState.Ready -> s.stateReady
    ServerState.Connected -> s.stateConnected
    ServerState.Expiring -> s.stateExpiring
    ServerState.Stopping -> s.stateStopping
    ServerState.Stopped -> s.stateStopped
    ServerState.Failed -> s.stateFailed
}

@Composable
fun stateColors(state: ServerState): Pair<Color, Color> {
    val status = LocalStatusColors.current
    val scheme = MaterialTheme.colorScheme
    return when (state) {
        ServerState.Ready -> status.successContainer to status.onSuccessContainer
        ServerState.Connected -> scheme.primaryContainer to scheme.onPrimaryContainer
        ServerState.Provisioning -> scheme.secondaryContainer to scheme.onSecondaryContainer
        ServerState.Expiring -> status.warningContainer to status.onWarningContainer
        ServerState.Failed -> scheme.errorContainer to scheme.onErrorContainer
        ServerState.Stopping -> status.warningContainer to status.onWarningContainer
        ServerState.Stopped -> status.neutralContainer to status.onNeutralContainer
    }
}

/**
 * Status uses the Material 3 Badge component rather than a hand-drawn Surface pill.
 * The dot was removed: colour plus the written state already carries the meaning, and
 * a decorative dot added a third redundant channel.
 */
@Composable
fun StatusBadge(state: ServerState, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val (bg, fg) = stateColors(state)
    Badge(
        modifier = modifier.heightIn(min = 24.dp),
        containerColor = bg,
        contentColor = fg,
    ) {
        Text(
            state.label(s),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            maxLines = 1,
        )
    }
}

// --------------------------------------------------------------------------- layout

/**
 * The standard scrolling screen: one column, the window's margin, a bounded reading
 * measure, and a consistent section rhythm. Screens never set their own padding.
 */
@Composable
fun ScreenContent(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val margin = screenMargin
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = Layout.maxContentWidth)
                .fillMaxWidth()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = margin)
                .padding(top = Spacing.sm, bottom = Spacing.scrollEnd),
            verticalArrangement = Arrangement.spacedBy(Spacing.section),
            content = content,
        )
    }
}

/**
 * A group of related rows. Deliberately not a Card: Material 3 groups list content with
 * a subheader and a divider, and the previous design wrapped every group in its own
 * rounded container, which is what made the screens look like a web dashboard.
 */
@Composable
fun Section(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trailing != null) trailing()
        }
        Column(content = content)
    }
}

@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.outlineVariant)
}

/** Forces an address or code to read left-to-right even inside an RTL layout. */
@Composable
fun ltrStyle() = MaterialTheme.typography.bodyMedium.copy(
    textDirection = TextDirection.Ltr,
    textAlign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Start,
)

// --------------------------------------------------------------------------- rows

/**
 * A read-only fact about a server, on the Material 3 ListItem. Label above value rather
 * than a fixed-width label column, so a long Persian label can never collide with the
 * value or truncate it.
 */
@Composable
fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    onCopy: (() -> Unit)? = null,
) {
    val s = LocalStrings.current
    ListItem(
        modifier = modifier.fillMaxWidth().flushListRow(),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Text(
                value.ifBlank { "—" },
                style = if (mono) ltrStyle() else MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        overlineContent = {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = if (onCopy != null) {
            {
                IconButton(onClick = onCopy) {
                    Symbol(
                        Symbols.contentCopy,
                        size = IconSize.medium,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = "${s.copy} $label" },
                    )
                }
            }
        } else null,
    )
}

/** A setting whose control is a switch, on ListItem so the whole row is the target. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    control: @Composable () -> Unit,
) {
    ListItem(
        modifier = modifier.fillMaxWidth().flushListRow().clickable(enabled = enabled) { onCheckedChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = subtitle?.let {
            { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        trailingContent = { control() },
    )
}

/** A row that navigates somewhere. Chevron flips with layout direction automatically. */
@Composable
fun NavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: String? = null,
    subtitle: String? = null,
    trailingText: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    ListItem(
        modifier = modifier.fillMaxWidth().flushListRow().clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = glyph?.let {
            { Symbol(it, size = IconSize.standard, tint = scheme.onSurfaceVariant) }
        },
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = subtitle?.let {
            { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (trailingText != null) {
                    Text(trailingText, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                Symbol(Symbols.chevronRight, size = IconSize.medium, tint = scheme.outline)
            }
        },
    )
}

// --------------------------------------------------------------------------- buttons

/**
 * Icon + label with the spacing the Material 3 button spec defines. The old screens
 * concatenated two spaces onto the label ("  " + text), which broke alignment and
 * centred the pair incorrectly.
 */
@Composable
fun ButtonContent(glyph: String?, text: String, loading: Boolean = false) {
    if (loading) {
        CircularProgressIndicator(
            Modifier.size(IconSize.compact),
            strokeWidth = 2.dp,
            color = androidx.compose.material3.LocalContentColor.current,
        )
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    } else if (glyph != null) {
        Symbol(glyph, size = IconSize.compact)
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    }
    Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** The one primary action on a screen. Filled, standard height, never a giant capsule. */
@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: String? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinTouchTarget),
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.small,
        contentPadding = ButtonDefaults.ContentPadding,
    ) { ButtonContent(glyph, text, loading) }
}

/** A secondary action of real importance: Filled Tonal, per the M3 hierarchy. */
@Composable
fun TonalAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: String? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    androidx.compose.material3.FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinTouchTarget),
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.small,
        contentPadding = ButtonDefaults.ContentPadding,
    ) { ButtonContent(glyph, text, loading) }
}

/** A lower-emphasis action: Outlined. */
@Composable
fun OutlinedAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: String? = null,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinTouchTarget),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        contentPadding = ButtonDefaults.ContentPadding,
    ) { ButtonContent(glyph, text) }
}

/** A destructive action. Outlined with error content, and always behind a confirmation. */
@Composable
fun DestructiveAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: String? = null,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinTouchTarget),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        contentPadding = ButtonDefaults.ContentPadding,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.error),
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.error.copy(alpha = 0.5f)),
    ) { ButtonContent(glyph, text) }
}

@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: String? = null,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = ButtonDefaults.TextButtonContentPadding,
    ) { ButtonContent(glyph, text) }
}

/** Confirmation for anything that destroys or interrupts. Material 3 AlertDialog. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    glyph: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = glyph?.let { { Symbol(it, size = IconSize.standard) } },
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (destructive) scheme.error else scheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel, style = MaterialTheme.typography.labelLarge) } },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = scheme.surfaceContainerHigh,
    )
}

// --------------------------------------------------------------------------- page states

/** Empty is a destination, not a blank page: it explains and offers the next step. */
@Composable
fun EmptyState(
    glyph: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().padding(vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Symbol(glyph, size = IconSize.display, tint = scheme.outline)
        Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.xs))
            PrimaryAction(actionLabel, onAction, glyph = Symbols.add)
        }
    }
}

@Composable
fun LoadingState(label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        CircularProgressIndicator(Modifier.size(IconSize.large), strokeWidth = 3.dp)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
    detailsLabel: String? = null,
    onDetails: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().padding(vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Symbol(Symbols.error, size = IconSize.large, tint = scheme.error)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (retryLabel != null && onRetry != null) TonalAction(retryLabel, onRetry, glyph = Symbols.refresh)
            if (detailsLabel != null && onDetails != null) TextAction(detailsLabel, onDetails, glyph = Symbols.terminal)
        }
    }
}

enum class MessageTone { Info, Warning, Error }

/**
 * A persistent inline message. Material 3 has no banner component, so this is built from
 * the Surface primitive with the matching container role — one container, no nesting.
 */
@Composable
fun InlineMessage(
    text: String,
    modifier: Modifier = Modifier,
    tone: MessageTone = MessageTone.Info,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val status = LocalStatusColors.current
    val (bg, fg, glyph) = when (tone) {
        MessageTone.Info -> Triple(scheme.surfaceContainerHigh, scheme.onSurfaceVariant, Symbols.info)
        MessageTone.Warning -> Triple(status.warningContainer, status.onWarningContainer, Symbols.warning)
        MessageTone.Error -> Triple(scheme.errorContainer, scheme.onErrorContainer, Symbols.error)
    }
    Surface(
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = bg,
        contentColor = fg,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Symbol(glyph, size = IconSize.medium, tint = if (tone == MessageTone.Info) scheme.primary else fg)
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        }
    }
}

// --------------------------------------------------------------------------- progress

@Composable
fun TimeMeter(
    fraction: Float,
    remaining: String,
    caption: String,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
) {
    val status = LocalStatusColors.current
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(caption, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(
                remaining,
                style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Ltr),
                color = if (warning) status.warning else scheme.onSurface,
            )
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(Spacing.sm),
            color = if (warning) status.warning else scheme.primary,
            trackColor = scheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
        )
    }
}

/** One stage of the creation workflow. Not a Material component; built from tokens. */
@Composable
fun StepRow(
    title: String,
    status: StepStatus,
    detail: String?,
    elapsed: String?,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val scheme = MaterialTheme.colorScheme
    val statusColors = LocalStatusColors.current
    ListItem(
        modifier = modifier.fillMaxWidth().flushListRow(),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(Modifier.size(IconSize.standard), contentAlignment = Alignment.Center) {
                when (status) {
                    StepStatus.Running -> CircularProgressIndicator(
                        Modifier.size(IconSize.medium),
                        strokeWidth = 2.dp,
                        color = scheme.primary,
                        trackColor = scheme.surfaceContainerHighest,
                    )
                    StepStatus.Done -> Symbol(Symbols.checkCircle, size = IconSize.medium, tint = statusColors.success, filled = true)
                    StepStatus.Failed -> Symbol(Symbols.error, size = IconSize.medium, tint = scheme.error, filled = true)
                    StepStatus.Skipped -> Symbol(Symbols.remove, size = IconSize.medium, tint = scheme.outline)
                    StepStatus.Pending -> Symbol(Symbols.radioButtonUnchecked, size = IconSize.medium, tint = scheme.outlineVariant)
                }
            }
        },
        headlineContent = {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (status == StepStatus.Pending) scheme.onSurfaceVariant else scheme.onSurface,
            )
        },
        supportingContent = detail?.let {
            {
                val latinOnly = detail.none { c -> c.code in 0x0590..0x08FF }
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall.copy(
                        textDirection = if (latinOnly) TextDirection.Ltr else TextDirection.Content,
                        textAlign = if (latinOnly && LocalLayoutDirection.current == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Start,
                    ),
                    color = scheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            val trailing = elapsed ?: when (status) {
                StepStatus.Running -> s.running
                StepStatus.Pending -> s.pending
                StepStatus.Skipped -> "—"
                else -> null
            }
            if (trailing != null) {
                Text(trailing, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        },
    )
}

/** Reveals a long explanation on demand instead of spending permanent screen height. */
@Composable
fun ExpandableHelp(
    label: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        TextAction(
            text = label,
            onClick = onToggle,
            glyph = if (expanded) Symbols.expandLess else Symbols.expandMore,
        )
        AnimatedVisibility(visible = expanded, enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()) {
            Column(
                Modifier.fillMaxWidth().padding(start = Spacing.lg, top = Spacing.xs, bottom = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                content = content,
            )
        }
    }
}
