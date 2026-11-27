package app.remotehub.i18n
import androidx.compose.runtime.staticCompositionLocalOf
/**
 * Copy for one locale.
 *
 * The values live in a map instead of constructor parameters: a data class with this
 * many properties would put more than 255 arguments on the constructor, which the JVM
 * rejects with a ClassFormatError. Every key is still a typed property, so a typo is a
 * compile error and a missing translation fails loudly at first use.
 */
class AppStrings(
    val tag: String,
    val rtl: Boolean,
    private val values: Map<String, String>,
) {
    init {
        val missing = requiredKeys.filterNot { values.containsKey(it) }
        require(missing.isEmpty()) { "locale $tag is missing copy: ${missing.joinToString()}" }
    }
    val appName: String = values.getValue("appName")
    val navDashboard: String = values.getValue("navDashboard")
    val navServers: String = values.getValue("navServers")
    val navNetwork: String = values.getValue("navNetwork")
    val navSettings: String = values.getValue("navSettings")
    val setupTitle: String = values.getValue("setupTitle")
    val setupSubtitle: String = values.getValue("setupSubtitle")
    val continueSetup: String = values.getValue("continueSetup")
    val githubAccount: String = values.getValue("githubAccount")
    val privateNetwork: String = values.getValue("privateNetwork")
    val remoteProvider: String = values.getValue("remoteProvider")
    val connected: String = values.getValue("connected")
    val notConnected: String = values.getValue("notConnected")
    val online: String = values.getValue("online")
    val offline: String = values.getValue("offline")
    val activeServers: String = values.getValue("activeServers")
    val timeRemaining: String = values.getValue("timeRemaining")
    val minutesUsed: String = values.getValue("minutesUsed")
    val yourServers: String = values.getValue("yourServers")
    val newServer: String = values.getValue("newServer")
    val connect: String = values.getValue("connect")
    val stop: String = values.getValue("stop")
    val restart: String = values.getValue("restart")
    val rebuild: String = values.getValue("rebuild")
    val details: String = values.getValue("details")
    val stateProvisioning: String = values.getValue("stateProvisioning")
    val stateReady: String = values.getValue("stateReady")
    val stateConnected: String = values.getValue("stateConnected")
    val stateExpiring: String = values.getValue("stateExpiring")
    val stateStopping: String = values.getValue("stateStopping")
    val stateStopped: String = values.getValue("stateStopped")
    val stateFailed: String = values.getValue("stateFailed")
    val emptyTitle: String = values.getValue("emptyTitle")
    val emptyBody: String = values.getValue("emptyBody")
    val quotaTitle: String = values.getValue("quotaTitle")
    val concurrentJobs: String = values.getValue("concurrentJobs")
    val jobMaxDuration: String = values.getValue("jobMaxDuration")
    val complianceNotice: String = values.getValue("complianceNotice")
    val githubTitle: String = values.getValue("githubTitle")
    val githubSubtitle: String = values.getValue("githubSubtitle")
    val tabDevice: String = values.getValue("tabDevice")
    val tabPat: String = values.getValue("tabPat")
    val deviceInstruction: String = values.getValue("deviceInstruction")
    val yourCode: String = values.getValue("yourCode")
    val copyCode: String = values.getValue("copyCode")
    val openGithub: String = values.getValue("openGithub")
    val waitingApproval: String = values.getValue("waitingApproval")
    val expiresInSeconds: String = values.getValue("expiresInSeconds")
    val patLabel: String = values.getValue("patLabel")
    val patPlaceholder: String = values.getValue("patPlaceholder")
    val requiredPermissions: String = values.getValue("requiredPermissions")
    val grantedPermissions: String = values.getValue("grantedPermissions")
    val missingPermissions: String = values.getValue("missingPermissions")
    val validateAndSave: String = values.getValue("validateAndSave")
    val storedSecurely: String = values.getValue("storedSecurely")
    val connectedAs: String = values.getValue("connectedAs")
    val disconnect: String = values.getValue("disconnect")
    val recommended: String = values.getValue("recommended")
    val deviceFlowHint: String = values.getValue("deviceFlowHint")
    val findingAddressHint: String = values.getValue("findingAddressHint")
    val progressSubtitle: String = values.getValue("progressSubtitle")
    val remoteDesktop: String = values.getValue("remoteDesktop")
    val sessionDetail: String = values.getValue("sessionDetail")
    val backup: String = values.getValue("backup")
    val connectRemote: String = values.getValue("connectRemote")
    val expiringBanner: String = values.getValue("expiringBanner")
    val expiryAlertHint: String = values.getValue("expiryAlertHint")
    val findingAddress: String = values.getValue("findingAddress")
    val githubStep1: String = values.getValue("githubStep1")
    val githubStep2: String = values.getValue("githubStep2")
    val githubStep3: String = values.getValue("githubStep3")
    val guideGithub: String = values.getValue("guideGithub")
    val guideRdp: String = values.getValue("guideRdp")
    val guideSecurity: String = values.getValue("guideSecurity")
    val guideTailscale: String = values.getValue("guideTailscale")
    val hidePassword: String = values.getValue("hidePassword")
    val language: String = values.getValue("language")
    val networkNote: String = values.getValue("networkNote")
    val notificationsTitle: String = values.getValue("notificationsTitle")
    val openWorkflowRun: String = values.getValue("openWorkflowRun")
    val privacyNote: String = values.getValue("privacyNote")
    val rdpGuide1: String = values.getValue("rdpGuide1")
    val rdpGuide2: String = values.getValue("rdpGuide2")
    val rdpGuide3: String = values.getValue("rdpGuide3")
    val rerun: String = values.getValue("rerun")
    val rerunNote: String = values.getValue("rerunNote")
    val riskNote: String = values.getValue("riskNote")
    val scopesNote: String = values.getValue("scopesNote")
    val security1: String = values.getValue("security1")
    val security2: String = values.getValue("security2")
    val security3: String = values.getValue("security3")
    val security4: String = values.getValue("security4")
    val showPassword: String = values.getValue("showPassword")
    val stopServer: String = values.getValue("stopServer")
    val stoppedBanner: String = values.getValue("stoppedBanner")
    val tailscaleStep1: String = values.getValue("tailscaleStep1")
    val tailscaleStep2: String = values.getValue("tailscaleStep2")
    val tailscaleStep3: String = values.getValue("tailscaleStep3")
    val setupGithubTitle: String = values.getValue("setupGithubTitle")
    val setupGithubHint: String = values.getValue("setupGithubHint")
    val setupGithubWhy: String = values.getValue("setupGithubWhy")
    val setupMakeToken: String = values.getValue("setupMakeToken")
    val setupTokenStep1: String = values.getValue("setupTokenStep1")
    val setupTokenStep2: String = values.getValue("setupTokenStep2")
    val setupTokenStep3: String = values.getValue("setupTokenStep3")
    val setupPasteToken: String = values.getValue("setupPasteToken")
    val setupNetworkTitle: String = values.getValue("setupNetworkTitle")
    val setupNetworkHint: String = values.getValue("setupNetworkHint")
    val setupNetworkWhy: String = values.getValue("setupNetworkWhy")
    val setupOpenKeys: String = values.getValue("setupOpenKeys")
    val setupKeyStep1: String = values.getValue("setupKeyStep1")
    val setupKeyStep2: String = values.getValue("setupKeyStep2")
    val setupKeyStep3: String = values.getValue("setupKeyStep3")
    val setupPasteKey: String = values.getValue("setupPasteKey")
    val setupSaveConnect: String = values.getValue("setupSaveConnect")
    val setupNetworkDone: String = values.getValue("setupNetworkDone")
    val setupCreateServer: String = values.getValue("setupCreateServer")
    val setupMissingGithub: String = values.getValue("setupMissingGithub")
    val setupMissingNetwork: String = values.getValue("setupMissingNetwork")
    val setupRiskNote: String = values.getValue("setupRiskNote")
    val scopeMissing: String = values.getValue("scopeMissing")
    val working: String = values.getValue("working")
    val retry: String = values.getValue("retry")
    val openServer: String = values.getValue("openServer")
    val createSubtitle: String = values.getValue("createSubtitle")
    val createNameLabel: String = values.getValue("createNameLabel")
    val createNameHint: String = values.getValue("createNameHint")
    val createDuration: String = values.getValue("createDuration")
    val createDurationHint: String = values.getValue("createDurationHint")
    val createDurationMax: String = values.getValue("createDurationMax")
    val createWhatHappens: String = values.getValue("createWhatHappens")
    val createBulletRepo: String = values.getValue("createBulletRepo")
    val createBulletWindows: String = values.getValue("createBulletWindows")
    val createBulletTunnel: String = values.getValue("createBulletTunnel")
    val createBulletPassword: String = values.getValue("createBulletPassword")
    val createEphemeralNote: String = values.getValue("createEphemeralNote")
    val createPublicNote: String = values.getValue("createPublicNote")
    val createStart: String = values.getValue("createStart")
    val progressTitle: String = values.getValue("progressTitle")
    val progressRunning: String = values.getValue("progressRunning")
    val progressDone: String = values.getValue("progressDone")
    val progressFailed: String = values.getValue("progressFailed")
    val progressStarting: String = values.getValue("progressStarting")
    val progressLog: String = values.getValue("progressLog")
    val progressOpenServer: String = values.getValue("progressOpenServer")
    val stepAccount: String = values.getValue("stepAccount")
    val stepRepo: String = values.getValue("stepRepo")
    val stepMachine: String = values.getValue("stepMachine")
    val stepNetwork: String = values.getValue("stepNetwork")
    val oauthClientId: String = values.getValue("oauthClientId")
    val oauthClientIdHint: String = values.getValue("oauthClientIdHint")
    val patHint: String = values.getValue("patHint")
    val networkTitle: String = values.getValue("networkTitle")
    val networkSubtitle: String = values.getValue("networkSubtitle")
    val embeddedNode: String = values.getValue("embeddedNode")
    val userspaceMode: String = values.getValue("userspaceMode")
    val noRootRequired: String = values.getValue("noRootRequired")
    val vpnCoexistence: String = values.getValue("vpnCoexistence")
    val tailnet: String = values.getValue("tailnet")
    val nodeName: String = values.getValue("nodeName")
    val nodeAddress: String = values.getValue("nodeAddress")
    val relay: String = values.getValue("relay")
    val latency: String = values.getValue("latency")
    val transport: String = values.getValue("transport")
    val connectNetwork: String = values.getValue("connectNetwork")
    val disconnectNetwork: String = values.getValue("disconnectNetwork")
    val authKey: String = values.getValue("authKey")
    val authKeyHint: String = values.getValue("authKeyHint")
    val officialSignIn: String = values.getValue("officialSignIn")
    val openBrowser: String = values.getValue("openBrowser")
    val createTitle: String = values.getValue("createTitle")
    val stepLabel: String = values.getValue("stepLabel")
    val ofLabel: String = values.getValue("ofLabel")
    val stepTarget: String = values.getValue("stepTarget")
    val stepConfig: String = values.getValue("stepConfig")
    val stepReview: String = values.getValue("stepReview")
    val provider: String = values.getValue("provider")
    val providerHint: String = values.getValue("providerHint")
    val region: String = values.getValue("region")
    val osImage: String = values.getValue("osImage")
    val machineSize: String = values.getValue("machineSize")
    val serverName: String = values.getValue("serverName")
    val expiryWarning: String = values.getValue("expiryWarning")
    val minutesBefore: String = values.getValue("minutesBefore")
    val autoBackup: String = values.getValue("autoBackup")
    val backupDestination: String = values.getValue("backupDestination")
    val sameRepo: String = values.getValue("sameRepo")
    val clientSideEncrypted: String = values.getValue("clientSideEncrypted")
    val publicRepoWarning: String = values.getValue("publicRepoWarning")
    val back: String = values.getValue("back")
    val next: String = values.getValue("next")
    val createServer: String = values.getValue("createServer")
    val provisioning: String = values.getValue("provisioning")
    val liveProgress: String = values.getValue("liveProgress")
    val cancel: String = values.getValue("cancel")
    val stepAccountCheck: String = values.getValue("stepAccountCheck")
    val stepRepoEnsure: String = values.getValue("stepRepoEnsure")
    val stepSecrets: String = values.getValue("stepSecrets")
    val stepDispatch: String = values.getValue("stepDispatch")
    val stepRunner: String = values.getValue("stepRunner")
    val stepJoin: String = values.getValue("stepJoin")
    val stepHandshake: String = values.getValue("stepHandshake")
    val stepReady: String = values.getValue("stepReady")
    val running: String = values.getValue("running")
    val done: String = values.getValue("done")
    val pending: String = values.getValue("pending")
    val failed: String = values.getValue("failed")
    val connectionInfo: String = values.getValue("connectionInfo")
    val address: String = values.getValue("address")
    val port: String = values.getValue("port")
    val username: String = values.getValue("username")
    val password: String = values.getValue("password")
    val copy: String = values.getValue("copy")
    val show: String = values.getValue("show")
    val hide: String = values.getValue("hide")
    val fingerprint: String = values.getValue("fingerprint")
    val openRemote: String = values.getValue("openRemote")
    val lifecycle: String = values.getValue("lifecycle")
    val rerunAction: String = values.getValue("rerunAction")
    val uptime: String = values.getValue("uptime")
    val warningBanner: String = values.getValue("warningBanner")
    val specifications: String = values.getValue("specifications")
    val cores: String = values.getValue("cores")
    val memory: String = values.getValue("memory")
    val disk: String = values.getValue("disk")
    val operatingSystem: String = values.getValue("operatingSystem")
    val backups: String = values.getValue("backups")
    val createBackup: String = values.getValue("createBackup")
    val restore: String = values.getValue("restore")
    val encrypted: String = values.getValue("encrypted")
    val history: String = values.getValue("history")
    val workflowRun: String = values.getValue("workflowRun")
    val openInGithub: String = values.getValue("openInGithub")
    val repository: String = values.getValue("repository")
    val lastError: String = values.getValue("lastError")
    val remoteTitle: String = values.getValue("remoteTitle")
    val connectedTo: String = values.getValue("connectedTo")
    val bandwidth: String = values.getValue("bandwidth")
    val keyboard: String = values.getValue("keyboard")
    val clipboard: String = values.getValue("clipboard")
    val ctrlAltDel: String = values.getValue("ctrlAltDel")
    val rightClick: String = values.getValue("rightClick")
    val fullscreen: String = values.getValue("fullscreen")
    val touchMode: String = values.getValue("touchMode")
    val trackpadMode: String = values.getValue("trackpadMode")
    val quality: String = values.getValue("quality")
    val disconnectSession: String = values.getValue("disconnectSession")
    val nativeEngine: String = values.getValue("nativeEngine")
    val secureChannel: String = values.getValue("secureChannel")
    val settingsTitle: String = values.getValue("settingsTitle")
    val appearance: String = values.getValue("appearance")
    val theme: String = values.getValue("theme")
    val themeSystem: String = values.getValue("themeSystem")
    val themeLight: String = values.getValue("themeLight")
    val themeDark: String = values.getValue("themeDark")
    val languageLabel: String = values.getValue("languageLabel")
    val notifications: String = values.getValue("notifications")
    val expiryAlert: String = values.getValue("expiryAlert")
    val alertThreshold: String = values.getValue("alertThreshold")
    val security: String = values.getValue("security")
    val biometricLock: String = values.getValue("biometricLock")
    val clearCredentials: String = values.getValue("clearCredentials")
    val storageEncryption: String = values.getValue("storageEncryption")
    val guides: String = values.getValue("guides")
    val gettingStarted: String = values.getValue("gettingStarted")
    val troubleshooting: String = values.getValue("troubleshooting")
    val policyAndLimits: String = values.getValue("policyAndLimits")
    val about: String = values.getValue("about")
    val version: String = values.getValue("version")
    val seconds: String = values.getValue("seconds")
    val minutes: String = values.getValue("minutes")
    val logsTitle: String = values.getValue("logsTitle")
    val logsSubtitle: String = values.getValue("logsSubtitle")
    val logsEmpty: String = values.getValue("logsEmpty")
    val logsEmptyHint: String = values.getValue("logsEmptyHint")
    val logsAll: String = values.getValue("logsAll")
    val logsErrors: String = values.getValue("logsErrors")
    val logsWarnings: String = values.getValue("logsWarnings")
    val logsNetwork: String = values.getValue("logsNetwork")
    val logsGithub: String = values.getValue("logsGithub")
    val logsRdp: String = values.getValue("logsRdp")
    val logsSystem: String = values.getValue("logsSystem")
    val logsCopy: String = values.getValue("logsCopy")
    val logsCopied: String = values.getValue("logsCopied")
    val logsClear: String = values.getValue("logsClear")
    val logsCleared: String = values.getValue("logsCleared")
    val logsShare: String = values.getValue("logsShare")
    val logsSearchHint: String = values.getValue("logsSearchHint")
    val logsDetail: String = values.getValue("logsDetail")
    val logsEnvironment: String = values.getValue("logsEnvironment")
    val logsCountLabel: String = values.getValue("logsCountLabel")
    val viewLogs: String = values.getValue("viewLogs")
    val diagnosticsSection: String = values.getValue("diagnosticsSection")
    val diagnosticsHint: String = values.getValue("diagnosticsHint")
    val lastCrashTitle: String = values.getValue("lastCrashTitle")
    val lastCrashHint: String = values.getValue("lastCrashHint")
    val logsLiveOn: String = values.getValue("logsLiveOn")
    val logsExpandAll: String = values.getValue("logsExpandAll")
    val errNetlinkTitle: String = values.getValue("errNetlinkTitle")
    val errNetlinkHint: String = values.getValue("errNetlinkHint")
    val errLogDirTitle: String = values.getValue("errLogDirTitle")
    val errLogDirHint: String = values.getValue("errLogDirHint")
    val errPanicTitle: String = values.getValue("errPanicTitle")
    val errPanicHint: String = values.getValue("errPanicHint")
    val errAuthKeyTitle: String = values.getValue("errAuthKeyTitle")
    val errAuthKeyHint: String = values.getValue("errAuthKeyHint")
    val errKeyExpiredTitle: String = values.getValue("errKeyExpiredTitle")
    val errKeyExpiredHint: String = values.getValue("errKeyExpiredHint")
    val errOfflineTitle: String = values.getValue("errOfflineTitle")
    val errOfflineHint: String = values.getValue("errOfflineHint")
    val errTimeoutTitle: String = values.getValue("errTimeoutTitle")
    val errTimeoutHint: String = values.getValue("errTimeoutHint")
    val errTlsTitle: String = values.getValue("errTlsTitle")
    val errTlsHint: String = values.getValue("errTlsHint")
    val errRefusedTitle: String = values.getValue("errRefusedTitle")
    val errRefusedHint: String = values.getValue("errRefusedHint")
    val errGithubAuthTitle: String = values.getValue("errGithubAuthTitle")
    val errGithubAuthHint: String = values.getValue("errGithubAuthHint")
    val errGithubScopeTitle: String = values.getValue("errGithubScopeTitle")
    val errGithubScopeHint: String = values.getValue("errGithubScopeHint")
    val errGithubRateTitle: String = values.getValue("errGithubRateTitle")
    val errGithubRateHint: String = values.getValue("errGithubRateHint")
    val errStorageTitle: String = values.getValue("errStorageTitle")
    val errStorageHint: String = values.getValue("errStorageHint")
    val errRdpTitle: String = values.getValue("errRdpTitle")
    val errRdpHint: String = values.getValue("errRdpHint")
    val errGenericTitle: String = values.getValue("errGenericTitle")
    val errGenericHint: String = values.getValue("errGenericHint")
    val errCryptoTitle: String = values.getValue("errCryptoTitle")
    val errCryptoHint: String = values.getValue("errCryptoHint")
    val serversTitle: String = values.getValue("serversTitle")
    val serversSubtitle: String = values.getValue("serversSubtitle")
    val serversEmpty: String = values.getValue("serversEmpty")
    val serversEmptyHint: String = values.getValue("serversEmptyHint")
    val networkScreenTitle: String = values.getValue("networkScreenTitle")
    val networkScreenSubtitle: String = values.getValue("networkScreenSubtitle")
    val deviceAccess: String = values.getValue("deviceAccess")
    val deviceAccessHint: String = values.getValue("deviceAccessHint")
    val vpnOn: String = values.getValue("vpnOn")
    val vpnOff: String = values.getValue("vpnOff")
    val vpnEnable: String = values.getValue("vpnEnable")
    val vpnDisable: String = values.getValue("vpnDisable")
    val vpnWorking: String = values.getValue("vpnWorking")
    val vpnDenied: String = values.getValue("vpnDenied")
    val vpnConflict: String = values.getValue("vpnConflict")
    val vpnScope: String = values.getValue("vpnScope")
    val vpnInterface: String = values.getValue("vpnInterface")
    val restartServer: String = values.getValue("restartServer")
    val restartHint: String = values.getValue("restartHint")
    val restarting: String = values.getValue("restarting")
    val remoteConnecting: String = values.getValue("remoteConnecting")
    val remoteHandshake: String = values.getValue("remoteHandshake")
    val exitFullscreen: String = values.getValue("exitFullscreen")
    val brandTagline: String = values.getValue("brandTagline")
    val errVpnTitle: String = values.getValue("errVpnTitle")
    val errVpnHint: String = values.getValue("errVpnHint")
    val peersTitle: String = values.getValue("peersTitle")
    val peersEmpty: String = values.getValue("peersEmpty")
}

