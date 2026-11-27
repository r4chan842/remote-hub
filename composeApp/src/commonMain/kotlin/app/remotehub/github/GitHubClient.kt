package app.remotehub.github

import app.remotehub.data.platformHttpEngine
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class GitHubException(val status: Int, override val message: String) : Exception(message)

data class AccountValidation(
    val user: GhUser,
    val scopes: List<String>,
    val missing: List<String>,
)

class GitHubClient(
    private val token: String,
    engine: HttpClientEngine = platformHttpEngine(),
    private val host: String = "api.github.com",
) {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    val http = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
    }

    private suspend inline fun <reified T> decode(response: HttpResponse): T {
        ensureSuccess(response)
        return response.body()
    }

    suspend fun ensureSuccess(response: HttpResponse) {
        if (!response.status.isSuccess()) {
            val text = runCatching { response.bodyAsText() }.getOrDefault("")
            throw GitHubException(response.status.value, extractMessage(text, response.status.value))
        }
    }

    private fun extractMessage(body: String, status: Int): String {
        val marker = "\"message\""
        val idx = body.indexOf(marker)
        if (idx < 0) return "HTTP $status"
        val colon = body.indexOf(':', idx + marker.length)
        val firstQuote = body.indexOf('"', colon + 1)
        val secondQuote = body.indexOf('"', firstQuote + 1)
        return if (firstQuote > 0 && secondQuote > firstQuote) {
            body.substring(firstQuote + 1, secondQuote)
        } else {
            "HTTP $status"
        }
    }

    suspend fun validate(required: List<String> = REQUIRED_SCOPES): AccountValidation {
        val response = http.get {
            url { protocol = URLProtocol.HTTPS; host = this@GitHubClient.host; encodedPath = "/user" }
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header("X-GitHub-Api-Version", API_VERSION)
            header(HttpHeaders.UserAgent, USER_AGENT)
        }
        ensureSuccess(response)
        val user: GhUser = response.body()
        val granted = response.headers["x-oauth-scopes"]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        val missing = if (granted.isEmpty()) emptyList() else required.filter { req ->
            granted.none { it == req || (req == "public_repo" && it == "repo") }
        }
        return AccountValidation(user, granted, missing)
    }

    private suspend fun request(
        method: String,
        path: String,
        body: Any? = null,
        query: Map<String, String> = emptyMap(),
    ): HttpResponse = http.request {
        this.method = io.ktor.http.HttpMethod.parse(method)
        url {
            protocol = URLProtocol.HTTPS
            host = this@GitHubClient.host
            encodedPath = path
        }
        header(HttpHeaders.Authorization, "Bearer $token")
        header(HttpHeaders.Accept, "application/vnd.github+json")
        header("X-GitHub-Api-Version", API_VERSION)
        header(HttpHeaders.UserAgent, USER_AGENT)
        query.forEach { (k, v) -> parameter(k, v) }
        if (body != null) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    suspend fun repository(owner: String, name: String): GhRepo? {
        val response = request("GET", "/repos/$owner/$name")
        if (response.status.value == 404) return null
        return decode(response)
    }

    suspend fun createRepository(name: String, description: String, private: Boolean = false): GhRepo =
        decode(request("POST", "/user/repos", CreateRepoRequest(name, private, true, description)))

    suspend fun publicKey(owner: String, repo: String): RepoPublicKey =
        decode(request("GET", "/repos/$owner/$repo/actions/secrets/public-key"))

    suspend fun putSecret(owner: String, repo: String, name: String, encryptedValue: String, keyId: String) {
        ensureSuccess(
            request("PUT", "/repos/$owner/$repo/actions/secrets/$name", PutSecretRequest(encryptedValue, keyId)),
        )
    }

    /**
     * Re-runs an existing run instead of dispatching a new one. GitHub keeps the run
     * id, the run number and the original inputs, so the session id, the tailnet
     * hostname and the repository secrets all stay the same; only a new attempt is
     * added. That is what makes "restart" feel like restarting the same server.
     *
     * POST /repos/{owner}/{repo}/actions/runs/{run_id}/rerun -> 201
     */
    suspend fun rerun(owner: String, repo: String, runId: Long) {
        ensureSuccess(request("POST", "/repos/$owner/$repo/actions/runs/$runId/rerun"))
    }

    /** Re-runs only the jobs that did not succeed, keeping the successful ones. */
    suspend fun rerunFailed(owner: String, repo: String, runId: Long) {
        ensureSuccess(request("POST", "/repos/$owner/$repo/actions/runs/$runId/rerun-failed-jobs"))
    }

    suspend fun contentSha(owner: String, repo: String, path: String): String? {
        val response = request("GET", "/repos/$owner/$repo/contents/$path")
        if (response.status.value == 404) return null
        val entry: ContentEntry = decode(response)
        return entry.sha
    }

    suspend fun putContent(
        owner: String,
        repo: String,
        path: String,
        base64Content: String,
        message: String,
        branch: String? = null,
    ): PutContentResponse {
        val sha = contentSha(owner, repo, path)
        return decode(
            request("PUT", "/repos/$owner/$repo/contents/$path", PutContentRequest(message, base64Content, sha, branch)),
        )
    }

    suspend fun putVariable(owner: String, repo: String, name: String, value: String) {
        val create = request(
            "POST",
            "/repos/$owner/$repo/actions/variables",
            mapOf("name" to name, "value" to value),
        )
        if (create.status.value == 409 || create.status.value == 422) {
            ensureSuccess(
                request("PATCH", "/repos/$owner/$repo/actions/variables/$name", mapOf("name" to name, "value" to value)),
            )
            return
        }
        ensureSuccess(create)
    }

    suspend fun workflows(owner: String, repo: String): List<Workflow> {
        val response: WorkflowsResponse = decode(request("GET", "/repos/$owner/$repo/actions/workflows"))
        return response.workflows
    }

    suspend fun dispatch(owner: String, repo: String, workflowFile: String, ref: String, inputs: Map<String, String>) {
        ensureSuccess(
            request(
                "POST",
                "/repos/$owner/$repo/actions/workflows/$workflowFile/dispatches",
                DispatchRequest(ref, inputs),
            ),
        )
    }

    suspend fun latestDispatchRun(owner: String, repo: String): WorkflowRun? {
        val response: RunsResponse = decode(
            request("GET", "/repos/$owner/$repo/actions/runs", query = mapOf("event" to "workflow_dispatch", "per_page" to "5")),
        )
        return response.workflowRuns.maxByOrNull { it.id }
    }

    suspend fun run(owner: String, repo: String, runId: Long): WorkflowRun =
        decode(request("GET", "/repos/$owner/$repo/actions/runs/$runId"))

    suspend fun jobs(owner: String, repo: String, runId: Long): List<WorkflowJob> {
        val response: JobsResponse = decode(request("GET", "/repos/$owner/$repo/actions/runs/$runId/jobs"))
        return response.jobs
    }

    suspend fun cancel(owner: String, repo: String, runId: Long) {
        ensureSuccess(request("POST", "/repos/$owner/$repo/actions/runs/$runId/cancel"))
    }

    suspend fun rerun(owner: String, repo: String, runId: Long) {
        ensureSuccess(request("POST", "/repos/$owner/$repo/actions/runs/$runId/rerun"))
    }

    suspend fun deleteRepository(owner: String, repo: String) {
        ensureSuccess(request("DELETE", "/repos/$owner/$repo"))
    }

    fun close() = http.close()

    companion object {
        const val API_VERSION = "2022-11-28"
        const val USER_AGENT = "RemoteHub-Android"
        val REQUIRED_SCOPES = listOf("repo", "workflow")
    }
}
