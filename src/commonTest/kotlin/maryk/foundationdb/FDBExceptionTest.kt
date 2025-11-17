package maryk.foundationdb

import kotlin.test.Test
import kotlin.test.assertEquals

class FDBExceptionTest {
    @Test
    fun exposesCodeProperty() {
        val ex = FDBException("test exception", 42)
        assertEquals(42, ex.getCode())
    }
}
