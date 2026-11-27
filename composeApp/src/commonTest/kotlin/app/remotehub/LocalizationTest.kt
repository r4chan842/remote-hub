package app.remotehub

import app.remotehub.i18n.Arabic
import app.remotehub.i18n.English
import app.remotehub.i18n.Persian
import app.remotehub.i18n.availableLocales
import app.remotehub.i18n.stringsFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalizationTest {
    private val locales = listOf(Persian, English, Arabic)

    @Test
    fun everyRegisteredLocaleResolves() {
        assertEquals(3, availableLocales.size)
        availableLocales.forEach { (tag, _) -> assertEquals(tag, stringsFor(tag).tag) }
    }

    @Test
    fun unknownLocaleFallsBackToEnglish() {
        assertEquals("en", stringsFor("zz").tag)
    }

    @Test
    fun layoutDirectionMatchesScript() {
        assertTrue(Persian.rtl)
        assertTrue(Arabic.rtl)
        assertFalse(English.rtl)
    }

    @Test
    fun noLocaleShipsBlankCopy() {
        locales.forEach { s ->
            val values = listOf(
                s.appName, s.navDashboard, s.navServers, s.navNetwork, s.navSettings,
                s.setupTitle, s.setupSubtitle, s.continueSetup, s.githubTitle, s.githubSubtitle,
                s.tabDevice, s.tabPat, s.yourCode, s.copyCode, s.openGithub, s.waitingApproval,
                s.requiredPermissions, s.validateAndSave, s.storedSecurely, s.networkTitle,
                s.networkSubtitle, s.embeddedNode, s.connectNetwork, s.authKey, s.createTitle,
                s.stepTarget, s.stepConfig, s.stepReview, s.createServer, s.provisioning,
                s.stepAccountCheck, s.stepRepoEnsure, s.stepSecrets, s.stepDispatch,
                s.stepRunner, s.stepJoin, s.stepHandshake, s.stepReady, s.connectionInfo,
                s.address, s.port, s.username, s.password, s.openRemote, s.lifecycle,
                s.rerunAction, s.expiryWarning, s.warningBanner, s.backups, s.createBackup,
                s.restore, s.history, s.remoteTitle, s.nativeEngine, s.secureChannel,
                s.settingsTitle, s.theme, s.languageLabel, s.security, s.guides,
            )
            assertTrue(values.all { it.isNotBlank() }, "blank copy in locale ${s.tag}")
        }
    }
}
