package app.remotehub

import app.remotehub.data.randomPassword
import app.remotehub.server.SessionWorkflow
import app.remotehub.server.sessionIdFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regressions for failures that only showed up on a real device or a real runner:
 * a non-ASCII session id, a password Windows refuses, and a Kotlin escape that
 * rendered into broken PowerShell.
 */
class SessionSafetyTest {
    private val workflowId = Regex("^[a-z0-9-]{3,40}$")

    @Test
    fun persianServerNameStillProducesAWorkflowSafeId() {
        listOf("سرور من", "ویندوز ۱۱", "میز کار", "Пк", "我的电脑", "!!!", "").forEach { name ->
            val id = sessionIdFor(name)
            assertTrue(id.matches(workflowId), "id '$id' from '$name' is not workflow safe")
            assertTrue(("rh-$id").length <= 63, "hostname for '$name' is too long")
        }
    }

    @Test
    fun asciiServerNameKeepsItsReadablePrefix() {
        val id = sessionIdFor("Windows Desktop")
        assertTrue(id.startsWith("windows-desktop-"))
        assertTrue(id.matches(workflowId))
    }

    @Test
    fun longServerNameStaysInsideTheWorkflowLimit() {
        val id = sessionIdFor("x".repeat(200))
        assertTrue(id.matches(workflowId))
        assertTrue(id.length <= 40)
    }

    @Test
    fun generatedPasswordAlwaysMeetsWindowsComplexity() {
        // A local Windows account needs three of the four character classes; drawing
        // purely at random missed a class often enough to fail New-LocalUser.
        repeat(200) {
            val password = randomPassword(24)
            assertEquals(24, password.length)
            assertTrue(password.any { it in 'A'..'Z' }, "no uppercase: $password")
            assertTrue(password.any { it in 'a'..'z' }, "no lowercase")
            assertTrue(password.any { it in '0'..'9' }, "no digit")
            assertTrue(password.any { it in "!@#%*-_=+" }, "no symbol")
            assertTrue(password.none { it == '"' || it == '\'' || it == '`' || it == '$' })
        }
    }

    @Test
    fun shortPasswordRequestIsRaisedToASafeLength() {
        assertTrue(randomPassword(4).length >= 8)
    }

    @Test
    fun passwordsAreNotRepeated() {
        val drawn = List(50) { randomPassword(24) }.toSet()
        assertEquals(50, drawn.size)
    }

    @Test
    fun workflowTemplateHasNoUnrenderedKotlinEscapes() {
        val yaml = SessionWorkflow.render("a".repeat(40), "b".repeat(40))
        // ${'}'} used to render a stray brace into AddMinutes(), which made the very
        // first PowerShell step fail to parse.
        assertTrue(yaml.contains("AddMinutes(\$minutes)"))
        assertTrue(!yaml.contains("}minutes"))
        assertTrue(!yaml.contains("\${'\$'}"))
        val leftovers = Regex("""\$\{[^{]*}""").findAll(yaml)
            .map { it.value }
            .filter { !it.startsWith("\${{") }
            .toList()
        assertEquals(emptyList(), leftovers)
    }

    @Test
    fun workflowLeavesRoomForCleanupBeforeTheJobCeiling() {
        val yaml = SessionWorkflow.render("a".repeat(40), "b".repeat(40))
        assertTrue(yaml.contains("AddSeconds(-90)"))
        assertTrue(yaml.contains("if: always()"))
    }
}
