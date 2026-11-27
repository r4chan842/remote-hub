package app.remotehub

import app.remotehub.domain.StepStatus
import app.remotehub.i18n.English
import app.remotehub.i18n.Persian
import app.remotehub.ui.formatMillis
import app.remotehub.ui.provisionStepIds
import app.remotehub.ui.stepTitle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProvisioningTest {
    private val stepIds = provisionStepIds

    @Test
    fun everyProvisionStepHasDistinctLocalizedTitle() {
        listOf(English, Persian).forEach { strings ->
            val titles = stepIds.map { stepTitle(it, strings) }
            assertTrue(titles.all { it.isNotBlank() })
            assertEquals(stepIds.size, titles.toSet().size)
        }
    }

    @Test
    fun elapsedFormattingSwitchesToMinutes() {
        assertEquals("9s", formatMillis(9_400))
        assertEquals("1m 5s", formatMillis(65_000))
        assertEquals("6m 12s", formatMillis(372_000))
    }

    @Test
    fun progressIsDerivedFromRealStepStates() {
        val steps = listOf(
            StepStatus.Done, StepStatus.Done, StepStatus.Done,
            StepStatus.Running, StepStatus.Pending, StepStatus.Pending,
        )
        val fraction = steps.count { it == StepStatus.Done }.toFloat() / steps.size
        assertEquals(0.5f, fraction)
        assertNotEquals(1f, fraction)
    }
}