/** Every key a locale must provide. */
val requiredKeys: List<String> = listOf(
    "appName", "navDashboard", "navServers", "navNetwork", "navSettings", "setupTitle",
    "setupSubtitle", "continueSetup", "githubAccount", "privateNetwork", "remoteProvider", "connected",
    "notConnected", "online", "offline", "activeServers", "timeRemaining", "minutesUsed",
    "yourServers", "newServer", "connect", "stop", "restart", "rebuild",
    "details", "stateProvisioning", "stateReady", "stateConnected", "stateExpiring", "stateStopping",
    "stateStopped", "stateFailed", "emptyTitle", "emptyBody", "quotaTitle", "concurrentJobs",
    "jobMaxDuration", "complianceNotice", "githubTitle", "githubSubtitle", "tabDevice", "tabPat",
    "deviceInstruction", "yourCode", "copyCode", "openGithub", "waitingApproval", "expiresInSeconds",
    "patLabel", "patPlaceholder", "requiredPermissions", "grantedPermissions", "missingPermissions", "validateAndSave",
    "storedSecurely", "connectedAs", "disconnect", "recommended", "deviceFlowHint", "findingAddressHint",
    "progressSubtitle", "remoteDesktop", "sessionDetail", "backup", "connectRemote", "expiringBanner",
    "expiryAlertHint", "findingAddress", "githubStep1", "githubStep2", "githubStep3", "guideGithub",
    "guideRdp", "guideSecurity", "guideTailscale", "hidePassword", "language", "networkNote",
    "notificationsTitle", "openWorkflowRun", "privacyNote", "rdpGuide1", "rdpGuide2", "rdpGuide3",
    "rerun", "rerunNote", "riskNote", "scopesNote", "security1", "security2",
    "security3", "security4", "showPassword", "stopServer", "stoppedBanner", "tailscaleStep1",
    "tailscaleStep2", "tailscaleStep3", "setupGithubTitle", "setupGithubHint", "setupGithubWhy", "setupMakeToken",
    "setupTokenStep1", "setupTokenStep2", "setupTokenStep3", "setupPasteToken", "setupNetworkTitle", "setupNetworkHint",
    "setupNetworkWhy", "setupOpenKeys", "setupKeyStep1", "setupKeyStep2", "setupKeyStep3", "setupPasteKey",
    "setupSaveConnect", "setupNetworkDone", "setupCreateServer", "setupMissingGithub", "setupMissingNetwork", "setupRiskNote",
    "scopeMissing", "working", "retry", "openServer", "createSubtitle", "createNameLabel",
    "createNameHint", "createDuration", "createDurationHint", "createDurationMax", "createWhatHappens", "createBulletRepo",
    "createBulletWindows", "createBulletTunnel", "createBulletPassword", "createEphemeralNote", "createPublicNote", "createStart",
    "progressTitle", "progressRunning", "progressDone", "progressFailed", "progressStarting", "progressLog",
    "progressOpenServer", "stepAccount", "stepRepo", "stepMachine", "stepNetwork", "oauthClientId",
    "oauthClientIdHint", "patHint", "networkTitle", "networkSubtitle", "embeddedNode", "userspaceMode",
    "noRootRequired", "vpnCoexistence", "tailnet", "nodeName", "nodeAddress", "relay",
    "latency", "transport", "connectNetwork", "disconnectNetwork", "authKey", "authKeyHint",
    "officialSignIn", "openBrowser", "createTitle", "stepLabel", "ofLabel", "stepTarget",
    "stepConfig", "stepReview", "provider", "providerHint", "region", "osImage",
    "machineSize", "serverName", "expiryWarning", "minutesBefore", "autoBackup", "backupDestination",
    "sameRepo", "clientSideEncrypted", "publicRepoWarning", "back", "next", "createServer",
    "provisioning", "liveProgress", "cancel", "stepAccountCheck", "stepRepoEnsure", "stepSecrets",
    "stepDispatch", "stepRunner", "stepJoin", "stepHandshake", "stepReady", "running",
    "done", "pending", "failed", "connectionInfo", "address", "port",
    "username", "password", "copy", "show", "hide", "fingerprint",
    "openRemote", "lifecycle", "rerunAction", "uptime", "warningBanner", "specifications",
    "cores", "memory", "disk", "operatingSystem", "backups", "createBackup",
    "restore", "encrypted", "history", "workflowRun", "openInGithub", "repository",
    "lastError", "remoteTitle", "connectedTo", "bandwidth", "keyboard", "clipboard",
    "ctrlAltDel", "rightClick", "fullscreen", "touchMode", "trackpadMode", "quality", "disconnectSession",
    "nativeEngine", "secureChannel", "settingsTitle", "appearance", "theme", "themeSystem",
    "themeLight", "themeDark", "languageLabel", "notifications", "expiryAlert", "alertThreshold",
    "security", "biometricLock", "clearCredentials", "storageEncryption", "guides", "gettingStarted",
    "troubleshooting", "policyAndLimits", "about", "version", "seconds", "minutes",
    "logsTitle", "logsSubtitle", "logsEmpty", "logsEmptyHint", "logsAll",
    "logsErrors", "logsWarnings", "logsNetwork", "logsGithub", "logsRdp",
    "logsSystem", "logsCopy", "logsCopied", "logsClear", "logsCleared",
    "logsShare", "logsSearchHint", "logsDetail", "logsEnvironment", "logsCountLabel",
    "viewLogs", "diagnosticsSection", "diagnosticsHint", "lastCrashTitle", "lastCrashHint",
    "logsLiveOn", "logsExpandAll", "errNetlinkTitle", "errNetlinkHint", "errLogDirTitle",
    "errLogDirHint", "errPanicTitle", "errPanicHint", "errAuthKeyTitle", "errAuthKeyHint",
    "errKeyExpiredTitle", "errKeyExpiredHint", "errOfflineTitle", "errOfflineHint", "errTimeoutTitle",
    "errTimeoutHint", "errTlsTitle", "errTlsHint", "errRefusedTitle", "errRefusedHint",
    "errGithubAuthTitle", "errGithubAuthHint", "errGithubScopeTitle", "errGithubScopeHint", "errGithubRateTitle",
    "errGithubRateHint", "errStorageTitle", "errStorageHint", "errRdpTitle", "errRdpHint",
    "errGenericTitle", "errGenericHint",
    "errCryptoTitle", "errCryptoHint",
    "serversTitle",
    "serversSubtitle",
    "serversEmpty",
    "serversEmptyHint",
    "networkScreenTitle",
    "networkScreenSubtitle",
    "deviceAccess",
    "deviceAccessHint",
    "vpnOn",
    "vpnOff",
    "vpnEnable",
    "vpnDisable",
    "vpnWorking",
    "vpnDenied",
    "vpnConflict",
    "vpnScope",
    "vpnInterface",
    "restartServer",
    "restartHint",
    "restarting",
    "remoteConnecting",
    "remoteHandshake",
    "exitFullscreen",
    "brandTagline",
    "errVpnTitle",
    "errVpnHint",
    "peersTitle",
    "peersEmpty",
)

