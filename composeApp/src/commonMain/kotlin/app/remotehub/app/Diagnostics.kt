package app.remotehub.app

import app.remotehub.i18n.AppStrings

/** Severity of one diagnostic line. */
enum class LogLevel { Debug, Info, Warn, Error }

/**
 * One line of the in-app log. [detail] carries the technical payload (a Go stack,
 * an HTTP body, an ApplicationExitInfo description) and is collapsed by default so
 * the list stays readable while nothing is hidden.
 */
data class LogEntry(
    val seq: Long,
    val timeMillis: Long,
    val level: LogLevel,
    val source: String,
    val message: String,
    val detail: String = "",
)

/**
 * A failure the user can act on: a short title in their own language, the next
 * concrete step, and the untouched technical text for the log screen.
 */
data class AppError(
    val code: String,
    val title: String,
    val hint: String,
    val detail: String,
) {
    /** What a banner shows: the sentence plus the step, never a bare exception. */
    fun banner(): String = if (hint.isBlank()) title else "$title\n$hint"
}

/**
 * Turns raw engine text into a precise, translated explanation.
 *
 * The patterns come from the failures actually observed on Android: the netlink
 * denial on API 30+, logpolicy's panic when no directory is writable, expired or
 * single-use auth keys, and GitHub's scope/rate-limit replies. Matching is done on
 * the raw text because that is all the Go bridge and Ktor give us.
 */
fun classifyError(raw: String, s: AppStrings): AppError {
    val text = raw.trim()
    val lower = text.lowercase()

    fun pair(code: String, title: String, hint: String) = AppError(code, title, hint, text)

    return when {
        lower.contains("netlinkrib") || lower.contains("netlink") && lower.contains("permission denied") ->
            pair("netlink", s.errNetlinkTitle, s.errNetlinkHint)

        lower.contains("no safe place") || lower.contains("logs_dir") ||
            lower.contains("log state") || lower.contains("prepare writable directories") ->
            pair("logdir", s.errLogDirTitle, s.errLogDirHint)

        lower.contains("panic") ->
            pair("panic", s.errPanicTitle, s.errPanicHint)

        lower.contains("key expired") || lower.contains("authkey expired") ||
            lower.contains("expired key") || lower.contains("key is expired") ->
            pair("keyexpired", s.errKeyExpiredTitle, s.errKeyExpiredHint)

        lower.contains("invalid key") || lower.contains("unauthorized") ||
            lower.contains("authentication failed") || lower.contains("bad authkey") ||
            lower.contains("invalid authkey") || lower.contains("401") && lower.contains("tailscale") ->
            pair("authkey", s.errAuthKeyTitle, s.errAuthKeyHint)

        lower.contains("x509") || lower.contains("certificate") || lower.contains("tls handshake") ->
            pair("tls", s.errTlsTitle, s.errTlsHint)

        lower.contains("no such host") || lower.contains("network is unreachable") ||
            lower.contains("dns") && lower.contains("fail") || lower.contains("unable to resolve") ->
            pair("offline", s.errOfflineTitle, s.errOfflineHint)

        lower.contains("deadline exceeded") || lower.contains("timeout") ||
            lower.contains("timed out") || lower.contains("did not reach the running state") ->
            pair("timeout", s.errTimeoutTitle, s.errTimeoutHint)

        lower.contains("connection refused") || lower.contains("connectex") ||
            lower.contains("3389") && lower.contains("refused") ->
            pair("refused", s.errRefusedTitle, s.errRefusedHint)

        lower.contains("github") && (lower.contains("403") || lower.contains("resource not accessible")) ||
            lower.contains("missing scope") || lower.contains("insufficient scope") ->
            pair("ghscope", s.errGithubScopeTitle, s.errGithubScopeHint)

        lower.contains("rate limit") || lower.contains("429") || lower.contains("secondary rate") ->
            pair("ghrate", s.errGithubRateTitle, s.errGithubRateHint)

        lower.contains("bad credentials") || lower.contains("401") ->
            pair("ghauth", s.errGithubAuthTitle, s.errGithubAuthHint)

        lower.contains("keystore") || lower.contains("permission denied") && lower.contains("/data") ||
            lower.contains("read-only file system") || lower.contains("no space left") ->
            pair("storage", s.errStorageTitle, s.errStorageHint)

        lower.contains("rdp") || lower.contains("freerdp") || lower.contains("0x2000") ||
            lower.contains("certificate changed") ->
            pair("rdp", s.errRdpTitle, s.errRdpHint)

        else -> pair("unknown", s.errGenericTitle, s.errGenericHint)
    }
}
