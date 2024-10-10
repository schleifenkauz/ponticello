package xenakis.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import xenakis.impl.asTime
import xenakis.impl.timeCode

class TimeCodeTest {
    @Test
    fun testTimeCodes() {
        assertEquals("1", timeCode(1.0.asTime, 0))
        assertEquals("10", timeCode(10.0.asTime, 0))
        assertEquals("12,00", timeCode(12.0.asTime, 2))
        assertEquals("5:10", timeCode(310.0.asTime, 0))
        assertEquals("5:05,0", timeCode(305.0.asTime, 1))
        assertEquals("1,250", timeCode(1.25.asTime, 3))
        assertEquals("1,002", timeCode(1.002.asTime, 3))
        assertEquals("1:01,10", timeCode(61.1.asTime, 2))

    }
}