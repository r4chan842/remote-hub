package app.remotehub.data

interface SecureStore {
    fun put(key: String, value: String)
    fun get(key: String): String?
    fun remove(key: String)
    fun clear()
}

expect fun createSecureStore(): SecureStore

object StoreKeys {
    const val GITHUB_TOKEN = "github.token"
    const val GITHUB_LOGIN = "github.login"
    const val GITHUB_METHOD = "github.method"
    const val GITHUB_SCOPES = "github.scopes"
    const val TS_AUTH_KEY = "tailscale.authkey"
    const val TS_API_TOKEN = "tailscale.apitoken"
    const val LOCALE = "settings.locale"
    const val THEME = "settings.theme"
    const val WARNING_MINUTES = "settings.warning"
    const val SERVERS = "servers.json"
    const val BACKUP_PASSPHRASE = "backup.passphrase"
}
