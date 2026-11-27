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
        serverName = "windows desktop",
        repositoryName = "remote-hub-sessions",
        tailscaleAuthKey = "tskey-auth-example",
        durationMinutes = 240,
        warningMinutes = 15,
        publicRepository = true,
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
    fun workflowRunsWindowsOnTheHostedRunner() {
        val yaml = SessionWorkflow.render("a".repeat(40), "b".repeat(40))
        assertTrue(yaml.contains("runs-on: windows-2025"))
        assertTrue(yaml.contains("timeout-minutes: 360"))
        assertTrue(yaml.contains("permissions:\n  contents: read"))
        assertTrue(yaml.contains("actions/upload-artifact@${"b".repeat(40)}"))
        // The provider path is gone for good.
        assertTrue(!yaml.contains("hetzner", ignoreCase = true))
        assertTrue(!yaml.contains("digitalocean", ignoreCase = true))
        assertTrue(!yaml.contains("PROVIDER_TOKEN"))
        assertTrue(!yaml.contains("xrdp"))
        assertTrue(!yaml.contains("ubuntu-latest"))
    }

    @Test
    fun workflowIsHarderThanTheCirculatingSnippet() {
        val yaml = SessionWorkflow.render("a".repeat(40), "b".repeat(40))
        // No hard-coded password, and the built-in runner account is never touched.
        assertTrue(!yaml.contains("P@ssw0rd"))
        assertTrue(!yaml.contains("runneradmin"))
        assertTrue(!yaml.contains("NvDAdmin"))
        // NLA stays on and the transport is TLS.
        assertTrue(yaml.contains("-Name UserAuthentication -Value 1"))
        assertTrue(yaml.contains("-Name SecurityLayer -Value 2"))
        // RDP is reachable only from the tailnet range.
        assertTrue(yaml.contains("-RemoteAddress 100.64.0.0/10"))
        assertTrue(yaml.contains("Disable-NetFirewallRule"))
        // Secrets are masked before use and never echoed.
        assertTrue(yaml.contains("::add-mask::"))
        assertTrue(!yaml.contains("Write-Host \"Password"))
        // The node is ephemeral and always logs out.
        assertTrue(yaml.contains("--ephemeral"))
        assertTrue(yaml.contains("logout"))
        assertTrue(yaml.contains("if: always()"))
        // The installer is pinned and checksum-verified.
        assertTrue(yaml.contains(SessionWorkflow.TAILSCALE_MSI))
        assertTrue(yaml.contains(SessionWorkflow.TAILSCALE_MSI_SHA256))
        assertTrue(yaml.contains("checksum mismatch"))
        // The lifetime is validated and bounded by the six hour job ceiling.
        assertTrue(yaml.contains("^[a-z0-9-]{3,40}$"))
        assertTrue(yaml.contains("minutes out of range"))
        assertEquals(360, SessionWorkflow.MAX_MINUTES)
    }

    @Test
    fun provisionSealsThreeSecretsAndNeverSendsThePasswordInTheClear() = runTest {
        val seen = mutableListOf<String>()
        var dispatched = false
        var dispatchBody = ""
        val engine = MockEngine { req ->
            val path = req.url.encodedPath
            seen.add("${req.method.value} $path")
            if (path.endsWith("/dispatches")) {
                dispatchBody = (req.body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes().decodeToString()
            }
            val body = when {
                path == "/user" -> """{"login":"owner"}"""
                path == "/repos/owner/remote-hub-sessions" && req.method.value == "GET" ->
                    """{"name":"remote-hub-sessions","full_name":"owner/remote-hub-sessions","default_branch":"main"}"""
                path.endsWith("/actions/secrets/public-key") -> """{"key_id":"kid","key":"aGVsbG8="}"""
                path.contains("/actions/secrets/") -> ""
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
                    """{"id":99,"run_number":3,"status":"in_progress","conclusion":null,"html_url":"https://run"}"""
                path.endsWith("/jobs") ->
                    """{"jobs":[{"id":1,"name":"Windows session","status":"in_progress","conclusion":null,"steps":[
                        {"name":"1. Validate request","status":"completed","conclusion":"success","number":1},
                        {"name":"2. Enable Remote Desktop","status":"completed","conclusion":"success","number":2},
                        {"name":"3. Restrict RDP to the private network","status":"completed","conclusion":"success","number":3},
                        {"name":"4. Create the session account","status":"completed","conclusion":"success","number":4},
                        {"name":"5. Join the private network","status":"completed","conclusion":"success","number":5},
                        {"name":"6. Publish the endpoint","status":"completed","conclusion":"success","number":6},
                        {"name":"7. Keep the session alive","status":"in_progress","conclusion":null,"number":8}]}]}"""
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
        val provisioner = Provisioner(client, box, "owner", SessionWorkflow.render("a".repeat(40), "b".repeat(40)))
        val outcome = provisioner.run(request()) { events.add(it) }

        assertEquals(99L, outcome.runId)
        assertEquals(3, outcome.runNumber)
        assertEquals(240, outcome.durationMinutes)
        assertEquals("rhuser", outcome.username)
        assertEquals(24, outcome.password.length)
        assertTrue(outcome.sessionId.startsWith("windows-desktop-"))
        assertTrue(outcome.sessionId.matches(Regex("^[a-z0-9-]{3,40}$")))

        // Exactly three sealed secrets, all sealed with the repository key.
        assertEquals(3, box.sealed.size)
        assertTrue(box.sealed.all { it.second == "aGVsbG8=" })
        assertTrue(seen.any { it == "PUT /repos/owner/remote-hub-sessions/actions/secrets/TAILSCALE_AUTHKEY" })
        assertTrue(seen.any { it == "PUT /repos/owner/remote-hub-sessions/actions/secrets/RDP_USERNAME" })
        assertTrue(seen.any { it == "PUT /repos/owner/remote-hub-sessions/actions/secrets/RDP_PASSWORD" })
        assertTrue(seen.none { it.contains("PROVIDER_TOKEN") })
        assertTrue(seen.none { it.contains("/actions/variables") })
        // The password never travels in the dispatch inputs.
        assertTrue(!dispatchBody.contains(outcome.password))
        assertTrue(dispatchBody.contains("session_id"))
        assertTrue(dispatchBody.contains("\"minutes\":\"240\""))
        // The workflow file itself is committed.
        assertTrue(seen.any { it == "PUT /repos/owner/remote-hub-sessions/contents/.github/workflows/windows-rdp.yml" })

        val lastSteps = events.filterIsInstance<ProvisionEvent.Steps>().last().steps
        assertEquals(7, lastSteps.size)
        assertEquals(StepStatus.Done, lastSteps.first { it.id == "account" }.status)
        assertEquals(StepStatus.Done, lastSteps.first { it.id == "machine" }.status)
        assertEquals(StepStatus.Done, lastSteps.first { it.id == "network" }.status)
        assertEquals(StepStatus.Done, lastSteps.first { it.id == "ready" }.status)
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
        val provisioner = Provisioner(client, RecordingBox(), "owner", "yaml")
        val result = runCatching { provisioner.run(request()) { events.add(it) } }
        assertTrue(result.isFailure)
        val failure = events.filterIsInstance<ProvisionEvent.Failed>().last()
        assertEquals("account", failure.stepId)
        assertTrue(failure.message.contains("workflow"))
        client.close()
    }

    @Test
    fun aFailedJobStepIsReportedOnTheRightStage() = runTest {
        var dispatched = false
        val engine = MockEngine { req ->
            val path = req.url.encodedPath
            val body = when {
                path == "/user" -> """{"login":"owner"}"""
                path == "/repos/owner/remote-hub-sessions" -> """{"name":"remote-hub-sessions","full_name":"owner/remote-hub-sessions","default_branch":"main"}"""
                path.endsWith("/public-key") -> """{"key_id":"kid","key":"aGVsbG8="}"""
                path.contains("/actions/secrets/") -> ""
                path.contains("/contents/") && req.method.value == "GET" -> """{"sha":"abc","path":"p"}"""
                path.contains("/contents/") -> """{"commit":{"sha":"deadbeef"}}"""
                path.endsWith("/dispatches") -> { dispatched = true; "" }
                path.endsWith("/actions/runs") ->
                    if (dispatched) """{"workflow_runs":[{"id":7,"run_number":1,"status":"in_progress","html_url":"u"}]}"""
                    else """{"workflow_runs":[]}"""
                path.endsWith("/actions/runs/7") -> """{"id":7,"run_number":1,"status":"completed","conclusion":"failure","html_url":"u"}"""
                path.endsWith("/jobs") ->
                    """{"jobs":[{"id":1,"name":"Windows session","status":"completed","conclusion":"failure","steps":[
                        {"name":"5. Join the private network","status":"completed","conclusion":"failure","number":5}]}]}"""
                else -> "{}"
            }
            respond(
                ByteReadChannel(body),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType to listOf("application/json"), "x-oauth-scopes" to listOf("repo,workflow")),
            )
        }
        val client = GitHubClient("token", engine)
        val events = mutableListOf<ProvisionEvent>()
        val provisioner = Provisioner(client, RecordingBox(), "owner", "yaml")
        val result = runCatching { provisioner.run(request()) { events.add(it) } }
        assertTrue(result.isFailure)
        val lastSteps = events.filterIsInstance<ProvisionEvent.Steps>().last().steps
        assertEquals(StepStatus.Failed, lastSteps.first { it.id == "network" }.status)
        client.close()
    }

    @Test
    fun dispatchRetriesWhileGitHubRegistersTheNewWorkflow() = runTest {
        var dispatched = false
        var dispatchAttempts = 0
        val engine = MockEngine { req ->
            val path = req.url.encodedPath
            if (path.endsWith("/dispatches")) {
                dispatchAttempts++
                if (dispatchAttempts == 1) {
                    respond(
                        ByteReadChannel("""{"message":"Workflow does not have workflow_dispatch trigger"}"""),
                        HttpStatusCode.UnprocessableEntity,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                    return@MockEngine
                }
                dispatched = true
                respond(ByteReadChannel(""), HttpStatusCode.NoContent, headersOf(HttpHeaders.ContentType, "application/json"))
                return@MockEngine
            }
            val body = when {
                path == "/user" -> """{"login":"owner"}"""
                path == "/repos/owner/remote-hub-sessions" -> """{"name":"remote-hub-sessions","full_name":"owner/remote-hub-sessions","default_branch":"main"}"""
                path.endsWith("/public-key") -> """{"key_id":"kid","key":"aGVsbG8="}"""
                path.contains("/actions/secrets/") -> ""
                path.contains("/contents/") && req.method.value == "GET" -> """{"sha":"abc","path":"p"}"""
                path.contains("/contents/") -> """{"commit":{"sha":"deadbeef"}}"""
                path.endsWith("/actions/runs") ->
                    if (dispatched) """{"workflow_runs":[{"id":5,"run_number":1,"status":"in_progress","html_url":"u"}]}"""
                    else """{"workflow_runs":[]}"""
                path.endsWith("/actions/runs/5") -> """{"id":5,"run_number":1,"status":"in_progress","conclusion":null,"html_url":"u"}"""
                path.endsWith("/jobs") ->
                    """{"jobs":[{"id":1,"name":"Windows session","status":"in_progress","conclusion":null,"steps":[
                        {"name":"5. Join the private network","status":"completed","conclusion":"success","number":5},
                        {"name":"6. Publish the endpoint","status":"completed","conclusion":"success","number":6}]}]}"""
                else -> "{}"
            }
            respond(
                ByteReadChannel(body),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType to listOf("application/json"), "x-oauth-scopes" to listOf("repo,workflow")),
            )
        }
        val client = GitHubClient("token", engine)
        val events = mutableListOf<ProvisionEvent>()
        val outcome = Provisioner(client, RecordingBox(), "owner", "yaml").run(request()) { events.add(it) }
        assertEquals(2, dispatchAttempts)
        assertEquals(5L, outcome.runId)
        assertTrue(events.filterIsInstance<ProvisionEvent.Log>().any { it.line.contains("register the workflow") })
        client.close()
    }

    @Test
    fun durationIsClampedToTheJobCeiling() = runTest {
        assertEquals(360, SessionWorkflow.MAX_MINUTES)
        assertEquals(360, SessionWorkflow.DEFAULT_MINUTES)
        assertTrue(SessionWorkflow.MIN_MINUTES >= 15)
        val tooLong = 900.coerceIn(SessionWorkflow.MIN_MINUTES, SessionWorkflow.MAX_MINUTES)
        val tooShort = 1.coerceIn(SessionWorkflow.MIN_MINUTES, SessionWorkflow.MAX_MINUTES)
        assertEquals(360, tooLong)
        assertEquals(15, tooShort)
    }
}
