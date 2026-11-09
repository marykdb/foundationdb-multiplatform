package maryk.foundationdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame

class FDBExceptionTest {
    @Test
    fun exposesCodeProperty() {
        val ex = FDBException("test exception", 42)
        assertEquals(42, ex.getCode())
        val clone = assertIs<FDBException>(ex.retargetClone())
        assertNotSame(ex, clone)
        assertEquals(ex.message, clone.message)
        assertEquals(ex.getCode(), clone.getCode())
    }
}
