package backend.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NetworkSpeedTest {
    @Test fun `sample transfer bytes and duration produce megabits per second`() {
        assertEquals(8.388608, sampledMbps(1.0), 0.000001)
        assertEquals(16.777216, sampledMbps(0.5), 0.000001)
    }
    @Test fun `missing invalid or timed out samples are not turned into speeds`() {
        for (value in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 61.0)) {
            assertFailsWith<IllegalArgumentException> { sampledMbps(value) }
        }
    }
}
