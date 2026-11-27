package app.remotehub.net

import app.remotehub.data.platformHttpEngine
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.basicAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class KeyCapabilities(val devices: DeviceCapabilities)

@Serializable
data class DeviceCapabilities(val create: DeviceCreate)

@Serializable
data class DeviceCreate(
    val reusable: Boolean = false,
    val ephemeral: Boolean = true,
    val preauthorized: Boolean = true,
    val tags: List<String> = emptyList(),
)

@Serializable
data class CreateKeyRequest(
    val capabilities: KeyCapabilities,
    val expirySeconds: Int,
    val description: String = "",
)

@Serializable
data class CreateKeyResponse(val id: String = "", val key: String = "", val description: String = "")

@Serializable
data class DeviceListResponse(val devices: List<TailnetDevice> = emptyList())

@Serializable
data class TailnetDevice(
    val id: String = "",
    val nodeId: String = "",
    val name: String = "",
    val hostname: String = "",
    val os: String = "",
    val addresses: List<String> = emptyList(),
    val online: Boolean = false,
    val lastSeen: String = "",
    val tags: List<String> = emptyList(),
    val authorized: Boolean = false,
)

class TailscaleApiException(val status: Int, override val message: String) : Exception(message)

class TailscaleApi(
    private val apiToken: String,
    engine: HttpClientEngine = platformHttpEngine(),
    private val tailnet: String = "-",
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val http = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
    }

    private suspend fun check(ok: Boolean, status: Int, body: String) {
        if (!ok) throw TailscaleApiException(status, body.take(240).ifBlank { "HTTP $status" })
    }

    suspend fun createAuthKey(
        description: String,
        expirySeconds: Int = 3600,
        ephemeral: Boolean = true,
        tags: List<String> = emptyList(),
    ): CreateKeyResponse {
        val response = http.post("https://api.tailscale.com/api/v2/tailnet/$tailnet/keys") {
            basicAuth(apiToken, "")
            contentType(ContentType.Application.Json)
            setBody(
                CreateKeyRequest(
                    capabilities = KeyCapabilities(DeviceCapabilities(DeviceCreate(false, ephemeral, true, tags))),
                    expirySeconds = expirySeconds,
                    description = description,
                ),
            )
        }
        check(response.status.isSuccess(), response.status.value, response.bodyAsText())
        return response.body()
    }

    suspend fun devices(): List<TailnetDevice> {
        val response = http.get("https://api.tailscale.com/api/v2/tailnet/$tailnet/devices") {
            basicAuth(apiToken, "")
        }
        check(response.status.isSuccess(), response.status.value, response.bodyAsText())
        val payload: DeviceListResponse = response.body()
        return payload.devices
    }

    suspend fun findDevice(hostname: String): TailnetDevice? =
        devices().firstOrNull { it.hostname.equals(hostname, true) || it.name.startsWith("$hostname.", true) }

    suspend fun deleteDevice(deviceId: String) {
        val response = http.delete("https://api.tailscale.com/api/v2/device/$deviceId") {
            basicAuth(apiToken, "")
        }
        check(response.status.isSuccess(), response.status.value, response.bodyAsText())
    }

    suspend fun revokeKey(keyId: String) {
        val response = http.delete("https://api.tailscale.com/api/v2/tailnet/$tailnet/keys/$keyId") {
            basicAuth(apiToken, "")
        }
        check(response.status.isSuccess(), response.status.value, response.bodyAsText())
    }

    fun close() = http.close()
}