val Persian = AppStrings("fa", true, mapOf(
    "serversTitle" to "سرورها",
    "serversSubtitle" to "همهٔ نشست‌هایی که ساخته‌اید",
    "serversEmpty" to "هنوز سروری نساخته‌اید",
    "serversEmptyHint" to "از خانه یک سرور بسازید؛ اینجا فهرست می‌شود.",
    "networkScreenTitle" to "شبکهٔ خصوصی",
    "networkScreenSubtitle" to "وضعیت تانل و دسترسی کل دستگاه",
    "deviceAccess" to "دسترسی کل گوشی",
    "deviceAccessHint" to "با روشن کردن این گزینه، تانل به یک VPN واقعی اندروید تبدیل می‌شود و هر برنامه‌ای (مثل aRDP) می‌تواند به نشانی‌های ۱۰۰.x وصل شود.",
    "vpnOn" to "روشن",
    "vpnOff" to "خاموش",
    "vpnEnable" to "روشن کردن VPN دستگاه",
    "vpnDisable" to "خاموش کردن VPN دستگاه",
    "vpnWorking" to "در حال ساختن رابط VPN…",
    "vpnDenied" to "اجازهٔ VPN داده نشد.",
    "vpnConflict" to "اندروید در هر لحظه فقط یک VPN را می‌پذیرد. اگر VPN دیگری روشن است، اول آن را خاموش کنید.",
    "vpnScope" to "فقط بازهٔ ۱۰۰.۶۴.۰.۰/۱۰ وارد تانل می‌شود؛ بقیهٔ ترافیک دست‌نخورده می‌ماند.",
    "vpnInterface" to "رابط سیستمی",
    "restartServer" to "اجرای دوبارهٔ همین نشست",
    "restartHint" to "همان ورک‌فلو با همان شناسه دوباره اجرا می‌شود؛ سرور تازه‌ای ساخته نمی‌شود و رمز عوض نمی‌شود.",
    "restarting" to "در حال اجرای دوباره…",
    "remoteConnecting" to "در حال برقراری نشست ویندوز…",
    "remoteHandshake" to "احراز هویت NLA و دریافت تصویر",
    "exitFullscreen" to "خروج از تمام‌صفحه",
    "brandTagline" to "دسکتاپ ویندوز، روی تانل خودتان",
    "errVpnTitle" to "رابط VPN ساخته نشد",
    "errVpnHint" to "VPN دیگری را خاموش کنید یا اجازهٔ VPN را دوباره بدهید؛ اپ به حالت درون‌برنامه برمی‌گردد.",
    "peersTitle" to "دستگاه‌های تانل",
    "peersEmpty" to "هنوز دستگاهی دیده نمی‌شود",
    "errCryptoTitle" to "موتور رمزنگاری روی این دستگاه بارگذاری نشد",
    "errCryptoHint" to "این نسخه را حذف و تازه نصب کنید؛ اگر تکرار شد، لاگ را بفرستید.",
    "logsTitle" to "لاگ‌ها",
    "logsSubtitle" to "هرچه در برنامه رخ داده، با زمان دقیق",
    "logsEmpty" to "هنوز لاگی ثبت نشده",
    "logsEmptyHint" to "با هر تلاش اتصال، اینجا پر می‌شود.",
    "logsAll" to "همه",
    "logsErrors" to "خطاها",
    "logsWarnings" to "هشدارها",
    "logsNetwork" to "شبکه",
    "logsGithub" to "گیت‌هاب",
    "logsRdp" to "دسکتاپ",
    "logsSystem" to "سیستم",
    "logsCopy" to "کپی لاگ",
    "logsCopied" to "لاگ کپی شد",
    "logsClear" to "پاک کردن",
    "logsCleared" to "لاگ پاک شد",
    "logsShare" to "خروجی گرفتن",
    "logsSearchHint" to "جست‌وجو در لاگ",
    "logsDetail" to "جزئیات فنی",
    "logsEnvironment" to "وضعیت محیط",
    "logsCountLabel" to "ردیف",
    "viewLogs" to "دیدن لاگ",
    "diagnosticsSection" to "لاگ و عیب‌یابی",
    "diagnosticsHint" to "اگر چیزی کار نکرد، متن دقیق خطا اینجاست.",
    "lastCrashTitle" to "اجرای قبلی ناخواسته بسته شد",
    "lastCrashHint" to "دلیل ثبت‌شده در لاگ‌ها آمده است.",
    "logsLiveOn" to "به‌روزرسانی زنده",
    "logsExpandAll" to "نمایش جزئیات",
    "errNetlinkTitle" to "اندروید اجازهٔ خواندن کارت شبکه را نداد",
    "errNetlinkHint" to "نسخهٔ جدید از مسیر مجاز استفاده می‌کند؛ برنامه را ببندید و دوباره باز کنید.",
    "errLogDirTitle" to "مسیر قابل‌نوشتن برای فایل‌های Tailscale پیدا نشد",
    "errLogDirHint" to "حافظهٔ برنامه پر یا محدود است؛ فضای خالی کنید و دوباره تلاش کنید.",
    "errPanicTitle" to "موتور شبکه خطای داخلی داد",
    "errPanicHint" to "برنامه بسته نشد؛ متن کامل خطا در لاگ‌ها است. آن را کپی کنید و دوباره تلاش کنید.",
    "errAuthKeyTitle" to "کلید Tailscale پذیرفته نشد",
    "errAuthKeyHint" to "کلید باید tskey-auth باشد، منقضی نشده و یک‌بارمصرفِ استفاده‌نشده باشد؛ کلید تازه بسازید.",
    "errKeyExpiredTitle" to "کلید Tailscale منقضی شده است",
    "errKeyExpiredHint" to "در پنل Keys کلیدی با اعتبار حداقل ۹۰ روز و گزینهٔ Reusable بسازید.",
    "errOfflineTitle" to "اتصال اینترنت یا DNS کار نمی‌کند",
    "errOfflineHint" to "شبکهٔ گوشی را بررسی کنید؛ فیلترشکن روشن مانع اتصال می‌شود.",
    "errTimeoutTitle" to "اتصال در زمان مقرر کامل نشد",
    "errTimeoutHint" to "معمولاً کلید نامعتبر یا شبکهٔ کند است؛ لاگ مرحلهٔ آخر را ببینید و دوباره تلاش کنید.",
    "errTlsTitle" to "گواهی امنیتی تأیید نشد",
    "errTlsHint" to "تاریخ و ساعت گوشی را روی حالت خودکار بگذارید و شبکه را عوض کنید.",
    "errRefusedTitle" to "سرور به درخواست اتصال پاسخ نداد",
    "errRefusedHint" to "ویندوز هنوز بالا نیامده یا RDP آماده نیست؛ چند دقیقه بعد دوباره تلاش کنید.",
    "errGithubAuthTitle" to "توکن گیت‌هاب معتبر نیست",
    "errGithubAuthHint" to "توکن را دوباره بسازید؛ ممکن است پاک یا منقضی شده باشد.",
    "errGithubScopeTitle" to "توکن مجوز کافی ندارد",
    "errGithubScopeHint" to "مجوزهای repo و workflow لازم است؛ از لینک آمادهٔ برنامه توکن تازه بسازید.",
    "errGithubRateTitle" to "گیت‌هاب موقتاً درخواست‌ها را محدود کرد",
    "errGithubRateHint" to "یک دقیقه صبر کنید و دوباره تلاش کنید.",
    "errStorageTitle" to "ذخیره‌سازی امن برنامه در دسترس نیست",
    "errStorageHint" to "فضای خالی کنید یا یک‌بار برنامه را ببندید و باز کنید.",
    "errRdpTitle" to "اتصال دسکتاپ برقرار نشد",
    "errRdpHint" to "آدرس و رمز را دوباره بررسی کنید؛ اگر سرور دوباره ساخته شده، رمز عوض شده است.",
    "errGenericTitle" to "عملیات ناموفق بود",
    "errGenericHint" to "متن دقیق خطا در لاگ‌ها ثبت شده است.",
    "appName" to "RemoteHub",
    "navDashboard" to "داشبورد",
    "navServers" to "سرورها",
    "navNetwork" to "شبکه",
    "navSettings" to "تنظیمات",
    "setupTitle" to "راه‌اندازی",
    "setupSubtitle" to "برای ساخت سرور، این دو اتصال را کامل کنید.",
    "continueSetup" to "ادامه راه‌اندازی",
    "githubAccount" to "حساب GitHub",
    "privateNetwork" to "شبکه خصوصی",
    "remoteProvider" to "سرویس ریموت",
    "connected" to "متصل",
    "notConnected" to "متصل نیست",
    "online" to "آنلاین",
    "offline" to "آفلاین",
    "activeServers" to "سرور فعال",
    "timeRemaining" to "زمان باقی‌مانده",
    "minutesUsed" to "دقیقه مصرف‌شده",
    "yourServers" to "سرورهای شما",
    "newServer" to "سرور جدید",
    "connect" to "اتصال",
    "stop" to "توقف",
    "restart" to "راه‌اندازی مجدد",
    "rebuild" to "بازسازی",
    "details" to "جزئیات",
    "stateProvisioning" to "در حال آماده‌سازی",
    "stateReady" to "آماده",
    "stateConnected" to "متصل",
    "stateExpiring" to "در آستانه پایان",
    "stateStopping" to "در حال توقف",
    "stateStopped" to "متوقف",
    "stateFailed" to "خطا",
    "emptyTitle" to "هنوز سروری ندارید",
    "emptyBody" to "با دکمه سرور جدید، اولین محیط ریموت خود را بسازید.",
    "quotaTitle" to "سهمیه و محدودیت‌ها",
    "concurrentJobs" to "جاب هم‌زمان",
    "jobMaxDuration" to "حداکثر زمان جاب",
    "complianceNotice" to "زیرساخت ریموت روی سرویس مجاز اجرا می‌شود؛ GitHub Actions فقط برای ساخت، تست و انتشار استفاده می‌شود.",
    "githubTitle" to "اتصال به GitHub",
    "githubSubtitle" to "ورود رسمی، بدون اشتراک‌گذاری گذرواژه.",
    "tabDevice" to "کد دستگاه",
    "tabPat" to "توکن شخصی",
    "deviceInstruction" to "کد زیر را در صفحه رسمی GitHub وارد کنید تا این دستگاه مجاز شود.",
    "yourCode" to "کد شما",
    "copyCode" to "کپی کد",
    "openGithub" to "باز کردن GitHub",
    "waitingApproval" to "در انتظار تأیید شما",
    "expiresInSeconds" to "انقضا تا",
    "patLabel" to "توکن دسترسی شخصی",
    "patPlaceholder" to "github_pat_…",
    "requiredPermissions" to "مجوزهای موردنیاز",
    "grantedPermissions" to "مجوزهای اعطاشده",
    "missingPermissions" to "مجوزهای ناقص",
    "validateAndSave" to "اعتبارسنجی و ذخیره",
    "storedSecurely" to "توکن در Keystore اندروید و DPAPI ویندوز نگهداری می‌شود.",
    "connectedAs" to "متصل به‌عنوان",
    "disconnect" to "قطع اتصال",
    "recommended" to "پیشنهادی",
    "deviceFlowHint" to "بدون نیاز به ساخت توکن دستی؛ مناسب اکثر کاربران.",
    "findingAddressHint" to "هر چند ثانیه دوباره تلاش می‌کنم. شبکه خصوصی باید وصل بماند.",
    "progressSubtitle" to "این کار چند دقیقه طول می‌کشد.",
    "remoteDesktop" to "ریموت دسکتاپ",
    "sessionDetail" to "جزئیات سرور",
    "backup" to "بکاپ رمزنگاری‌شده",
    "connectRemote" to "ریموت دسکتاپ داخل اپ",
    "expiringBanner" to "زمان این سرور رو به پایان است. اگر داده مهمی داری، همین حالا بکاپ بگیر.",
    "expiryAlertHint" to "پیش از پایان زمان، به تو خبر می‌دهم.",
    "findingAddress" to "در حال پیدا کردن آدرس سرور روی شبکه خصوصی…",
    "githubStep1" to "روی لینک زیر بزن. صفحه ساخت توکن با مجوزهای repo و workflow از قبل تیک‌خورده باز می‌شود.",
    "githubStep2" to "در بخش Expiration یک مدت انتخاب کن و روی Generate token بزن.",
    "githubStep3" to "توکن را کپی کن، به اپ برگرد و در کادر بچسبان. بعد دکمه اتصال را بزن.",
    "guideGithub" to "اتصال به گیت‌هاب",
    "guideRdp" to "استفاده از ریموت دسکتاپ",
    "guideSecurity" to "امنیت و حریم خصوصی",
    "guideTailscale" to "شبکه خصوصی Tailscale",
    "hidePassword" to "مخفی کردن گذرواژه",
    "language" to "زبان",
    "networkNote" to "کلید باید Reusable و Ephemeral باشد و اعتبارش حداقل ۹۰ روز؛ در غیر این صورت نشست‌های بعدی وصل نمی‌شوند.",
    "notificationsTitle" to "اعلان‌ها",
    "openWorkflowRun" to "دیدن اجرای ورک‌فلو در گیت‌هاب",
    "privacyNote" to "توکن و کلید فقط در حافظه رمزنگاری‌شده همین گوشی می‌مانند و هرگز در لاگ چاپ نمی‌شوند.",
    "rdpGuide1" to "در صفحه جزئیات، دکمه ریموت دسکتاپ را بزن. تصویر تمام‌صفحه داخل همین اپ باز می‌شود.",
    "rdpGuide2" to "یک انگشت = حرکت نشانگر، ضربه = کلیک، دو انگشت = اسکرول. حالت ترک‌پد را از نوار بالا عوض کن.",
    "rdpGuide3" to "دکمه کیبورد صفحه‌کلید گوشی را باز می‌کند و دکمه کلیپ‌بورد متن کپی‌شده را به سرور می‌فرستد.",
    "rerun" to "اجرای دوباره",
    "rerunNote" to "اجرای دوباره یک ماشین تازه می‌سازد: آدرس و گذرواژه عوض می‌شود و داده‌های ماشین قبلی پاک می‌شود.",
    "riskNote" to "ماشین روی رانر گیت‌هاب ساخته می‌شود. این کار با شرایط استفاده گیت‌هاب سازگار نیست و ممکن است حساب محدود شود.",
    "scopesNote" to "مجوز repo برای ساخت ریپو و رمزگذاری Secrets لازم است؛ مجوز workflow برای گذاشتن فایل ورک‌فلو.",
    "security1" to "گذرواژه هر نشست تصادفی و ۲۴ کاراکتری است و در لاگ عمومی چاپ نمی‌شود.",
    "security2" to "پورت ریموت فقط از شبکه خصوصی Tailscale باز است، نه از اینترنت.",
    "security3" to "احراز هویت سطح شبکه (NLA) روشن است و حساب پیش‌فرض رانر دست‌نخورده می‌ماند.",
    "security4" to "در پایان نشست، ماشین از شبکه خصوصی خارج می‌شود و کاملاً پاک می‌شود.",
    "showPassword" to "نمایش گذرواژه",
    "stopServer" to "توقف سرور",
    "stoppedBanner" to "این نشست پایان یافته است. برای ادامه، اجرای دوباره را بزن.",
    "tailscaleStep1" to "در سایت Tailscale حساب بساز (رایگان). بعد سه خط بالای صفحه را بزن و منوی Settings را باز کن.",
    "tailscaleStep2" to "در Settings بخش Keys را انتخاب کن. روی Generate auth key بزن، یک اسم برای کلید بگذار و تیک Ephemeral و Reusable را روشن کن.",
    "tailscaleStep3" to "اعتبار کلید (Expiration) را حداقل ۹۰ روز بگذار، کلید را کپی کن و در کادر همین صفحه بچسبان.",
    "setupGithubTitle" to "اتصال حساب GitHub",
    "setupGithubHint" to "یک توکن شخصی لازم است",
    "setupGithubWhy" to "با این توکن برنامه ریپو می‌سازد و ورک‌فلو را اجرا می‌کند.",
    "setupMakeToken" to "ساخت توکن در گیت‌هاب",
    "setupTokenStep1" to "لینک بالا را باز کن؛ مجوزها از قبل تیک خورده‌اند.",
    "setupTokenStep2" to "پایین صفحه روی Generate token بزن.",
    "setupTokenStep3" to "توکن را کپی کن و در کادر زیر بچسبان.",
    "setupPasteToken" to "توکن را اینجا بچسبان",
    "setupNetworkTitle" to "کلید شبکه خصوصی (Tailscale)",
    "setupNetworkHint" to "برای تونل امن بین گوشی و سرور",
    "setupNetworkWhy" to "فقط یک تونل امن است؛ کلاینت جدا روی گوشی لازم نیست.",
    "setupOpenKeys" to "باز کردن صفحه ساخت کلید",
    "setupKeyStep1" to "بعد از ساخت حساب در سایت Tailscale، سه خط بالای صفحه را بزن و منوی Settings را باز کن.",
    "setupKeyStep2" to "بخش Keys را انتخاب کن، یک اسم برای کلید بگذار، تیک Ephemeral و Reusable را روشن کن و اعتبار کلید را حداقل ۹۰ روز بگذار.",
    "setupKeyStep3" to "کلید ساخته‌شده را کپی کن، در کادر پایین بچسبان و «ذخیره و اتصال» را بزن.",
    "setupPasteKey" to "کلید را اینجا بچسبان",
    "setupSaveConnect" to "ذخیره و اتصال",
    "setupNetworkDone" to "شبکه خصوصی وصل است",
    "setupCreateServer" to "ساخت سرور",
    "setupMissingGithub" to "اول حساب گیت‌هاب را وصل کن.",
    "setupMissingNetwork" to "کلید شبکه خصوصی را وارد کن.",
    "setupRiskNote" to "این روش از رانر گیت‌هاب به‌عنوان ویندوز موقت استفاده می‌کند و با قوانین Actions سازگار نیست؛ ممکن است اکانتت محدود شود.",
    "scopeMissing" to "مجوز repo و workflow لازم است. توکن را با لینک بالا دوباره بساز.",
    "working" to "در حال انجام…",
    "retry" to "تلاش دوباره",
    "openServer" to "باز کردن سرور",
    "createSubtitle" to "یک نام و یک مدت انتخاب کن. بقیه خودکار است.",
    "createNameLabel" to "نام سرور",
    "createNameHint" to "حروف انگلیسی و عدد؛ مثلاً windows-1",
    "createDuration" to "مدت نشست",
    "createDurationHint" to "بعد از این مدت ماشین خاموش می‌شود.",
    "createDurationMax" to "حداکثر ۶ ساعت — سقف رسمی هر job در گیت‌هاب.",
    "createWhatHappens" to "چه اتفاقی می‌افتد",
    "createBulletRepo" to "یک ریپو در حساب خودت ساخته می‌شود.",
    "createBulletWindows" to "ویندوز سرور روی رانر گیت‌هاب بالا می‌آید.",
    "createBulletTunnel" to "سرور به شبکه خصوصی تو وصل می‌شود.",
    "createBulletPassword" to "رمز روی گوشی تو می‌ماند و در لاگ چاپ نمی‌شود.",
    "createEphemeralNote" to "این ماشین موقت است. با پایان نشست همهٔ فایل‌ها پاک می‌شوند و نشست بعدی IP تازه دارد.",
    "createPublicNote" to "ریپو عمومی است تا دقیقه‌ها رایگان بمانند. رمز و کلید در لاگ چاپ نمی‌شوند، ولی آدرس سرور عمومی دیده می‌شود.",
    "createStart" to "شروع ساخت",
    "progressTitle" to "در حال ساخت",
    "progressRunning" to "معمولاً ۳ تا ۶ دقیقه طول می‌کشد.",
    "progressDone" to "سرور آماده است.",
    "progressFailed" to "ساخت متوقف شد.",
    "progressStarting" to "در حال شروع…",
    "progressLog" to "گزارش",
    "progressOpenServer" to "دیدن اطلاعات سرور",
    "stepAccount" to "بررسی حساب GitHub",
    "stepRepo" to "ساخت یا آماده‌سازی ریپو",
    "stepMachine" to "آماده‌سازی ماشین ویندوز",
    "stepNetwork" to "اتصال به شبکه خصوصی",
    "oauthClientId" to "شناسهٔ برنامهٔ OAuth",
    "oauthClientIdHint" to "در GitHub → Settings → Developer settings → OAuth Apps یک برنامه بسازید، «Enable Device Flow» را روشن کنید و Client ID را اینجا بگذارید. اگر ندارید، از تب توکن شخصی استفاده کنید.",
    "patHint" to "برای حساب‌های سازمانی یا دسترسی محدود به یک ریپازیتوری.",
    "networkTitle" to "شبکه خصوصی",
    "networkSubtitle" to "Tailscale داخل خود برنامه اجرا می‌شود؛ کلاینت جداگانه لازم نیست.",
    "embeddedNode" to "گره داخلی",
    "userspaceMode" to "Userspace",
    "noRootRequired" to "بدون Root",
    "vpnCoexistence" to "سازگار با VPN فعال",
    "tailnet" to "Tailnet",
    "nodeName" to "نام گره",
    "nodeAddress" to "آدرس گره",
    "relay" to "رله",
    "latency" to "تأخیر",
    "transport" to "مسیر",
    "connectNetwork" to "اتصال شبکه",
    "disconnectNetwork" to "قطع شبکه",
    "authKey" to "کلید احراز هویت",
    "authKeyHint" to "کلید کوتاه‌عمر و Ephemeral از API رسمی ساخته می‌شود.",
    "officialSignIn" to "ورود رسمی",
    "openBrowser" to "باز کردن مرورگر",
    "createTitle" to "ساخت سرور",
    "stepLabel" to "مرحله",
    "ofLabel" to "از",
    "stepTarget" to "مقصد",
    "stepConfig" to "پیکربندی",
    "stepReview" to "بازبینی",
    "provider" to "سرویس‌دهنده",
    "providerHint" to "مقصد باید برای دسترسی ریموت مجاز باشد.",
    "region" to "منطقه",
    "osImage" to "ایمیج سیستم‌عامل",
    "machineSize" to "اندازه ماشین",
    "serverName" to "نام سرور",
    "expiryWarning" to "هشدار پایان زمان",
    "minutesBefore" to "دقیقه قبل از پایان",
    "autoBackup" to "بکاپ خودکار",
    "backupDestination" to "مقصد بکاپ",
    "sameRepo" to "همان ریپازیتوری",
    "clientSideEncrypted" to "رمزنگاری سمت کلاینت",
    "publicRepoWarning" to "ریپازیتوری عمومی است؛ بکاپ‌ها پیش از آپلود رمزنگاری می‌شوند و کلید هرگز آپلود نمی‌شود.",
    "back" to "بازگشت",
    "next" to "بعدی",
    "createServer" to "ساخت سرور",
    "provisioning" to "در حال آماده‌سازی",
    "liveProgress" to "پیشرفت واقعی از GitHub API",
    "cancel" to "لغو",
    "stepAccountCheck" to "بررسی حساب GitHub",
    "stepRepoEnsure" to "بررسی یا ساخت ریپازیتوری",
    "stepSecrets" to "ثبت Secrets رمزنگاری‌شده",
    "stepDispatch" to "اجرای ورک‌فلو",
    "stepRunner" to "دریافت وضعیت اجرا",
    "stepJoin" to "اتصال به شبکه خصوصی",
    "stepHandshake" to "دریافت اطلاعات اتصال",
    "stepReady" to "آماده تحویل",
    "running" to "در حال اجرا",
    "done" to "انجام شد",
    "pending" to "در انتظار",
    "failed" to "ناموفق",
    "connectionInfo" to "اطلاعات اتصال",
    "address" to "آدرس",
    "port" to "پورت",
    "username" to "نام کاربری",
    "password" to "گذرواژه",
    "copy" to "کپی",
    "show" to "نمایش",
    "hide" to "مخفی",
    "fingerprint" to "اثر انگشت گواهی",
    "openRemote" to "ریموت دسکتاپ",
    "lifecycle" to "چرخه حیات",
    "rerunAction" to "اجرای دوباره اکشن",
    "uptime" to "زمان فعالیت",
    "warningBanner" to "زمان این نشست رو به پایان است. برای ادامه، بکاپ بگیرید و دوباره اجرا کنید.",
    "specifications" to "مشخصات",
    "cores" to "هسته",
    "memory" to "حافظه",
    "disk" to "دیسک",
    "operatingSystem" to "سیستم‌عامل",
    "backups" to "بکاپ‌ها",
    "createBackup" to "ایجاد بکاپ",
    "restore" to "بازیابی",
    "encrypted" to "رمزنگاری‌شده",
    "history" to "تاریخچه عملیات",
    "workflowRun" to "اجرای ورک‌فلو",
    "openInGithub" to "باز کردن در GitHub",
    "repository" to "ریپازیتوری",
    "lastError" to "آخرین خطا",
    "remoteTitle" to "ریموت دسکتاپ",
    "connectedTo" to "متصل به",
    "bandwidth" to "پهنای باند",
    "keyboard" to "کیبورد",
    "clipboard" to "کلیپ‌بورد",
    "ctrlAltDel" to "Ctrl+Alt+Del",
    "rightClick" to "راست‌کلیک",
    "fullscreen" to "تمام‌صفحه",
    "touchMode" to "لمس مستقیم",
    "trackpadMode" to "ترک‌پد",
    "quality" to "کیفیت",
    "disconnectSession" to "قطع نشست",
    "nativeEngine" to "موتور بومی FreeRDP",
    "secureChannel" to "کانال رمزنگاری‌شده روی Tailscale",
    "settingsTitle" to "تنظیمات",
    "appearance" to "ظاهر",
    "theme" to "تم",
    "themeSystem" to "سیستم",
    "themeLight" to "روشن",
    "themeDark" to "تیره",
    "languageLabel" to "زبان",
    "notifications" to "اعلان‌ها",
    "expiryAlert" to "هشدار پایان زمان",
    "alertThreshold" to "آستانه هشدار",
    "security" to "امنیت",
    "biometricLock" to "قفل زیست‌سنجی",
    "clearCredentials" to "پاک کردن اعتبارنامه‌ها",
    "storageEncryption" to "رمزنگاری حافظه",
    "guides" to "راهنما",
    "gettingStarted" to "شروع به کار",
    "troubleshooting" to "عیب‌یابی",
    "policyAndLimits" to "سیاست‌ها و محدودیت‌ها",
    "about" to "درباره",
    "version" to "نسخه",
    "seconds" to "ثانیه",
    "minutes" to "دقیقه",
))

