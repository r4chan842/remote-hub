package app.remotehub

import app.remotehub.design.Layout
import app.remotehub.design.Motion
import app.remotehub.design.Spacing
import app.remotehub.design.WindowClass
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the design system itself.
 *
 * Half of these are token assertions against the published Material 3 values. The other
 * half read the screen sources and fail if a pattern the redesign removed comes back:
 * a card per section, a decorative shape carrying meaning, a capsule button, or two
 * spaces used as icon padding. Without this, the next feature quietly reintroduces them.
 */
class DesignSystemTest {

    // ------------------------------------------------------------------ tokens

    @Test
    fun spacingIsAFourDpGrid() {
        val scale = listOf(Spacing.xxs, Spacing.xs, Spacing.sm, Spacing.md, Spacing.lg, Spacing.xl, Spacing.xxl, Spacing.xxxl)
        assertEquals(listOf(2, 4, 8, 12, 16, 24, 32, 48), scale.map { it.value.toInt() })
        scale.drop(1).forEach { assertTrue(it.value % 4 == 0f, "$it is off the 4dp grid") }
    }

    @Test
    fun windowBreakpointsMatchThePublishedClasses() {
        assertEquals(WindowClass.Compact, WindowClass.forWidth(0))
        assertEquals(WindowClass.Compact, WindowClass.forWidth(599))
        assertEquals(WindowClass.Medium, WindowClass.forWidth(600))
        assertEquals(WindowClass.Medium, WindowClass.forWidth(839))
        assertEquals(WindowClass.Expanded, WindowClass.forWidth(840))
        assertEquals(WindowClass.Expanded, WindowClass.forWidth(1199))
        assertEquals(WindowClass.Large, WindowClass.forWidth(1200))
        assertEquals(WindowClass.Large, WindowClass.forWidth(1599))
        assertEquals(WindowClass.ExtraLarge, WindowClass.forWidth(1600))
    }

    @Test
    fun railReplacesTheBarFromMediumUp() {
        assertTrue(!WindowClass.Compact.useRail)
        WindowClass.entries.filter { it != WindowClass.Compact }.forEach { assertTrue(it.useRail, "$it") }
        assertTrue(!WindowClass.Medium.useTwoPane)
        assertTrue(WindowClass.Expanded.useTwoPane)
    }

    @Test
    fun screenMarginsFollowTheLayoutGuidance() {
        assertEquals(16.dp, Layout.margin(WindowClass.Compact))
        assertEquals(24.dp, Layout.margin(WindowClass.Medium))
        assertEquals(24.dp, Layout.margin(WindowClass.Expanded))
    }

    @Test
    fun motionDurationsMatchTheSpec() {
        assertEquals(listOf(50, 100, 150, 200), listOf(Motion.short1, Motion.short2, Motion.short3, Motion.short4))
        assertEquals(listOf(250, 300, 350, 400), listOf(Motion.medium1, Motion.medium2, Motion.medium3, Motion.medium4))
        assertEquals(listOf(450, 500), listOf(Motion.long1, Motion.long2))
    }

    @Test
    fun readingMeasureIsBounded() {
        assertTrue(Layout.maxContentWidth <= 840.dp, "long lines on a desktop window hurt readability")
    }

    // ------------------------------------------------------------------ source compliance

    /** Locate the module sources when running from the Gradle module directory. */
    private fun sourceRoot(): File? {
        var dir: File? = File(System.getProperty("user.dir"))
        repeat(6) {
            val candidate = dir?.resolve("composeApp/src/commonMain/kotlin/app/remotehub")
            if (candidate != null && candidate.isDirectory) return candidate
            dir = dir?.parentFile
        }
        return null
    }

    private fun screenSources(): List<File> {
        val root = sourceRoot() ?: return emptyList()
        return root.resolve("ui").listFiles { f -> f.extension == "kt" }?.toList().orEmpty()
    }

    @Test
    fun screensDoNotWrapEverySectionInACard() {
        val files = screenSources()
        if (files.isEmpty()) return
        val offenders = files.filter { it.readText().contains(Regex("""\bPanel\s*\(""")) }
        assertTrue(offenders.isEmpty(), "card-per-section is back in: ${offenders.map { it.name }}")
    }

    @Test
    fun screensDoNotUseDecorativeShapes() {
        val files = screenSources()
        if (files.isEmpty()) return
        val offenders = files.filter { it.readText().contains("ExpressiveShapes") }
        assertTrue(offenders.isEmpty(), "decorative shapes carry no meaning: ${offenders.map { it.name }}")
    }

    @Test
    fun buttonsAreNotFullBleedCapsules() {
        val files = screenSources()
        if (files.isEmpty()) return
        val offenders = files.filter { it.readText().contains("CornerRadius.full") }
        assertTrue(offenders.isEmpty(), "capsule buttons are back in: ${offenders.map { it.name }}")
    }

    @Test
    fun iconSpacingUsesTheButtonSlotNotSpaces() {
        val files = screenSources()
        if (files.isEmpty()) return
        val offenders = files.filter { it.readText().contains("Text(\"  \"") }
        assertTrue(offenders.isEmpty(), "two spaces are not icon padding: ${offenders.map { it.name }}")
    }

    @Test
    fun screensTakeSpacingFromTokens() {
        val files = screenSources()
        if (files.isEmpty()) return
        // Padding/spacing arguments must be tokens. Stroke widths, borders and the
        // occasional 1dp hairline are exempt, so only layout calls are inspected.
        val layoutCall = Regex("""(?:padding|spacedBy)\([^)]*?\b(\d+)\.dp""")
        val allowed = setOf("0", "1", "2")
        val offenders = mutableListOf<String>()
        files.forEach { file ->
            layoutCall.findAll(file.readText()).forEach { match ->
                val value = match.groupValues[1]
                if (value !in allowed) offenders += "${file.name}: ${match.value}"
            }
        }
        assertTrue(offenders.isEmpty(), "hardcoded layout spacing:\n${offenders.joinToString("\n")}")
    }

    @Test
    fun listRowsSitOnTheScreenGrid() {
        val files = screenSources()
        if (files.isEmpty()) return
        val root = sourceRoot() ?: return
        val all = files + listOf(root.resolve("design/Components.kt"))
        val offenders = mutableListOf<String>()
        all.forEach { file ->
            val text = file.readText()
            val rows = text.split("ListItem(").size - 1
            val flush = text.split("flushListRow()").size - 1
            // Every ListItem must cancel the component's own 16dp inset, otherwise rows
            // indent past their section header and past the fields beside them.
            if (rows > flush) offenders += "${file.name}: $rows rows, $flush aligned"
        }
        assertTrue(offenders.isEmpty(), "ListItem rows off the grid: ${offenders.joinToString()}")
    }

    @Test
    fun everyScreenUsesTheSharedScreenContainer() {
        val files = screenSources()
        if (files.isEmpty()) return
        // Remote and Logs own their scroll behaviour; the rest must use ScreenContent.
        val exempt = setOf("RemoteScreen.kt", "LogsScreen.kt", "Shell.kt", "ServersScreen.kt")
        val offenders = files.filter { it.name !in exempt && !it.readText().contains("ScreenContent(") }
        assertTrue(offenders.isEmpty(), "screens bypassing the shared container: ${offenders.map { it.name }}")
    }
}
