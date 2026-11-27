package app.remotehub.github

import app.remotehub.data.platformHttpEngine
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

sealed interface DevicePoll {
    data object Pending : DevicePoll
    data class SlowDown(val newInterval: Int) : DevicePoll
    data class Granted(val token: String, val scopes: List<String>) : DevicePoll
    data class Failed(val reason: String) : DevicePoll
}

class DeviceFlowClient(
    private val clientId: String,
    engine: HttpClientEngine = platformHttpEngine(),
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val http = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
    }

    suspend fun requestCode(scope: String = "repo workflow"): DeviceCodeResponse {
        val response = http.post("https://github.com/login/device/code") {
            header(HttpHeaders.Accept, "application/json")
            contentType(ContentType.Application.Json)
            setBody(mapOf("client_id" to clientId, "scope" to scope))
        }
        if (response.status.value != 200) {
            throw GitHubException(response.status.value, "device_code_request_failed")
        }
        return response.body()
    }

    suspend fun pollOnce(deviceCode: String): DevicePoll {
        val response = http.post("https://github.com/login/oauth/access_token") {
            header(HttpHeaders.Accept, "application/json")
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "client_id" to clientId,
                    "device_code" to deviceCode,
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                ),
            )
        }
        val payload: DeviceTokenResponse = response.body()
        payload.accessToken?.let { token ->
            val scopes = payload.scope?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            return DevicePoll.Granted(token, scopes)
        }
        return when (payload.error) {
            "authorization_pending" -> DevicePoll.Pending
            "slow_down" -> DevicePoll.SlowDown(10)
            "expired_token" -> DevicePoll.Failed("expired_token")
            "access_denied" -> DevicePoll.Failed("access_denied")
            "incorrect_device_code" -> DevicePoll.Failed("incorrect_device_code")
            null -> DevicePoll.Failed("unknown_error")
            else -> DevicePoll.Failed(payload.error)
        }
    }

    suspend fun awaitToken(
        code: DeviceCodeResponse,
        onTick: (remainingSeconds: Int) -> Unit = {},
    ): DevicePoll {
        var interval = code.interval.coerceAtLeast(5)
        var remaining = code.expiresIn
        while (remaining > 0) {
            onTick(remaining)
            delay(interval * 1000L)
            remaining -= interval
            when (val result = pollOnce(code.deviceCode)) {
                is DevicePoll.Granted -> return result
                is DevicePoll.Failed -> return result
                is DevicePoll.SlowDown -> interval = result.newInterval
                DevicePoll.Pending -> Unit
            }
        }
        return DevicePoll.Failed("expired_token")
    }

    fun close() = http.close()
}