val English = AppStrings("en", false, mapOf(
    "serversTitle" to "Servers",
    "serversSubtitle" to "Every session you have created",
    "serversEmpty" to "No servers yet",
    "serversEmptyHint" to "Create one from Home and it will be listed here.",
    "networkScreenTitle" to "Private network",
    "networkScreenSubtitle" to "Tunnel status and device-wide access",
    "deviceAccess" to "Whole-device access",
    "deviceAccessHint" to "Turning this on promotes the tunnel to a real Android VPN, so any app (aRDP, for example) can reach 100.x addresses.",
    "vpnOn" to "On",
    "vpnOff" to "Off",
    "vpnEnable" to "Enable device VPN",
    "vpnDisable" to "Disable device VPN",
    "vpnWorking" to "Building the VPN interface…",
    "vpnDenied" to "VPN permission was declined.",
    "vpnConflict" to "Android allows one VPN at a time. Turn off any other VPN first.",
    "vpnScope" to "Only 100.64.0.0/10 enters the tunnel; the rest of your traffic is untouched.",
    "vpnInterface" to "System interface",
    "restartServer" to "Restart this session",
    "restartHint" to "The same workflow run is re-run with the same id: no second server, no new password.",
    "restarting" to "Restarting…",
    "remoteConnecting" to "Starting the Windows session…",
    "remoteHandshake" to "NLA sign-in and first frame",
    "exitFullscreen" to "Exit fullscreen",
    "brandTagline" to "A Windows desktop on your own tailnet",
    "errVpnTitle" to "The VPN interface could not be created",
    "errVpnHint" to "Turn off the other VPN or grant the permission again; the app falls back to its in-app tunnel.",
    "peersTitle" to "Tailnet devices",
    "peersEmpty" to "No devices visible yet",
    "errCryptoTitle" to "The encryption engine could not load on this device",
    "errCryptoHint" to "Uninstall and reinstall this build; if it repeats, send the log.",
    "logsTitle" to "Logs",
    "logsSubtitle" to "Everything the app did, with exact times",
    "logsEmpty" to "No log entries yet",
    "logsEmptyHint" to "Every connection attempt fills this list.",
    "logsAll" to "All",
    "logsErrors" to "Errors",
    "logsWarnings" to "Warnings",
    "logsNetwork" to "Network",
    "logsGithub" to "GitHub",
    "logsRdp" to "Desktop",
    "logsSystem" to "System",
    "logsCopy" to "Copy log",
    "logsCopied" to "Log copied",
    "logsClear" to "Clear",
    "logsCleared" to "Log cleared",
    "logsShare" to "Export",
    "logsSearchHint" to "Search the log",
    "logsDetail" to "Technical detail",
    "logsEnvironment" to "Environment",
    "logsCountLabel" to "entries",
    "viewLogs" to "View log",
    "diagnosticsSection" to "Logs & diagnostics",
    "diagnosticsHint" to "If something fails, the exact error text is here.",
    "lastCrashTitle" to "The previous run ended unexpectedly",
    "lastCrashHint" to "The recorded reason is in the logs.",
    "logsLiveOn" to "Live updates",
    "logsExpandAll" to "Show details",
    "errNetlinkTitle" to "Android refused to read the network interfaces",
    "errNetlinkHint" to "The new build uses the permitted path; close the app and open it again.",
    "errLogDirTitle" to "No writable folder was found for Tailscale files",
    "errLogDirHint" to "App storage is full or restricted; free some space and retry.",
    "errPanicTitle" to "The network engine hit an internal error",
    "errPanicHint" to "The app stayed alive; the full text is in the logs. Copy it and retry.",
    "errAuthKeyTitle" to "The Tailscale key was rejected",
    "errAuthKeyHint" to "It must be a fresh, unexpired tskey-auth key; create a new one.",
    "errKeyExpiredTitle" to "The Tailscale key has expired",
    "errKeyExpiredHint" to "In the Keys panel create a Reusable key valid for at least 90 days.",
    "errOfflineTitle" to "Internet or DNS is not working",
    "errOfflineHint" to "Check the phone's network; another VPN can block the tunnel.",
    "errTimeoutTitle" to "The connection did not finish in time",
    "errTimeoutHint" to "Usually an invalid key or a slow network; check the last stage in the log and retry.",
    "errTlsTitle" to "The security certificate was not trusted",
    "errTlsHint" to "Set the phone's date to automatic and try another network.",
    "errRefusedTitle" to "The server refused the connection",
    "errRefusedHint" to "Windows is still booting or RDP is not ready; retry in a few minutes.",
    "errGithubAuthTitle" to "The GitHub token is not valid",
    "errGithubAuthHint" to "Create the token again; it may be revoked or expired.",
    "errGithubScopeTitle" to "The token lacks the required permissions",
    "errGithubScopeHint" to "repo and workflow are required; use the app's prefilled link to make a new token.",
    "errGithubRateTitle" to "GitHub temporarily limited the requests",
    "errGithubRateHint" to "Wait a minute and retry.",
    "errStorageTitle" to "The app's secure storage is unavailable",
    "errStorageHint" to "Free space, or close and reopen the app once.",
    "errRdpTitle" to "The desktop connection failed",
    "errRdpHint" to "Recheck the address and password; a rebuilt server has a new password.",
    "errGenericTitle" to "The operation failed",
    "errGenericHint" to "The exact error text is recorded in the logs.",
    "appName" to "RemoteHub",
    "navDashboard" to "Dashboard",
    "navServers" to "Servers",
    "navNetwork" to "Network",
    "navSettings" to "Settings",
    "setupTitle" to "Setup",
    "setupSubtitle" to "Complete these two connections to create a server.",
    "continueSetup" to "Continue setup",
    "githubAccount" to "GitHub account",
    "privateNetwork" to "Private network",
    "remoteProvider" to "Remote provider",
    "connected" to "Connected",
    "notConnected" to "Not connected",
    "online" to "Online",
    "offline" to "Offline",
    "activeServers" to "Active servers",
    "timeRemaining" to "Time remaining",
    "minutesUsed" to "Minutes used",
    "yourServers" to "Your servers",
    "newServer" to "New server",
    "connect" to "Connect",
    "stop" to "Stop",
    "restart" to "Restart",
    "rebuild" to "Rebuild",
    "details" to "Details",
    "stateProvisioning" to "Provisioning",
    "stateReady" to "Ready",
    "stateConnected" to "Connected",
    "stateExpiring" to "Expiring soon",
    "stateStopping" to "Stopping",
    "stateStopped" to "Stopped",
    "stateFailed" to "Failed",
    "emptyTitle" to "No servers yet",
    "emptyBody" to "Use New server to provision your first remote environment.",
    "quotaTitle" to "Quota and limits",
    "concurrentJobs" to "Concurrent jobs",
    "jobMaxDuration" to "Max job duration",
    "complianceNotice" to "Remote infrastructure runs on a permitted provider. GitHub Actions is used only for build, test and release.",
    "githubTitle" to "Connect GitHub",
    "githubSubtitle" to "Official sign-in, no password sharing.",
    "tabDevice" to "Device code",
    "tabPat" to "Personal token",
    "deviceInstruction" to "Enter this code on the official GitHub page to authorize this device.",
    "yourCode" to "Your code",
    "copyCode" to "Copy code",
    "openGithub" to "Open GitHub",
    "waitingApproval" to "Waiting for your approval",
    "expiresInSeconds" to "Expires in",
    "patLabel" to "Personal access token",
    "patPlaceholder" to "github_pat_…",
    "requiredPermissions" to "Required permissions",
    "grantedPermissions" to "Granted permissions",
    "missingPermissions" to "Missing permissions",
    "validateAndSave" to "Validate and save",
    "storedSecurely" to "Tokens are kept in Android Keystore and Windows DPAPI.",
    "connectedAs" to "Connected as",
    "disconnect" to "Disconnect",
    "recommended" to "Recommended",
    "deviceFlowHint" to "No manual token creation. Best for most users.",
    "findingAddressHint" to "I retry every few seconds. Keep the private network connected.",
    "progressSubtitle" to "This takes a few minutes.",
    "remoteDesktop" to "Remote desktop",
    "sessionDetail" to "Server details",
    "backup" to "Encrypted backup",
    "connectRemote" to "Remote desktop in app",
    "expiringBanner" to "This server is running out of time. Back up anything important now.",
    "expiryAlertHint" to "I will warn you before the time runs out.",
    "findingAddress" to "Finding the server address on the private network…",
    "githubStep1" to "Tap the link below. The token page opens with repo and workflow already ticked.",
    "githubStep2" to "Pick an Expiration, then press Generate token.",
    "githubStep3" to "Copy the token, come back and paste it in the box, then press Connect.",
    "guideGithub" to "Connecting GitHub",
    "guideRdp" to "Using remote desktop",
    "guideSecurity" to "Security and privacy",
    "guideTailscale" to "Tailscale private network",
    "hidePassword" to "Hide password",
    "language" to "Language",
    "networkNote" to "The key must be Reusable and Ephemeral with at least 90 days of validity, otherwise later sessions cannot join.",
    "notificationsTitle" to "Notifications",
    "openWorkflowRun" to "View the workflow run on GitHub",
    "privacyNote" to "Your token and key stay in this phone's encrypted storage and are never printed in logs.",
    "rdpGuide1" to "On the details page press Remote desktop. The full-screen picture opens inside this app.",
    "rdpGuide2" to "One finger moves the pointer, tap clicks, two fingers scroll. Switch trackpad mode from the top bar.",
    "rdpGuide3" to "The keyboard button opens the phone keyboard, and the clipboard button sends copied text to the server.",
    "rerun" to "Run again",
    "rerunNote" to "Running again builds a fresh machine: the address and password change and the old machine's data is gone.",
    "riskNote" to "The machine runs on a GitHub runner. This does not match GitHub's terms of use and your account may be restricted.",
    "scopesNote" to "repo is needed to create the repository and encrypt secrets; workflow is needed to write the workflow file.",
    "security1" to "Each session password is random, 24 characters long, and never printed in the public log.",
    "security2" to "The remote port is open only from the Tailscale private network, not from the internet.",
    "security3" to "Network Level Authentication stays on and the runner's default account is left untouched.",
    "security4" to "When the session ends the machine leaves the private network and is wiped completely.",
    "showPassword" to "Show password",
    "stopServer" to "Stop server",
    "stoppedBanner" to "This session has ended. Press Run again to start a new one.",
    "tailscaleStep1" to "Create a free Tailscale account on the website. Then tap the three lines at the top and open the Settings menu.",
    "tailscaleStep2" to "In Settings open Keys. Press Generate auth key, give the key a name, and turn on Ephemeral and Reusable.",
    "tailscaleStep3" to "Set the key expiry to at least 90 days, copy the key and paste it in the box on this page.",
    "setupGithubTitle" to "Connect your GitHub account",
    "setupGithubHint" to "A personal token is required",
    "setupGithubWhy" to "With this token the app creates the repository and runs the workflow.",
    "setupMakeToken" to "Create the token on GitHub",
    "setupTokenStep1" to "Open the link above; the permissions are already ticked.",
    "setupTokenStep2" to "Scroll down and press Generate token.",
    "setupTokenStep3" to "Copy the token and paste it below.",
    "setupPasteToken" to "Paste the token here",
    "setupNetworkTitle" to "Private network key (Tailscale)",
    "setupNetworkHint" to "For a secure tunnel between phone and server",
    "setupNetworkWhy" to "It is only a secure tunnel; no separate client is needed on the phone.",
    "setupOpenKeys" to "Open the key page",
    "setupKeyStep1" to "After creating your Tailscale account, tap the three lines at the top and open the Settings menu.",
    "setupKeyStep2" to "Open Keys, give the key a name, turn on Ephemeral and Reusable, and set the expiry to at least 90 days.",
    "setupKeyStep3" to "Copy the generated key, paste it in the box below and press Save and connect.",
    "setupPasteKey" to "Paste the key here",
    "setupSaveConnect" to "Save and connect",
    "setupNetworkDone" to "Private network connected",
    "setupCreateServer" to "Create server",
    "setupMissingGithub" to "Connect your GitHub account first.",
    "setupMissingNetwork" to "Enter the private network key.",
    "setupRiskNote" to "This uses a GitHub runner as a temporary Windows desktop. It does not comply with the Actions terms and your account may be restricted.",
    "scopeMissing" to "The repo and workflow scopes are required. Create the token again with the link above.",
    "working" to "Working…",
    "retry" to "Try again",
    "openServer" to "Open server",
    "createSubtitle" to "Pick a name and a lifetime. The rest is automatic.",
    "createNameLabel" to "Server name",
    "createNameHint" to "English letters and digits, e.g. windows-1",
    "createDuration" to "Session lifetime",
    "createDurationHint" to "The machine shuts down after this time.",
    "createDurationMax" to "Maximum 6 hours — GitHub's per-job ceiling.",
    "createWhatHappens" to "What happens",
    "createBulletRepo" to "A repository is created in your own account.",
    "createBulletWindows" to "Windows Server starts on a GitHub runner.",
    "createBulletTunnel" to "The server joins your private network.",
    "createBulletPassword" to "The password stays on your phone and is never printed in the log.",
    "createEphemeralNote" to "This machine is temporary. When the session ends every file is gone and the next session gets a new address.",
    "createPublicNote" to "The repository is public so minutes stay free. Secrets are never logged, but the server address is visible.",
    "createStart" to "Start building",
    "progressTitle" to "Building",
    "progressRunning" to "Usually takes 3 to 6 minutes.",
    "progressDone" to "The server is ready.",
    "progressFailed" to "Building stopped.",
    "progressStarting" to "Starting…",
    "progressLog" to "Log",
    "progressOpenServer" to "See server details",
    "stepAccount" to "Checking GitHub account",
    "stepRepo" to "Creating the repository",
    "stepMachine" to "Preparing the Windows machine",
    "stepNetwork" to "Joining the private network",
    "oauthClientId" to "OAuth App client ID",
    "oauthClientIdHint" to "Create an OAuth App under GitHub → Settings → Developer settings, enable Device Flow, then paste its Client ID here. No app yet? Use the personal token tab instead.",
    "patHint" to "For organization accounts or single-repository scoped access.",
    "networkTitle" to "Private network",
    "networkSubtitle" to "Tailscale runs inside the app. No separate client to install.",
    "embeddedNode" to "Embedded node",
    "userspaceMode" to "Userspace",
    "noRootRequired" to "No root",
    "vpnCoexistence" to "Coexists with VPN",
    "tailnet" to "Tailnet",
    "nodeName" to "Node name",
    "nodeAddress" to "Node address",
    "relay" to "Relay",
    "latency" to "Latency",
    "transport" to "Path",
    "connectNetwork" to "Connect network",
    "disconnectNetwork" to "Disconnect network",
    "authKey" to "Auth key",
    "authKeyHint" to "Short-lived ephemeral keys are minted through the official API.",
    "officialSignIn" to "Official sign-in",
    "openBrowser" to "Open browser",
    "createTitle" to "New server",
    "stepLabel" to "Step",
    "ofLabel" to "of",
    "stepTarget" to "Target",
    "stepConfig" to "Configuration",
    "stepReview" to "Review",
    "provider" to "Provider",
    "providerHint" to "The target must permit remote desktop access.",
    "region" to "Region",
    "osImage" to "OS image",
    "machineSize" to "Machine size",
    "serverName" to "Server name",
    "expiryWarning" to "Expiry warning",
    "minutesBefore" to "minutes before expiry",
    "autoBackup" to "Automatic backup",
    "backupDestination" to "Backup destination",
    "sameRepo" to "Same repository",
    "clientSideEncrypted" to "Client-side encrypted",
    "publicRepoWarning" to "The repository is public. Backups are encrypted before upload and the key is never uploaded.",
    "back" to "Back",
    "next" to "Next",
    "createServer" to "Create server",
    "provisioning" to "Provisioning",
    "liveProgress" to "Live progress from the GitHub API",
    "cancel" to "Cancel",
    "stepAccountCheck" to "Verify GitHub account",
    "stepRepoEnsure" to "Ensure repository",
    "stepSecrets" to "Store encrypted secrets",
    "stepDispatch" to "Dispatch workflow",
    "stepRunner" to "Read run status",
    "stepJoin" to "Join private network",
    "stepHandshake" to "Retrieve connection info",
    "stepReady" to "Ready to hand over",
    "running" to "Running",
    "done" to "Done",
    "pending" to "Pending",
    "failed" to "Failed",
    "connectionInfo" to "Connection details",
    "address" to "Address",
    "port" to "Port",
    "username" to "Username",
    "password" to "Password",
    "copy" to "Copy",
    "show" to "Show",
    "hide" to "Hide",
    "fingerprint" to "Certificate fingerprint",
    "openRemote" to "Remote desktop",
    "lifecycle" to "Lifecycle",
    "rerunAction" to "Re-run action",
    "uptime" to "Uptime",
    "warningBanner" to "This session is about to expire. Take a backup and re-run to continue.",
    "specifications" to "Specifications",
    "cores" to "Cores",
    "memory" to "Memory",
    "disk" to "Disk",
    "operatingSystem" to "Operating system",
    "backups" to "Backups",
    "createBackup" to "Create backup",
    "restore" to "Restore",
    "encrypted" to "Encrypted",
    "history" to "Operation history",
    "workflowRun" to "Workflow run",
    "openInGithub" to "Open in GitHub",
    "repository" to "Repository",
    "lastError" to "Last error",
    "remoteTitle" to "Remote desktop",
    "connectedTo" to "Connected to",
    "bandwidth" to "Bandwidth",
    "keyboard" to "Keyboard",
    "clipboard" to "Clipboard",
    "ctrlAltDel" to "Ctrl+Alt+Del",
    "rightClick" to "Right click",
    "fullscreen" to "Fullscreen",
    "touchMode" to "Direct touch",
    "trackpadMode" to "Trackpad",
    "quality" to "Quality",
    "disconnectSession" to "Disconnect",
    "nativeEngine" to "Native FreeRDP engine",
    "secureChannel" to "Encrypted channel over Tailscale",
    "settingsTitle" to "Settings",
    "appearance" to "Appearance",
    "theme" to "Theme",
    "themeSystem" to "System",
    "themeLight" to "Light",
    "themeDark" to "Dark",
    "languageLabel" to "Language",
    "notifications" to "Notifications",
    "expiryAlert" to "Expiry alert",
    "alertThreshold" to "Alert threshold",
    "security" to "Security",
    "biometricLock" to "Biometric lock",
    "clearCredentials" to "Clear credentials",
    "storageEncryption" to "Storage encryption",
    "guides" to "Guides",
    "gettingStarted" to "Getting started",
    "troubleshooting" to "Troubleshooting",
    "policyAndLimits" to "Policies and limits",
    "about" to "About",
    "version" to "Version",
    "seconds" to "seconds",
    "minutes" to "minutes",
))

