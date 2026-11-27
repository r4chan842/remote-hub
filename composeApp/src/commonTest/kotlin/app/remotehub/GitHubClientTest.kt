package app.remotehub

import app.remotehub.github.DeviceFlowClient
import app.remotehub.github.DevicePoll
import app.remotehub.github.GitHubClient
import app.remotehub.github.GitHubException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubClientTest {
    private fun jsonEngine(status: HttpStatusCode = HttpStatusCode.OK, body: String, extra: Map<String, String> = emptyMap()) =
        MockEngine { request ->
            respond(
                content = ByteReadChannel(body),
                status = status,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    *extra.map { it.key to listOf(it.value) }.toTypedArray(),
                ),
            )
        }

    @Test
    fun validateReadsScopesFromHeaders() = runTest {
        val client = GitHubClient(
            "token",
            jsonEngine(body = """{"login":"octocat","name":"Octo Cat","id":1}""", extra = mapOf("x-oauth-scopes" to "repo, workflow")),
        )
        val validation = client.validate()
        assertEquals("octocat", validation.user.login)
        assertEquals(listOf("repo", "workflow"), validation.scopes)
        assertTrue(validation.missing.isEmpty())
        client.close()
    }

    @Test
    fun validateReportsMissingWorkflowScope() = runTest {
        val client = GitHubClient("token", jsonEngine(body = """{"login":"octocat"}""", extra = mapOf("x-oauth-scopes" to "repo")))
        val validation = client.validate()
        assertEquals(listOf("workflow"), validation.missing)
        client.close()
    }

    @Test
    fun fineGrainedTokenWithoutScopeHeaderIsAccepted() = runTest {
        val client = GitHubClient("token", jsonEngine(body = """{"login":"octocat"}"""))
        assertTrue(client.validate().missing.isEmpty())
        client.close()
    }

    @Test
    fun revokedTokenSurfacesGitHubMessage() = runTest {
        val client = GitHubClient("token", jsonEngine(HttpStatusCode.Unauthorized, """{"message":"Bad credentials"}"""))
        val error = assertFailsWith<GitHubException> { client.validate() }
        assertEquals(401, error.status)
        assertEquals("Bad credentials", error.message)
        client.close()
    }

    @Test
    fun missingRepositoryReturnsNull() = runTest {
        val client = GitHubClient("token", jsonEngine(HttpStatusCode.NotFound, """{"message":"Not Found"}"""))
        assertNull(client.repository("owner", "absent"))
        client.close()
    }

    @Test
    fun existingRepositoryIsParsed() = runTest {
        val client = GitHubClient(
            "token",
            jsonEngine(body = """{"name":"sessions","full_name":"owner/sessions","private":false,"default_branch":"main"}"""),
        )
        val repo = client.repository("owner", "sessions")
        assertNotNull(repo)
        assertEquals("owner/sessions", repo.fullName)
        assertEquals("main", repo.defaultBranch)
        client.close()
    }

    @Test
    fun publicKeyIsParsed() = runTest {
        val client = GitHubClient("token", jsonEngine(body = """{"key_id":"012345678912345678","key":"hSDwCYkwp1R0i33ctD73Wg2/Og0mOBr066SpjqqbTmo="}"""))
        val key = client.publicKey("owner", "sessions")
        assertEquals("012345678912345678", key.keyId)
        assertTrue(key.key.isNotBlank())
        client.close()
    }

    @Test
    fun latestDispatchRunPicksHighestId() = runTest {
        val body = """
            {"total_count":2,"workflow_runs":[
              {"id":10,"run_number":1,"status":"completed","conclusion":"success","html_url":"u1"},
              {"id":42,"run_number":2,"status":"in_progress","conclusion":null,"html_url":"u2"}
            ]}
        """.trimIndent()
        val client = GitHubClient("token", jsonEngine(body = body))
        val run = client.latestDispatchRun("owner", "sessions")
        assertNotNull(run)
        assertEquals(42L, run.id)
        assertEquals(2, run.runNumber)
        client.close()
    }

    @Test
    fun jobStepsAreParsed() = runTest {
        val body = """
            {"jobs":[{"id":7,"name":"Orchestrate provider","status":"in_progress","conclusion":null,
              "steps":[{"name":"Validate request","status":"completed","conclusion":"success","number":2},
                       {"name":"Provision host","status":"in_progress","conclusion":null,"number":3}]}]}
        """.trimIndent()
        val client = GitHubClient("token", jsonEngine(body = body))
        val jobs = client.jobs("owner", "sessions", 42)
        assertEquals(1, jobs.size)
        assertEquals(2, jobs[0].steps.size)
        assertEquals("Provision host", jobs[0].steps[1].name)
        client.close()
    }

    @Test
    fun dispatchFailurePropagatesStatus() = runTest {
        val client = GitHubClient("token", jsonEngine(HttpStatusCode.UnprocessableEntity, """{"message":"Workflow does not have workflow_dispatch trigger"}"""))
        val error = assertFailsWith<GitHubException> {
            client.dispatch("owner", "sessions", "session.yml", "main", emptyMap())
        }
        assertEquals(422, error.status)
        client.close()
    }

    @Test
    fun deviceCodeRequestIsParsed() = runTest {
        val body = """
            {"device_code":"abc","user_code":"WDJB-MJHT","verification_uri":"https://github.com/login/device",
             "expires_in":900,"interval":5}
        """.trimIndent()
        val flow = DeviceFlowClient("client", jsonEngine(body = body))
        val code = flow.requestCode()
        assertEquals("WDJB-MJHT", code.userCode)
        assertEquals(900, code.expiresIn)
        flow.close()
    }

    @Test
    fun devicePollPendingThenGranted() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            val body = if (calls == 1) {
                """{"error":"authorization_pending"}"""
            } else {
                """{"access_token":"gho_real","token_type":"bearer","scope":"repo,workflow"}"""
            }
            respond(ByteReadChannel(body), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val flow = DeviceFlowClient("client", engine)
        assertEquals(DevicePoll.Pending, flow.pollOnce("abc"))
        val granted = flow.pollOnce("abc")
        assertTrue(granted is DevicePoll.Granted)
        assertEquals("gho_real", granted.token)
        assertEquals(listOf("repo", "workflow"), granted.scopes)
        flow.close()
    }

    @Test
    fun devicePollSlowDownRaisesInterval() = runTest {
        val flow = DeviceFlowClient("client", jsonEngine(body = """{"error":"slow_down"}"""))
        val result = flow.pollOnce("abc")
        assertTrue(result is DevicePoll.SlowDown)
        assertEquals(10, result.newInterval)
        flow.close()
    }

    @Test
    fun devicePollDenialIsTerminal() = runTest {
        val flow = DeviceFlowClient("client", jsonEngine(body = """{"error":"access_denied"}"""))
        val result = flow.pollOnce("abc")
        assertTrue(result is DevicePoll.Failed)
        assertEquals("access_denied", result.reason)
        flow.close()
    }

    @Test
    fun devicePollExpiryIsTerminal() = runTest {
        val flow = DeviceFlowClient("client", jsonEngine(body = """{"error":"expired_token"}"""))
        val result = flow.pollOnce("abc")
        assertTrue(result is DevicePoll.Failed)
        assertEquals("expired_token", result.reason)
        flow.close()
    }

    @Test
    fun deviceCodeIsSentFormEncoded() = runTest {
        var sentType: String? = null
        var sentBody = ""
        val engine = MockEngine { req ->
            sentType = req.body.contentType?.toString()
            sentBody = (req.body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                ByteReadChannel("""{"device_code":"d","user_code":"AB-CD","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}"""),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val flow = DeviceFlowClient("Ov23abc", engine)
        flow.requestCode()
        assertTrue(sentType!!.startsWith("application/x-www-form-urlencoded"))
        assertTrue(sentBody.contains("client_id=Ov23abc"))
        flow.close()
    }

    @Test
    fun deviceFlowDisabledIsReported() = runTest {
        val flow = DeviceFlowClient("client", jsonEngine(body = """{"error":"device_flow_disabled"}"""))
        val error = assertFailsWith<GitHubException> { flow.requestCode() }
        assertEquals("device_flow_disabled", error.message)
        flow.close()
    }
}
