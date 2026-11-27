package app.remotehub.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.remotehub.resources.Res
import app.remotehub.resources.material_symbols
import app.remotehub.resources.vazirmatn_bold
import app.remotehub.resources.vazirmatn_medium
import app.remotehub.resources.vazirmatn_regular
import app.remotehub.resources.vazirmatn_semibold
import org.jetbrains.compose.resources.Font

data class StatusColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val neutralContainer: Color,
    val onNeutralContainer: Color,
) {
    companion object {
        val light = StatusColors(
            success = Color(0xFF1B6E3C),
            onSuccess = Color(0xFFFFFFFF),
            successContainer = Color(0xFFC8EED2),
            onSuccessContainer = Color(0xFF062915),
            warning = Color(0xFF8A5300),
            warningContainer = Color(0xFFFFE2B8),
            onWarningContainer = Color(0xFF2B1700),
            neutralContainer = Color(0xFFE4E2E9),
            onNeutralContainer = Color(0xFF44474F),
        )
        val dark = StatusColors(
            success = Color(0xFF7BDC9B),
            onSuccess = Color(0xFF00391B),
            successContainer = Color(0xFF14522C),
            onSuccessContainer = Color(0xFFC8EED2),
            warning = Color(0xFFFFB95C),
            warningContainer = Color(0xFF5C3A00),
            onWarningContainer = Color(0xFFFFE2B8),
            neutralContainer = Color(0xFF33353A),
            onNeutralContainer = Color(0xFFC5C6D0),
        )
    }
}

val LocalSymbolFont = staticCompositionLocalOf { FontFamily.Default }
val LocalStatusColors = staticCompositionLocalOf { StatusColors.light }

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2E5AAC),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001945),
    inversePrimary = Color(0xFFADC6FF),
    secondary = Color(0xFF575E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBE2F9),
    onSecondaryContainer = Color(0xFF141B2C),
    tertiary = Color(0xFF006A6A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF9CF1F0),
    onTertiaryContainer = Color(0xFF002020),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAF9FD),
    onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFFAF9FD),
    onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    surfaceTint = Color(0xFF2E5AAC),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F7),
    outline = Color(0xFF757780),
    outlineVariant = Color(0xFFC5C6D0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAF9FD),
    surfaceDim = Color(0xFFDAD9E0),
    surfaceContainer = Color(0xFFEEEDF3),
    surfaceContainerHigh = Color(0xFFE8E7EE),
    surfaceContainerHighest = Color(0xFFE2E2E9),
    surfaceContainerLow = Color(0xFFF4F3F8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF004494),
    onPrimaryContainer = Color(0xFFD8E2FF),
    inversePrimary = Color(0xFF2E5AAC),
    secondary = Color(0xFFBFC6DC),
    onSecondary = Color(0xFF293041),
    secondaryContainer = Color(0xFF3F4759),
    onSecondaryContainer = Color(0xFFDBE2F9),
    tertiary = Color(0xFF80D5D4),
    onTertiary = Color(0xFF003737),
    tertiaryContainer = Color(0xFF004F50),
    onTertiaryContainer = Color(0xFF9CF1F0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    surfaceTint = Color(0xFFADC6FF),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2F3036),
    outline = Color(0xFF8F9099),
    outlineVariant = Color(0xFF44474F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF38393F),
    surfaceDim = Color(0xFF121318),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    surfaceContainerLow = Color(0xFF1A1B20),
    surfaceContainerLowest = Color(0xFF0C0E13),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
private fun appTypography(): Typography {
    val family = FontFamily(
        Font(Res.font.vazirmatn_regular, FontWeight.Normal),
        Font(Res.font.vazirmatn_medium, FontWeight.Medium),
        Font(Res.font.vazirmatn_semibold, FontWeight.SemiBold),
        Font(Res.font.vazirmatn_bold, FontWeight.Bold),
    )
    fun style(size: Int, height: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = height.sp,
        letterSpacing = tracking.sp,
    )
    return Typography(
        displaySmall = style(34, 42, FontWeight.Normal),
        headlineMedium = style(27, 34, FontWeight.SemiBold),
        headlineSmall = style(23, 30, FontWeight.SemiBold),
        titleLarge = style(21, 28, FontWeight.Medium),
        titleMedium = style(16, 24, FontWeight.Medium, 0.15),
        titleSmall = style(14, 20, FontWeight.Medium, 0.1),
        bodyLarge = style(16, 24, FontWeight.Normal, 0.5),
        bodyMedium = style(14, 21, FontWeight.Normal, 0.25),
        bodySmall = style(12, 17, FontWeight.Normal, 0.4),
        labelLarge = style(14, 20, FontWeight.Medium, 0.1),
        labelMedium = style(12, 16, FontWeight.Medium, 0.5),
        labelSmall = style(11, 16, FontWeight.Medium, 0.5),
    )
}

@Composable
fun RemoteHubTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val symbolFont = FontFamily(Font(Res.font.material_symbols))
    CompositionLocalProvider(
        LocalSymbolFont provides symbolFont,
        LocalStatusColors provides if (darkTheme) StatusColors.dark else StatusColors.light,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = appTypography(),
            shapes = AppShapes,
            content = content,
        )
    }
}