val Arabic = AppStrings("ar", true, mapOf(
    "serversTitle" to "الخوادم",
    "serversSubtitle" to "كل الجلسات التي أنشأتها",
    "serversEmpty" to "لا توجد خوادم بعد",
    "serversEmptyHint" to "أنشئ خادمًا من الصفحة الرئيسية ليظهر هنا.",
    "networkScreenTitle" to "الشبكة الخاصة",
    "networkScreenSubtitle" to "حالة النفق ووصول الجهاز بالكامل",
    "deviceAccess" to "وصول الجهاز بالكامل",
    "deviceAccessHint" to "عند تشغيل هذا الخيار يتحول النفق إلى VPN حقيقي في أندرويد، ليصل أي تطبيق (مثل aRDP) إلى عناوين 100.x.",
    "vpnOn" to "مُشغّل",
    "vpnOff" to "متوقف",
    "vpnEnable" to "تشغيل VPN الجهاز",
    "vpnDisable" to "إيقاف VPN الجهاز",
    "vpnWorking" to "جارٍ إنشاء واجهة VPN…",
    "vpnDenied" to "تم رفض إذن VPN.",
    "vpnConflict" to "يسمح أندرويد بـ VPN واحد فقط في الوقت نفسه. أوقف أي VPN آخر أولًا.",
    "vpnScope" to "يدخل النفقَ النطاقُ 100.64.0.0/10 فقط؛ وتبقى بقية حركة البيانات كما هي.",
    "vpnInterface" to "واجهة النظام",
    "restartServer" to "إعادة تشغيل الجلسة نفسها",
    "restartHint" to "تُعاد تشغيل نفس جلسة سير العمل بالمعرّف نفسه: لا خادم ثانٍ ولا كلمة مرور جديدة.",
    "restarting" to "جارٍ إعادة التشغيل…",
    "remoteConnecting" to "جارٍ بدء جلسة ويندوز…",
    "remoteHandshake" to "تسجيل دخول NLA واستلام أول إطار",
    "exitFullscreen" to "الخروج من ملء الشاشة",
    "brandTagline" to "سطح مكتب ويندوز على شبكتك الخاصة",
    "errVpnTitle" to "تعذّر إنشاء واجهة VPN",
    "errVpnHint" to "أوقف VPN الآخر أو امنح الإذن مرة أخرى؛ سيعود التطبيق إلى النفق الداخلي.",
    "peersTitle" to "أجهزة الشبكة",
    "peersEmpty" to "لا توجد أجهزة مرئية بعد",
    "errCryptoTitle" to "تعذّر تحميل محرك التشفير على هذا الجهاز",
    "errCryptoHint" to "احذف هذا الإصدار وثبّته من جديد؛ إذا تكرر الأمر فأرسل السجل.",
    "logsTitle" to "السجلات",
    "logsSubtitle" to "كل ما حدث في التطبيق مع الوقت الدقيق",
    "logsEmpty" to "لا توجد سجلات بعد",
    "logsEmptyHint" to "كل محاولة اتصال تملأ هذه القائمة.",
    "logsAll" to "الكل",
    "logsErrors" to "الأخطاء",
    "logsWarnings" to "التحذيرات",
    "logsNetwork" to "الشبكة",
    "logsGithub" to "GitHub",
    "logsRdp" to "سطح المكتب",
    "logsSystem" to "النظام",
    "logsCopy" to "نسخ السجل",
    "logsCopied" to "تم نسخ السجل",
    "logsClear" to "مسح",
    "logsCleared" to "تم مسح السجل",
    "logsShare" to "تصدير",
    "logsSearchHint" to "ابحث في السجل",
    "logsDetail" to "التفاصيل التقنية",
    "logsEnvironment" to "البيئة",
    "logsCountLabel" to "سطر",
    "viewLogs" to "عرض السجل",
    "diagnosticsSection" to "السجلات والتشخيص",
    "diagnosticsHint" to "إذا فشل شيء، ستجد نص الخطأ الدقيق هنا.",
    "lastCrashTitle" to "أُغلق التشغيل السابق بشكل غير متوقع",
    "lastCrashHint" to "السبب المسجّل موجود في السجلات.",
    "logsLiveOn" to "تحديث مباشر",
    "logsExpandAll" to "إظهار التفاصيل",
    "errNetlinkTitle" to "رفض أندرويد قراءة واجهات الشبكة",
    "errNetlinkHint" to "يستخدم الإصدار الجديد المسار المسموح؛ أغلق التطبيق وافتحه مجددًا.",
    "errLogDirTitle" to "لم يتم العثور على مجلد قابل للكتابة لملفات Tailscale",
    "errLogDirHint" to "مساحة التطبيق ممتلئة أو مقيّدة؛ حرّر مساحة وأعد المحاولة.",
    "errPanicTitle" to "حدث خطأ داخلي في محرك الشبكة",
    "errPanicHint" to "لم يُغلق التطبيق؛ النص الكامل في السجلات. انسخه وأعد المحاولة.",
    "errAuthKeyTitle" to "لم يتم قبول مفتاح Tailscale",
    "errAuthKeyHint" to "يجب أن يكون مفتاح tskey-auth جديدًا وغير منتهٍ؛ أنشئ مفتاحًا جديدًا.",
    "errKeyExpiredTitle" to "انتهت صلاحية مفتاح Tailscale",
    "errKeyExpiredHint" to "أنشئ مفتاحًا قابلًا لإعادة الاستخدام لمدة ٩٠ يومًا على الأقل.",
    "errOfflineTitle" to "الإنترنت أو DNS لا يعمل",
    "errOfflineHint" to "تحقق من شبكة الهاتف؛ قد يمنع VPN آخر الاتصال.",
    "errTimeoutTitle" to "لم يكتمل الاتصال في الوقت المحدد",
    "errTimeoutHint" to "عادةً مفتاح غير صالح أو شبكة بطيئة؛ راجع آخر مرحلة في السجل وأعد المحاولة.",
    "errTlsTitle" to "لم يتم التحقق من الشهادة الأمنية",
    "errTlsHint" to "اضبط تاريخ الهاتف تلقائيًا وجرّب شبكة أخرى.",
    "errRefusedTitle" to "رفض الخادم الاتصال",
    "errRefusedHint" to "ما زال ويندوز يقلع أو RDP غير جاهز؛ أعد المحاولة بعد دقائق.",
    "errGithubAuthTitle" to "رمز GitHub غير صالح",
    "errGithubAuthHint" to "أنشئ الرمز مجددًا؛ ربما أُلغي أو انتهت صلاحيته.",
    "errGithubScopeTitle" to "الرمز لا يملك الصلاحيات المطلوبة",
    "errGithubScopeHint" to "مطلوب صلاحيتا repo وworkflow؛ استخدم الرابط الجاهز لإنشاء رمز جديد.",
    "errGithubRateTitle" to "قيّد GitHub الطلبات مؤقتًا",
    "errGithubRateHint" to "انتظر دقيقة وأعد المحاولة.",
    "errStorageTitle" to "التخزين الآمن للتطبيق غير متاح",
    "errStorageHint" to "حرّر مساحة أو أغلق التطبيق وافتحه مرة أخرى.",
    "errRdpTitle" to "فشل الاتصال بسطح المكتب",
    "errRdpHint" to "تحقق من العنوان وكلمة المرور؛ للخادم المعاد إنشاؤه كلمة مرور جديدة.",
    "errGenericTitle" to "فشلت العملية",
    "errGenericHint" to "تم تسجيل نص الخطأ الدقيق في السجلات.",
    "appName" to "RemoteHub",
    "navDashboard" to "لوحة التحكم",
    "navServers" to "الخوادم",
    "navNetwork" to "الشبكة",
    "navSettings" to "الإعدادات",
    "setupTitle" to "الإعداد",
    "setupSubtitle" to "أكمل هذين الاتصالين لإنشاء خادم.",
    "continueSetup" to "متابعة الإعداد",
    "githubAccount" to "حساب GitHub",
    "privateNetwork" to "شبكة خاصة",
    "remoteProvider" to "مزود الوصول",
    "connected" to "متصل",
    "notConnected" to "غير متصل",
    "online" to "متصل",
    "offline" to "غير متصل",
    "activeServers" to "خوادم نشطة",
    "timeRemaining" to "الوقت المتبقي",
    "minutesUsed" to "الدقائق المستخدمة",
    "yourServers" to "خوادمك",
    "newServer" to "خادم جديد",
    "connect" to "اتصال",
    "stop" to "إيقاف",
    "restart" to "إعادة تشغيل",
    "rebuild" to "إعادة بناء",
    "details" to "التفاصيل",
    "stateProvisioning" to "قيد التجهيز",
    "stateReady" to "جاهز",
    "stateConnected" to "متصل",
    "stateExpiring" to "قارب على الانتهاء",
    "stateStopping" to "قيد الإيقاف",
    "stateStopped" to "متوقف",
    "stateFailed" to "خطأ",
    "emptyTitle" to "لا خوادم بعد",
    "emptyBody" to "استخدم خادم جديد لتجهيز أول بيئة بعيدة.",
    "quotaTitle" to "الحصص والحدود",
    "concurrentJobs" to "مهام متزامنة",
    "jobMaxDuration" to "أقصى مدة للمهمة",
    "complianceNotice" to "تعمل البنية البعيدة على مزود مسموح. يُستخدم GitHub Actions للبناء والاختبار والإصدار فقط.",
    "githubTitle" to "ربط GitHub",
    "githubSubtitle" to "تسجيل دخول رسمي بدون مشاركة كلمة المرور.",
    "tabDevice" to "رمز الجهاز",
    "tabPat" to "رمز شخصي",
    "deviceInstruction" to "أدخل هذا الرمز في صفحة GitHub الرسمية للسماح لهذا الجهاز.",
    "yourCode" to "رمزك",
    "copyCode" to "نسخ الرمز",
    "openGithub" to "فتح GitHub",
    "waitingApproval" to "بانتظار موافقتك",
    "expiresInSeconds" to "ينتهي خلال",
    "patLabel" to "رمز وصول شخصي",
    "patPlaceholder" to "github_pat_…",
    "requiredPermissions" to "الأذونات المطلوبة",
    "grantedPermissions" to "الأذونات الممنوحة",
    "missingPermissions" to "الأذونات الناقصة",
    "validateAndSave" to "تحقق واحفظ",
    "storedSecurely" to "تُحفظ الرموز في Android Keystore و Windows DPAPI.",
    "connectedAs" to "متصل باسم",
    "disconnect" to "قطع الاتصال",
    "recommended" to "مستحسن",
    "deviceFlowHint" to "بدون إنشاء رمز يدوي. مناسب لمعظم المستخدمين.",
    "findingAddressHint" to "أعيد المحاولة كل بضع ثوانٍ. أبقِ الشبكة الخاصة متصلة.",
    "progressSubtitle" to "يستغرق هذا بضع دقائق.",
    "remoteDesktop" to "سطح المكتب البعيد",
    "sessionDetail" to "تفاصيل الخادم",
    "backup" to "نسخة مشفرة",
    "connectRemote" to "سطح المكتب داخل التطبيق",
    "expiringBanner" to "وقت الخادم ينفد. احفظ نسخة احتياطية الآن.",
    "expiryAlertHint" to "سأنبهك قبل انتهاء الوقت.",
    "findingAddress" to "جارٍ البحث عن عنوان الخادم…",
    "githubStep1" to "اضغط الرابط أدناه. تُفتح صفحة الرمز مع صلاحيتي repo و workflow محدّدتين.",
    "githubStep2" to "اختر مدة في Expiration ثم اضغط Generate token.",
    "githubStep3" to "انسخ الرمز، عد والصقه في الحقل ثم اضغط اتصال.",
    "guideGithub" to "ربط GitHub",
    "guideRdp" to "استخدام سطح المكتب البعيد",
    "guideSecurity" to "الأمان والخصوصية",
    "guideTailscale" to "شبكة Tailscale الخاصة",
    "hidePassword" to "إخفاء كلمة المرور",
    "language" to "اللغة",
    "networkNote" to "يجب أن يكون المفتاح Reusable و Ephemeral وبصلاحية 90 يوماً على الأقل، وإلا لن تتصل الجلسات التالية.",
    "notificationsTitle" to "الإشعارات",
    "openWorkflowRun" to "عرض تشغيل الإجراء على GitHub",
    "privacyNote" to "يبقى الرمز والمفتاح في تخزين هذا الهاتف المشفر ولا يُطبعان في السجلات.",
    "rdpGuide1" to "في صفحة التفاصيل اضغط سطح المكتب البعيد. تُفتح الصورة بملء الشاشة داخل التطبيق.",
    "rdpGuide2" to "إصبع واحد يحرك المؤشر، النقر يضغط، إصبعان للتمرير. بدّل وضع لوحة اللمس من الشريط العلوي.",
    "rdpGuide3" to "زر لوحة المفاتيح يفتح لوحة الهاتف، وزر الحافظة يرسل النص المنسوخ إلى الخادم.",
    "rerun" to "تشغيل مجدد",
    "rerunNote" to "التشغيل مجدداً ينشئ جهازاً جديداً: يتغير العنوان وكلمة المرور وتُمحى بيانات الجهاز السابق.",
    "riskNote" to "يعمل الجهاز على مُشغّل GitHub. هذا لا يوافق شروط استخدام GitHub وقد يُقيَّد حسابك.",
    "scopesNote" to "صلاحية repo لإنشاء المستودع وتشفير الأسرار؛ وworkflow لكتابة ملف الإجراء.",
    "security1" to "كلمة مرور كل جلسة عشوائية بطول 24 حرفاً ولا تُطبع في السجل العام.",
    "security2" to "منفذ سطح المكتب مفتوح فقط من شبكة Tailscale الخاصة، لا من الإنترنت.",
    "security3" to "تبقى مصادقة مستوى الشبكة مفعّلة ولا يُمس حساب المشغّل الافتراضي.",
    "security4" to "عند انتهاء الجلسة يغادر الجهاز الشبكة الخاصة ويُمحى بالكامل.",
    "showPassword" to "إظهار كلمة المرور",
    "stopServer" to "إيقاف الخادم",
    "stoppedBanner" to "انتهت هذه الجلسة. اضغط تشغيل مجدداً لبدء جلسة جديدة.",
    "tailscaleStep1" to "أنشئ حساب Tailscale مجانياً على الموقع. ثم اضغط الخطوط الثلاثة في الأعلى وافتح قائمة Settings.",
    "tailscaleStep2" to "في Settings افتح Keys. اضغط Generate auth key، اختر اسماً للمفتاح وفعّل Ephemeral و Reusable.",
    "tailscaleStep3" to "اجعل صلاحية المفتاح 90 يوماً على الأقل، انسخه والصقه في الحقل بهذه الصفحة.",
    "setupGithubTitle" to "ربط حساب GitHub",
    "setupGithubHint" to "يلزم رمز شخصي",
    "setupGithubWhy" to "بهذا الرمز ينشئ التطبيق المستودع ويشغّل سير العمل.",
    "setupMakeToken" to "إنشاء الرمز على GitHub",
    "setupTokenStep1" to "افتح الرابط أعلاه؛ الصلاحيات محددة مسبقاً.",
    "setupTokenStep2" to "في أسفل الصفحة اضغط Generate token.",
    "setupTokenStep3" to "انسخ الرمز والصقه في الحقل أدناه.",
    "setupPasteToken" to "الصق الرمز هنا",
    "setupNetworkTitle" to "مفتاح الشبكة الخاصة (Tailscale)",
    "setupNetworkHint" to "لنفق آمن بين الهاتف والخادم",
    "setupNetworkWhy" to "إنه نفق آمن فقط؛ لا حاجة لعميل منفصل على الهاتف.",
    "setupOpenKeys" to "فتح صفحة إنشاء المفتاح",
    "setupKeyStep1" to "بعد إنشاء حساب Tailscale، اضغط الخطوط الثلاثة في الأعلى وافتح قائمة Settings.",
    "setupKeyStep2" to "افتح Keys، اختر اسماً للمفتاح، فعّل Ephemeral و Reusable، واجعل الصلاحية 90 يوماً على الأقل.",
    "setupKeyStep3" to "انسخ المفتاح، الصقه في الحقل أدناه واضغط حفظ واتصال.",
    "setupPasteKey" to "الصق المفتاح هنا",
    "setupSaveConnect" to "حفظ واتصال",
    "setupNetworkDone" to "الشبكة الخاصة متصلة",
    "setupCreateServer" to "إنشاء خادم",
    "setupMissingGithub" to "اربط حساب GitHub أولاً.",
    "setupMissingNetwork" to "أدخل مفتاح الشبكة الخاصة.",
    "setupRiskNote" to "يستخدم هذا روش عدّاء GitHub كسطح مكتب Windows مؤقت. لا يتوافق مع شروط Actions وقد يُقيَّد حسابك.",
    "scopeMissing" to "صلاحيتا repo و workflow مطلوبة. أنشئ الرمز مجدداً من الرابط أعلاه.",
    "working" to "جارٍ العمل…",
    "retry" to "إعادة المحاولة",
    "openServer" to "فتح الخادم",
    "createSubtitle" to "اختر اسماً ومدة. الباقي تلقائي.",
    "createNameLabel" to "اسم الخادم",
    "createNameHint" to "أحرف إنجليزية وأرقام، مثل windows-1",
    "createDuration" to "مدة الجلسة",
    "createDurationHint" to "تُغلق الآلة بعد هذه المدة.",
    "createDurationMax" to "الحد الأقصى ٦ ساعات — سقف كل مهمة في GitHub.",
    "createWhatHappens" to "ما الذي سيحدث",
    "createBulletRepo" to "يُنشأ مستودع في حسابك.",
    "createBulletWindows" to "يبدأ Windows Server على عدّاء GitHub.",
    "createBulletTunnel" to "ينضم الخادم إلى شبكتك الخاصة.",
    "createBulletPassword" to "يبقى الرمز على هاتفك ولا يُطبع في السجل.",
    "createEphemeralNote" to "هذه الآلة مؤقتة. عند انتهاء الجلسة تُحذف كل الملفات وتحصل الجلسة التالية على عنوان جديد.",
    "createPublicNote" to "المستودع عام لتبقى الدقائق مجانية. لا تُسجَّل الأسرار، لكن عنوان الخادم يكون ظاهراً.",
    "createStart" to "بدء الإنشاء",
    "progressTitle" to "جارٍ الإنشاء",
    "progressRunning" to "يستغرق عادة من ٣ إلى  دقائق.",
    "progressDone" to "الخادم جاهز.",
    "progressFailed" to "توقف الإنشاء.",
    "progressStarting" to "جارٍ البدء…",
    "progressLog" to "السجل",
    "progressOpenServer" to "عرض تفاصيل الخادم",
    "stepAccount" to "التحقق من حساب GitHub",
    "stepRepo" to "إنشاء المستودع",
    "stepMachine" to "تهيئة آلة Windows",
    "stepNetwork" to "الاتصال بالشبكة الخاصة",
    "oauthClientId" to "معرّف تطبيق OAuth",
    "oauthClientIdHint" to "أنشئ تطبيق OAuth من GitHub ثم الإعدادات ثم إعدادات المطوّر، فعّل Device Flow، والصق معرّف العميل هنا. لا تملك تطبيقاً؟ استخدم تبويب الرمز الشخصي.",
    "patHint" to "لحسابات المؤسسات أو الوصول المحدود بمستودع واحد.",
    "networkTitle" to "شبكة خاصة",
    "networkSubtitle" to "يعمل Tailscale داخل التطبيق. لا حاجة لعميل منفصل.",
    "embeddedNode" to "عقدة مدمجة",
    "userspaceMode" to "Userspace",
    "noRootRequired" to "بدون Root",
    "vpnCoexistence" to "يتوافق مع VPN",
    "tailnet" to "Tailnet",
    "nodeName" to "اسم العقدة",
    "nodeAddress" to "عنوان العقدة",
    "relay" to "المرحّل",
    "latency" to "التأخير",
    "transport" to "المسار",
    "connectNetwork" to "ربط الشبكة",
    "disconnectNetwork" to "قطع الشبكة",
    "authKey" to "مفتاح المصادقة",
    "authKeyHint" to "تُنشأ مفاتيح قصيرة العمر عبر الواجهة الرسمية.",
    "officialSignIn" to "دخول رسمي",
    "openBrowser" to "فتح المتصفح",
    "createTitle" to "خادم جديد",
    "stepLabel" to "الخطوة",
    "ofLabel" to "من",
    "stepTarget" to "الهدف",
    "stepConfig" to "التهيئة",
    "stepReview" to "المراجعة",
    "provider" to "المزود",
    "providerHint" to "يجب أن يسمح الهدف بالوصول البعيد.",
    "region" to "المنطقة",
    "osImage" to "صورة النظام",
    "machineSize" to "حجم الجهاز",
    "serverName" to "اسم الخادم",
    "expiryWarning" to "تنبيه الانتهاء",
    "minutesBefore" to "دقيقة قبل الانتهاء",
    "autoBackup" to "نسخ احتياطي تلقائي",
    "backupDestination" to "وجهة النسخ",
    "sameRepo" to "نفس المستودع",
    "clientSideEncrypted" to "مشفّر على الجهاز",
    "publicRepoWarning" to "المستودع عام. تُشفّر النسخ قبل الرفع ولا يُرفع المفتاح أبدًا.",
    "back" to "رجوع",
    "next" to "التالي",
    "createServer" to "إنشاء الخادم",
    "provisioning" to "قيد التجهيز",
    "liveProgress" to "تقدم حقيقي من GitHub API",
    "cancel" to "إلغاء",
    "stepAccountCheck" to "التحقق من حساب GitHub",
    "stepRepoEnsure" to "التحقق من المستودع",
    "stepSecrets" to "تخزين الأسرار مشفّرة",
    "stepDispatch" to "تشغيل سير العمل",
    "stepRunner" to "قراءة حالة التشغيل",
    "stepJoin" to "الانضمام للشبكة الخاصة",
    "stepHandshake" to "استلام بيانات الاتصال",
    "stepReady" to "جاهز للتسليم",
    "running" to "قيد التنفيذ",
    "done" to "تم",
    "pending" to "بالانتظار",
    "failed" to "فشل",
    "connectionInfo" to "بيانات الاتصال",
    "address" to "العنوان",
    "port" to "المنفذ",
    "username" to "اسم المستخدم",
    "password" to "كلمة المرور",
    "copy" to "نسخ",
    "show" to "إظهار",
    "hide" to "إخفاء",
    "fingerprint" to "بصمة الشهادة",
    "openRemote" to "سطح المكتب البعيد",
    "lifecycle" to "دورة الحياة",
    "rerunAction" to "إعادة تشغيل الإجراء",
    "uptime" to "مدة التشغيل",
    "warningBanner" to "هذه الجلسة قاربت على الانتهاء. خُذ نسخة احتياطية وأعد التشغيل.",
    "specifications" to "المواصفات",
    "cores" to "أنوية",
    "memory" to "الذاكرة",
    "disk" to "القرص",
    "operatingSystem" to "نظام التشغيل",
    "backups" to "النسخ الاحتياطية",
    "createBackup" to "إنشاء نسخة",
    "restore" to "استعادة",
    "encrypted" to "مشفّر",
    "history" to "سجل العمليات",
    "workflowRun" to "تشغيل سير العمل",
    "openInGithub" to "فتح في GitHub",
    "repository" to "المستودع",
    "lastError" to "آخر خطأ",
    "remoteTitle" to "سطح المكتب البعيد",
    "connectedTo" to "متصل بـ",
    "bandwidth" to "النطاق",
    "keyboard" to "لوحة المفاتيح",
    "clipboard" to "الحافظة",
    "ctrlAltDel" to "Ctrl+Alt+Del",
    "rightClick" to "نقر يمين",
    "fullscreen" to "ملء الشاشة",
    "touchMode" to "لمس مباشر",
    "trackpadMode" to "لوحة تتبع",
    "quality" to "الجودة",
    "disconnectSession" to "قطع الجلسة",
    "nativeEngine" to "محرك FreeRDP الأصلي",
    "secureChannel" to "قناة مشفّرة عبر Tailscale",
    "settingsTitle" to "الإعدادات",
    "appearance" to "المظهر",
    "theme" to "السمة",
    "themeSystem" to "النظام",
    "themeLight" to "فاتح",
    "themeDark" to "داكن",
    "languageLabel" to "اللغة",
    "notifications" to "الإشعارات",
    "expiryAlert" to "تنبيه الانتهاء",
    "alertThreshold" to "حد التنبيه",
    "security" to "الأمان",
    "biometricLock" to "قفل بيومتري",
    "clearCredentials" to "مسح بيانات الاعتماد",
    "storageEncryption" to "تشفير التخزين",
    "guides" to "الأدلة",
    "gettingStarted" to "البدء",
    "troubleshooting" to "حل المشكلات",
    "policyAndLimits" to "السياسات والحدود",
    "about" to "حول",
    "version" to "الإصدار",
    "seconds" to "ثانية",
    "minutes" to "دقيقة",
))

val LocalStrings = staticCompositionLocalOf { English }

fun stringsFor(tag: String): AppStrings = when (tag) {
    "fa" -> Persian
    "ar" -> Arabic
    else -> English
}

val availableLocales = listOf("fa" to "فارسی", "en" to "English", "ar" to "العربية")
