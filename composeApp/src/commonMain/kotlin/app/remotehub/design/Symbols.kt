package app.remotehub.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

object Symbols {
    const val accountCircle: String = "\uf20b"
    const val add: String = "\ue145"
    const val analytics: String = "\uef3e"
    const val apps: String = "\ue5c3"
    const val archive: String = "\ue149"
    const val arrowBack: String = "\ue5c4"
    const val autorenew: String = "\ue863"
    const val backup: String = "\ue864"
    const val bolt: String = "\uea0b"
    const val bugReport: String = "\ue868"
    const val build: String = "\uf8cd"
    const val cable: String = "\uefe6"
    const val check: String = "\ue668"
    const val checkCircle: String = "\uf0be"
    const val chevronRight: String = "\ue5cc"
    const val close: String = "\ue5cd"
    const val cloudDownload: String = "\ue2c0"
    const val cloudUpload: String = "\ue2c3"
    const val computer: String = "\ue31e"
    const val contentCopy: String = "\ue14d"
    const val darkMode: String = "\ue51c"
    const val dashboard: String = "\ue871"
    const val delete: String = "\ue92e"
    const val description: String = "\ue873"
    const val desktopWindows: String = "\ue30c"
    const val devices: String = "\ue326"
    const val dns: String = "\ue875"
    const val error: String = "\uf8b6"
    const val expandMore: String = "\ue5cf"
    const val filterList: String = "\ue152"
    const val folder: String = "\ue2c7"
    const val fullscreen: String = "\ue5d0"
    const val fullscreenExit: String = "\ue5d1"
    const val history: String = "\ue8b3"
    const val home: String = "\ue9b2"
    const val hourglassEmpty: String = "\ue88b"
    const val hub: String = "\ue9f4"
    const val info: String = "\ue88e"
    const val key: String = "\ue73c"
    const val keyboard: String = "\ue312"
    const val lan: String = "\ueb2f"
    const val language: String = "\uea07"
    const val lightMode: String = "\ue518"
    const val link: String = "\ue250"
    const val linkOff: String = "\ue16f"
    const val lock: String = "\ue899"
    const val logout: String = "\ue9ba"
    const val memory: String = "\ue322"
    const val menu: String = "\ue5d2"
    const val moreVert: String = "\ue5d4"
    const val mouse: String = "\ue323"
    const val notifications: String = "\ue7f5"
    const val openInNew: String = "\ue89e"
    const val pauseCircle: String = "\ue1a2"
    const val pending: String = "\uef64"
    const val person: String = "\uf0d3"
    const val playArrow: String = "\ue037"
    const val powerSettingsNew: String = "\uf8c7"
    const val public: String = "\ue80b"
    const val refresh: String = "\ue5d5"
    const val replay: String = "\ue042"
    const val rocketLaunch: String = "\ueb9b"
    const val save: String = "\ue161"
    const val schedule: String = "\uefd6"
    const val search: String = "\uef7a"
    const val security: String = "\ue32a"
    const val send: String = "\ue163"
    const val settings: String = "\ue8b8"
    const val shield: String = "\ue9e0"
    const val signalCellularAlt: String = "\ue202"
    const val smartphone: String = "\ue7ba"
    const val sort: String = "\ue164"
    const val speed: String = "\ue9e4"
    const val stop: String = "\ue047"
    const val storage: String = "\ue1db"
    const val sync: String = "\ue627"
    const val taskAlt: String = "\ue2e6"
    const val terminal: String = "\ueb8e"
    const val timer: String = "\ue425"
    const val touchApp: String = "\ue913"
    const val tune: String = "\ue429"
    const val verifiedUser: String = "\uf013"
    const val visibility: String = "\ue8f4"
    const val visibilityOff: String = "\ue8f5"
    const val vpnKey: String = "\ue0da"
    const val warning: String = "\uf083"
    const val wifi: String = "\ue63e"
    const val work: String = "\ue943"
}

@Composable
fun Symbol(
    glyph: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Text(
            text = glyph,
            style = TextStyle(
                fontFamily = LocalSymbolFont.current,
                fontSize = (size.value * 0.92f).sp,
                lineHeight = (size.value * 1.02f).sp,
                textAlign = TextAlign.Center,
                color = tint,
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
}
