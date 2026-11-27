package app.remotehub

import app.remotehub.data.SecretBox
import app.remotehub.domain.StepStatus
import app.remotehub.github.GitHubClient
import app.remotehub.server.ProvisionEvent
import app.remotehub.server.ProvisionRequest
import app.remotehub.server.Provisioner
import app.remotehub.server.SessionWorkflow
import app.remotehub.server.base64
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProvisionerTest {
    private class RecordingBox : SecretBox {
        val sealed = mutableListOf<Pair<String, String>>()
        override fun seal(message: String, recipientPublicKeyBase64: String): String {
            sealed.add(message to recipientPublicKeyBase64)
            return "sealed:${sealed.size}"
        }
    }

    private fun request() = ProvisionRequest(
        serverName = "dev box",
        repositoryName = "sessions",
        tailscaleAuthKey = "tskey-auth-example",
        providerToken = "provider-token",
        durationMinutes = 120,
        warningMinutes = 15,
        desktopWidth = 1600,
        desktopHeight = 900,
        region = "fsn1",
        machineSize = "cx22",
        osImage = "ubuntu-24.04",
        autoBackup = true,
    )

    @Test
    fun base64MatchesRfcVectors() {
        assertEquals("", base64("".encodeToByteArray()))
        assertEquals("Zg==", base64("f".encodeToByteArray()))
        assertEquals("Zm8=", base64("fo".encodeToByteArray()))
        assertEquals("Zm9v", base64("foo".encodeToByteArray()))
        assertEquals("Zm9vYmE=", base64("fooba".encodeToByteArray()))
        assertEquals("Zm9vYmFy", base64("foobar".encodeToByteArray()))
    }

    @Test
    fun base64HandlesBinaryBytes() {
        val bytes = ByteArray(256) { it.toByte() }
        val encoded = base64(bytes)
        assertTrue(encoded.none { it == '\n' })
        assertEquals(344, encoded.length)
    }

    @Test
    fun workflowNeverRunsDesktopOnRunner() {
        val yaml = SessionWorkflow.render("a".repeat(40), "b".repeat(40))
        assertTrue(yaml.contains("timeout-minutes: 20"))
        assertTrue(yaml.contains("permissions:\n  contents: read"))
        assertTrue(!yaml.contains("xrdp --nodaemon"))
        assertTrue(!yaml.contains("Await session window"))
        assertTrue(yaml.contains("actions/checkout@${"a".repeat(40)}"))
        assertTrue(yaml.contains("REDACTED"))
    }

    @Test
    fun hostScriptShutsDownAfterBudget() {
        val script = SessionWorkflow.hostScript
        assertTrue(script.contains("remotehub-expire"))
        assertTrue(script.contains("shutdown -h now"))
        assertTrue(script.contains("tailscale logout"))
        assertTrue(script.contains("--authkey="))
    }

    @Test
    fun provisionSealsEverySecretWithRepoKey() = runTest {
        val seen = mutableListOf<String>()
        var dispatched = false
        val engine = MockEngine { req ->
            val path = req.url.encodedPath
            seen.add("${req.method.value} $path")
            val body = when {
                path == "/user" -> """{"login":"owner"}"""
                path == "/repos/owner/sessions" && req.method.value == "GET" ->
                    """{"name":"sessions","full_name":"owner/sessions","default_branch":"main"}"""
                path.endsWith("/actions/secrets/public-key") ->
                    """{"key_id":"kid","key":"aGVsbG8="}"""
                path.contains("/actions/secrets/") -> ""
                path.contains("/actions/variables") -> ""
                path.contains("/contents/") && req.method.value == "GET" -> """{"sha":"abc","path":"p"}"""
                path.contains("/contents/") -> """{"commit":{"sha":"deadbeef"}}"""
                path.endsWith("/dispatches") -> {
                    dispatched = true
                    ""
                }
                path.endsWith("/actions/runs") ->
                    if (dispatched) {
                        """{"workflow_runs":[{"id":99,"run_number":3,"status":"in_progress","html_url":"https://run"}]}"""
                    } else {
                        """{"workflow_runs":[]}"""
                    }
                path.endsWith("/actions/runs/99") ->
                    """{"id":99,"run_number":3,"status":"completed","conclusion":"success","html_url":"https://run"}"""
                path.endsWith("/jobs") ->
                    """{"jobs":[{"id":1,"name":"Orchestrate provider","status":"completed","conclusion":"success","steps":[{"name":"Provision host","status":"completed","conclusion":"success","number":3}]}]}"""
                else -> "{}"
            }
            respond(
                ByteReadChannel(body),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType to listOf("application/json"), "x-oauth-scopes" to listOf("repo,workflow")),
            )
        }
        val box = RecordingBox()
        val client = GitHubClient("token", engine)
        val events = mutableListOf<ProvisionEvent>()
        val provisioner = Provisioner(client, box, "owner", SessionWorkflow.render("a".repeat(40), "b".repeat(40)), provider = "hetzner")
        val outcome = provisioner.run(request()) { events.add(it) }

        assertEquals(99L, outcome.runId)
        assertEquals(3, outcome.runNumber)
        assertEquals(120, outcome.durationMinutes)
        assertEquals("rhuser", outcome.username)
        assertEquals(22, outcome.password.length)
        assertTrue(outcome.sessionId.startsWith("dev-box-"))
        assertEquals(10, box.sealed.size)
        assertTrue(box.sealed.all { it.second == "aGVsbG8=" })
        assertTrue(seen.any { it == "POST /repos/owner/sessions/actions/variables" })
        assertTrue(seen.any { it == "PUT /repos/owner/sessions/actions/secrets/PROVIDER_TOKEN" })
        assertTrue(seen.any { it.contains("/dispatches") })

        val lastSteps = events.filterIsInstance<ProvisionEvent.Steps>().last().steps
        assertEquals(StepStatus.Done, lastSteps.first { it.id == "account" }.status)
        assertEquals(StepStatus.Done, lastSteps.first { it.id == "secrets" }.status)
        assertTrue(events.any { it is ProvisionEvent.Ready })
        client.close()
    }

    @Test
    fun missingScopeFailsBeforeCreatingAnything() = runTest {
        val engine = MockEngine {
            respond(
                ByteReadChannel("""{"login":"owner"}"""),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType to listOf("application/json"), "x-oauth-scopes" to listOf("repo")),
            )
        }
        val client = GitHubClient("token", engine)
        val events = mutableListOf<ProvisionEvent>()
        val provisioner = Provisioner(client, RecordingBox(), "owner", "yaml", provider = "hetzner")
        val result = runCatching { provisioner.run(request()) { events.add(it) } }
        assertTrue(result.isFailure)
        val failure = events.filterIsInstance<ProvisionEvent.Failed>().last()
        assertEquals("account", failure.stepId)
        assertTrue(failure.message.contains("workflow"))
        client.close()
    }
}
