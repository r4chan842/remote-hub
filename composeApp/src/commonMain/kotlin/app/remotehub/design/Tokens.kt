package app.remotehub.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * The single source of layout truth for every screen.
 *
 * Screens used to invent their own padding (8/10/12/14/16/18/24 dp all appeared as
 * literal values), their own corner radii and their own icon sizes, which is why the
 * app read as a set of unrelated pages. Nothing outside this file may hardcode a
 * spacing, elevation, duration or icon size; `DesignSystemTest` enforces that.
 */
object Spacing {
    val none: Dp = 0.dp
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 48.dp

    /** Vertical rhythm between two sections on the same screen. */
    val section: Dp = 24.dp

    /** Vertical rhythm between rows inside one section. */
    val row: Dp = 4.dp

    /** Bottom scroll allowance so the last row clears the navigation bar. */
    val scrollEnd: Dp = 24.dp
}

/**
 * Material 3 elevation levels.
 * https://m3.material.io/styles/elevation/tokens
 */
object Elevation {
    val level0: Dp = 0.dp
    val level1: Dp = 1.dp
    val level2: Dp = 3.dp
    val level3: Dp = 6.dp
    val level4: Dp = 8.dp
    val level5: Dp = 12.dp
}

/**
 * Icon sizes. Material Symbols are drawn on a 24 dp grid; the smaller steps are for
 * dense supporting text and button leading icons only.
 */
object IconSize {
    val small: Dp = 16.dp
    val compact: Dp = 18.dp
    val medium: Dp = 20.dp
    val standard: Dp = 24.dp
    val large: Dp = 40.dp
    val display: Dp = 56.dp
}

/** Minimum touch target, per the accessibility guidance. */
val MinTouchTarget: Dp = 48.dp

/**
 * Material 3 motion tokens, transcribed from the official spec rather than guessed.
 * https://m3.material.io/styles/motion/easing-and-duration/tokens-specs
 */
object Motion {
    const val short1 = 50
    const val short2 = 100
    const val short3 = 150
    const val short4 = 200
    const val medium1 = 250
    const val medium2 = 300
    const val medium3 = 350
    const val medium4 = 400
    const val long1 = 450
    const val long2 = 500

    val standard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val standardDecelerate: Easing = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)
    val standardAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)
    val emphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val emphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    /** Content that enters the screen. */
    fun <T> enter(durationMillis: Int = medium4): FiniteAnimationSpec<T> =
        tween(durationMillis, easing = emphasizedDecelerate)

    /** Content that leaves the screen. */
    fun <T> exit(durationMillis: Int = short4): FiniteAnimationSpec<T> =
        tween(durationMillis, easing = emphasizedAccelerate)

    /** A value that changes in place: colour, size, progress. */
    fun <T> change(durationMillis: Int = short4): FiniteAnimationSpec<T> =
        tween(durationMillis, easing = standard)
}

/**
 * Width window size classes, with the official breakpoints.
 * https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes
 */
enum class WindowClass { Compact, Medium, Expanded, Large, ExtraLarge;

    /** A navigation rail replaces the bar from Medium up. */
    val useRail: Boolean get() = this != Compact

    /** Two-pane detail layouts need Expanded width. */
    val useTwoPane: Boolean get() = ordinal >= Expanded.ordinal

    companion object {
        fun forWidth(widthDp: Int): WindowClass = when {
            widthDp < 600 -> Compact
            widthDp < 840 -> Medium
            widthDp < 1200 -> Expanded
            widthDp < 1600 -> Large
            else -> ExtraLarge
        }
    }
}

val LocalWindowClass = staticCompositionLocalOf { WindowClass.Compact }

/**
 * Layout values that depend on the window, resolved once per composition instead of
 * being re-derived by each screen.
 */
object Layout {
    /** Horizontal screen margin: 16 dp compact, 24 dp from Medium up, per M3 layout. */
    fun margin(window: WindowClass): Dp = if (window == WindowClass.Compact) Spacing.lg else Spacing.xl

    /**
     * Reading measure. Text and forms stop growing past this so a desktop window does
     * not produce 1800 dp long lines.
     */
    val maxContentWidth: Dp = 720.dp

    /** A list pane beside a detail pane. */
    val listPaneWidth: Dp = 360.dp

    /** Gap between two panes. */
    val paneGap: Dp = Spacing.lg
}

/** Screen margin for the current window. */
val screenMargin: Dp
    @Composable @ReadOnlyComposable get() = Layout.margin(LocalWindowClass.current)

/** Standard horizontal content padding for a scrolling screen. */
@Composable
fun contentWidthModifier(): Dp = Layout.maxContentWidth

/** Line height helper kept here so no screen invents a TextUnit. */
object TextTokens {
    val tight: TextUnit = TextUnit.Unspecified
}
