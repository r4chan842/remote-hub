package app.remotehub

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
    fun createRepositoryRequestsPublicByDefault() = runTest {
        var sent = ""
        val engine = MockEngine { req ->
            sent = (req.body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                ByteReadChannel("""{"name":"sessions","full_name":"owner/sessions","private":false,"default_branch":"main"}"""),
                HttpStatusCode.Created,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = GitHubClient("token", engine)
        val repo = client.createRepository("sessions", "RemoteHub temporary Windows sessions")
        assertEquals("owner/sessions", repo.fullName)
        assertTrue(sent.contains("\"private\":false"))
        assertTrue(!sent.contains("hetzner"))
        client.close()
    }

    @Test
    fun createRepositoryCanStayPrivate() = runTest {
        var sent = ""
        val engine = MockEngine { req ->
            sent = (req.body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                ByteReadChannel("""{"name":"sessions","full_name":"owner/sessions","private":true,"default_branch":"main"}"""),
                HttpStatusCode.Created,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = GitHubClient("token", engine)
        assertTrue(client.createRepository("sessions", "d", private = true).private)
        assertTrue(sent.contains("\"private\":true"))
        client.close()
    }

    @Test
    fun workflowStepTimestampsAreParsed() = runTest {
        val body = """
            {"jobs":[{"id":7,"name":"Windows session","status":"in_progress","conclusion":null,
              "steps":[{"name":"5. Join the private network","status":"completed","conclusion":"success","number":5,
                        "started_at":"2026-11-27T10:00:00Z","completed_at":"2026-11-27T10:00:42Z"}]}]}
        """.trimIndent()
        val client = GitHubClient("token", jsonEngine(body = body))
        val step = client.jobs("owner", "sessions", 42)[0].steps[0]
        assertEquals("2026-11-27T10:00:00Z", step.startedAt)
        assertEquals("2026-11-27T10:00:42Z", step.completedAt)
        client.close()
    }
}
