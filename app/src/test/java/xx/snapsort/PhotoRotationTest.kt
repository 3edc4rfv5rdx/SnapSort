package xx.snapsort

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PhotoRotationTest {
    @Test
    fun turnsGoBackAndForthInsteadOfDrifting() {
        for (start in listOf(1_700_000_000_000L, 1_700_000_002_000L, 1_700_000_001_234L)) {
            val once = nudged(start)
            assertNotEquals(start, once)
            assertEquals(2_000L, kotlin.math.abs(once - start))
            // Every turn changes the time, and it never wanders off.
            assertEquals(start, nudged(once))
        }
    }
}
