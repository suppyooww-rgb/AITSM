package backend.services

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecurringIssueMatcherTest {
    @Test fun `minor wording differences still identify the same recurring issue`() {
        val first = RecurringIssueMatcher.normalizedTerms("PC crashes after startup")
        val second = RecurringIssueMatcher.normalizedTerms("PC crash on startup")

        assertTrue(RecurringIssueMatcher.isSimilar(first, second))
    }

    @Test fun `unrelated issues are not grouped together`() {
        val startupCrash = RecurringIssueMatcher.normalizedTerms("PC crashes after startup")
        val printer = RecurringIssueMatcher.normalizedTerms("Office printer will not print")

        assertFalse(RecurringIssueMatcher.isSimilar(startupCrash, printer))
    }

    @Test fun `single generic words do not create broad matches`() {
        val first = RecurringIssueMatcher.normalizedTerms("Network issue")
        val second = RecurringIssueMatcher.normalizedTerms("Network outage")

        assertFalse(RecurringIssueMatcher.isSimilar(first, second))
    }
}
