package app.remotehub.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Generated from Google's official Material Symbols Outlined variable font
// (google/material-design-icons, variablefont). Codepoints are read back from
// the subset that ships with the app; the outlines are Google's, not drawn here.

object Symbols {
    const val accountCircle: String = "\ue853"
    const val add: String = "\ue145"
    const val analytics: String = "\uef3e"
    const val apps: String = "\ue5c3"
    const val archive: String = "\ue149"
    const val arrowBack: String = "\ue5c4"
    const val arrowForward: String = "\ue5c8"
    const val article: String = "\uef42"
    const val aspectRatio: String = "\ue85b"
    const val autorenew: String = "\ue028"
    const val backup: String = "\ue864"
    const val bolt: String = "\uea0b"
    const val bugReport: String = "\ue868"
    const val build: String = "\ue869"
    const val cable: String = "\uefe6"
    const val cancel: String = "\ue5c9"
    const val cast: String = "\ue307"
    const val check: String = "\ue5ca"
    const val checkCircle: String = "\ue86c"
    const val chevronRight: String = "\ue409"
    const val close: String = "\ue14c"
    const val closeFullscreen: String = "\uf1cf"
    const val cloudDone: String = "\ue2bf"
    const val cloudDownload: String = "\ue2c0"
    const val cloudSync: String = "\ueb5a"
    const val cloudUpload: String = "\ue2c3"
    const val computer: String = "\ue30a"
    const val contentCopy: String = "\ue14d"
    const val contrast: String = "\ueb37"
    const val copyAll: String = "\ue2ec"
    const val darkMode: String = "\ue51c"
    const val dashboard: String = "\ue871"
    const val delete: String = "\ue872"
    const val description: String = "\ue873"
    const val desktopWindows: String = "\ue30c"
    const val developerBoard: String = "\ue30d"
    const val devices: String = "\ue1b1"
    const val dns: String = "\ue875"
    const val download: String = "\ue171"
    const val edit: String = "\ue150"
    const val encrypted: String = "\ue593"
    const val error: String = "\ue000"
    const val expandMore: String = "\ue5cf"
    const val filterList: String = "\ue152"
    const val fingerprint: String = "\ue90d"
    const val fitScreen: String = "\uea10"
    const val folder: String = "\ue2c7"
    const val fullscreen: String = "\ue5d0"
    const val fullscreenExit: String = "\ue5d1"
    const val gesture: String = "\ue155"
    const val hardDrive: String = "\uf80e"
    const val history: String = "\ue28e"
    const val home: String = "\ue88a"
    const val hourglassEmpty: String = "\ue88b"
    const val hub: String = "\ue9f4"
    const val info: String = "\ue88e"
    const val key: String = "\ue73c"
    const val keyboard: String = "\ue312"
    const val keyboardAlt: String = "\uf028"
    const val lan: String = "\ueb2f"
    const val language: String = "\ue894"
    const val lightMode: String = "\ue518"
    const val link: String = "\ue157"
    const val linkOff: String = "\ue16f"
    const val listAlt: String = "\ue0ee"
    const val lock: String = "\ue88d"
    const val login: String = "\uea77"
    const val logout: String = "\ue9ba"
    const val memory: String = "\ue322"
    const val menu: String = "\ue5d2"
    const val monitor: String = "\uef5b"
    const val moreVert: String = "\ue5d4"
    const val mouse: String = "\ue323"
    const val networkCheck: String = "\ue640"
    const val notifications: String = "\ue7f4"
    const val openInFull: String = "\uf1ce"
    const val openInNew: String = "\ue895"
    const val palette: String = "\ue3b7"
    const val passkey: String = "\uf87f"
    const val password: String = "\uf042"
    const val pauseCircle: String = "\ue035"
    const val pending: String = "\uef64"
    const val person: String = "\ue7fd"
    const val phoneAndroid: String = "\ue324"
    const val playArrow: String = "\ue037"
    const val powerSettingsNew: String = "\ue8ac"
    const val public: String = "\ue80b"
    const val qrCode2: String = "\ue00a"
    const val refresh: String = "\ue5d5"
    const val replay: String = "\ue042"
    const val restartAlt: String = "\uf053"
    const val restore: String = "\ue28e"
    const val rocketLaunch: String = "\ueb9b"
    const val router: String = "\ue328"
    const val save: String = "\ue161"
    const val schedule: String = "\ue192"
    const val search: String = "\ue8b6"
    const val security: String = "\ue32a"
    const val send: String = "\ue163"
    const val settings: String = "\ue8b8"
    const val share: String = "\ue80d"
    const val shield: String = "\ue75b"
    const val shieldLock: String = "\uf686"
    const val signalCellularAlt: String = "\ue202"
    const val smartphone: String = "\ue0d4"
    const val sort: String = "\ue164"
    const val speed: String = "\ue9e4"
    const val stop: String = "\ue047"
    const val stopCircle: String = "\uef71"
    const val storage: String = "\ue1db"
    const val sync: String = "\ue627"
    const val taskAlt: String = "\ue2e6"
    const val terminal: String = "\ueb8e"
    const val timer: String = "\ue425"
    const val token: String = "\uea25"
    const val touchApp: String = "\ue913"
    const val touchpadMouse: String = "\uf687"
    const val translate: String = "\ue8e2"
    const val tune: String = "\ue429"
    const val upload: String = "\ue2c6"
    const val verified: String = "\ue031"
    const val verifiedUser: String = "\ue8e8"
    const val visibility: String = "\ue417"
    const val visibilityOff: String = "\ue8f5"
    const val vpnKey: String = "\ue0da"
    const val vpnLock: String = "\ue62f"
    const val warning: String = "\ue002"
    const val wifi: String = "\ue63e"
    const val wifiTethering: String = "\ue1e2"
    const val work: String = "\ue8f9"
}

/** Outlined at rest, filled when selected — the Material 3 icon state pair. */
val LocalSymbolFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }
val LocalSymbolFontFilled = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

@Composable
fun Symbol(
    glyph: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    filled: Boolean = false,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Text(
            text = glyph,
            style = TextStyle(
                fontFamily = if (filled) LocalSymbolFontFilled.current else LocalSymbolFont.current,
                fontSize = size.value.sp,
                lineHeight = size.value.sp,
                textAlign = TextAlign.Center,
                color = tint,
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * An icon inside an expressive container. The container shape is chosen by the
 * caller so a list of rows reads as a set of distinct marks rather than a column of
 * identical circles; selection morphs the container instead of recolouring it.
 */
@Composable
fun IconBadge(
    glyph: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: Shape = ExpressiveShapes.cookie6,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    filled: Boolean = false,
) {
    Surface(modifier = modifier.size(size), shape = shape, color = container, contentColor = content) {
        Box(contentAlignment = Alignment.Center) {
            Symbol(glyph, size = size * 0.5f, filled = filled)
        }
    }
}
